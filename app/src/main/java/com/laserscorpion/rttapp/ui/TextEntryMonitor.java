/* © 2016 Joel Cretan
 *
 * This is part of RTTAPP, an Android RFC 4103 real-time text app
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.laserscorpion.rttapp.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import com.laserscorpion.rttapp.BuildConfig;
import com.laserscorpion.rttapp.sip.SipClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * <p>The TextEntryMonitor, one of which is created by the RTTCallActivity at a time,
 * listens for input on the user's text field (EditText), and keeps track of which
 * characters have changed. It sends those characters in the real-time call via the SipClient.</p>
 *
 * <p>There are two modes, real time and en bloc, specified in the constructor.</p>
 *
 * <p>Real-time mode:<br>
 * If text is added or deleted at the end, TextEntryMonitor tells the SipClient to send
 * the new characters (possibly '\b') in the real-time call. If edits are made earlier in the text,
 * it undoes them, since the user is not allowed to add or remove text anywhere besides the
 * end of the field. Keeping track of these edits is pretty annoying, and you need to understand how
 * the Android.text.TextWatcher interface works before touching any of this.</p>
 *
 * <p>En bloc mode:<br>
 * Text is not sent character by character. It is only sent as complete messages, when checkAndSend()
 * is called.</p>
 */
public class TextEntryMonitor implements TextWatcher {
    private static final String TAG = "TextWatcher";
    private EditText fieldToMonitor;
    private CharSequence currentText;
    private SipClient texter;
    private boolean makingManualEdit = false;
    private boolean needManualEdit = false; // flag that indicates that we need to undo the text change the user made - not allowed to edit earlier text
    private boolean screenRotated = false;
    private boolean useRealTimeText; // real-time char-by-char mode vs. en bloc mode

    /**
     * The only constructor.
     * @param watchThis the text field to monitor for changes
     * @param realTime whether the text should be sent in real time, character by character, or only when checkAndSend() is called
     * @param sipClient the global hub for all things SIP, and who is responsible for sending the real-time text chars
     * @param startText initial text to populate the field with, to be ignored (likely put there because the activity is destroyed and recreated)
     * @param screenRotated whether this new TextEntryMonitor is being created due to screen rotation, i.e. the activity is destroyed and recreated
     */
    public TextEntryMonitor(EditText watchThis, boolean realTime, SipClient sipClient, CharSequence startText, boolean screenRotated) {
        synchronized (watchThis) {
            fieldToMonitor = watchThis;
            useRealTimeText = realTime;
            if (useRealTimeText)
                fieldToMonitor.addTextChangedListener(this);
            currentText = startText;
            texter = sipClient;
            this.screenRotated = screenRotated;
        }
    }

    /**
     * onTextChanged is the bulk of the logic to determine which characters to send to the other party when the user enters text.
     * If useRealTimeText == false, these listener methods are never called. In turn, most methods total are never
     * called. En bloc methods are at the end of the class. This method enforces the rule that the
     * user may only enter and delete text at the end of the field, not earlier (as in SipCon1).
     *
     * The important thing to know here is that in beforeTextChanged(), we set currentText to the
     * text in the field before the change occurs. Then, in onTextChanged(), s is the *new* text and
     * currentText is the *old* text. If we edit the text programmatically (e.g. to undo a prohibited
     * edit), these callbacks fire, so they need to know if they are being invoked due to a change
     * made by this code, not by the user. Thus we have the flag makingManualEdit, which is set in
     * afterTextChanged(), if it sees the flag needManualEdit.
     */
    @Override
    public synchronized void onTextChanged(CharSequence s, int start, int before, int count) {
        if (makingManualEdit) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ignoring manual edit");
            return;
        }
        if (screenRotated) {
            screenRotated = false;
            return; // ignore text addition due to destruction and recreation of activity
        }
        if (BuildConfig.DEBUG)
            //Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

        if (editOverlappedEnd(start, before, count)) {
            if (charsOnlyAppended(s, start, before, count)) {
                sendAppendedChars(s, start, before, count);
            } else if (charsOnlyDeleted(s, start, before, count)) {
                sendDeletionsFromEnd(s, start, before, count);
            } else if (count == before) {
                if (textActuallyChanged(s, start, before, count)) {
                    // when entering a space, the previous word is reported as changing for some reason, but it hasn't actually changed, so we must check
                    sendCompoundReplacementText(s, start, before, count);
                }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Some kind of complex edit occurred 1");
                sendCompoundReplacementText(s, start, before, count);
            }
        } else {
            if (editInLastWord(start, before, count)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "edit in last word");
                sendLastWord(s, start, before, count);
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Some kind of complex edit occurred 2");
                needManualEdit = true;
                // we can't allow edits that occur earlier in the text, you can only edit the end
            }
        }
    }

    @Override
    public synchronized void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (makingManualEdit)
            return;
        currentText = s.subSequence(0, s.length()); // deep copy the text before it is changed so we can compare before and after the edit
    }

    @Override
    public synchronized void afterTextChanged(Editable s) {
        if (needManualEdit) {
            synchronized (fieldToMonitor) {
                makingManualEdit = true;
                needManualEdit = false;
                s.replace(0, s.length(), currentText);
                if (BuildConfig.DEBUG) Log.d(TAG, "initiating replacement to undo prohibited edit");
            }
        }
        if (makingManualEdit) {
            makingManualEdit = false;
        }

    }

    private void sendLastWord(CharSequence now, int start, int before, int count) {
        int len = currentText.length() - lastWordStart(currentText);
        sendBackspaces(len);
        int newLen = now.length() - lastWordStart(currentText);

        CharSequence add = now.subSequence(now.length() - newLen, now.length());
        texter.sendRTTChars(add.toString());
    }

    private boolean editInLastWord(int start, int before, int count) {
        if (start >= lastWordStart(currentText))
            return true;
        return false;
    }

    private int lastWordStart(CharSequence text) {
        int i = text.length() - 1;
        for (; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c))
                break;
        }
        return i + 1;
    }

    private void sendBackspaces(int howMany) {
        byte[] del = new byte[howMany];
        Arrays.fill(del, (byte) 0x08);
        texter.sendRTTChars(new String(del, StandardCharsets.UTF_8));
    }

    /**
     * Precondition: charsOnlyAppended()
     */
    private void sendAppendedChars(CharSequence now, int start, int before, int count) {
        //if (BuildConfig.DEBUG) Log.d(TAG, "chars appended");
        CharSequence added = now.subSequence(start + before, now.length());
        try {
            texter.sendRTTChars(added.toString());
        } catch (IllegalStateException e) {
            synchronized (fieldToMonitor) {
                makingManualEdit = true;
                fieldToMonitor.setText(currentText); // "current" is updated in onBeforeTextChanged(), so it's actually "old" by the time this is called
            }
        }
    }

    /**
     * Precondition: charsOnlyDeleted()
     */
    private void sendDeletionsFromEnd(CharSequence now, int start, int before, int count) {
        //Log.d(TAG, "chars deleted from end - " + start + " - " + before + " - " + count);
        sendBackspaces(before - count);
    }

    /**
     *
     */
    private void sendCompoundReplacementText(CharSequence now, int start, int before, int count) {
        sendBackspaces(before);
        CharSequence seq = now.subSequence(start, start + count);
        texter.sendRTTChars(seq.toString());
    }

    /**
     * Check whether the text that changed included the end of the entire text.
     * If not, we can't allow the edit. You must only add and delete chars
     * at the end of the text.
     */
    private boolean editOverlappedEnd(int start, int before, int count) {
        if (currentText == null)
            return true;
        return (start + before == currentText.length());
    }

    /**
     * Precondition: editOverlappedEnd()
     */
    private boolean charsOnlyAppended(CharSequence now, int start, int before, int count) {
        if (before == 0)
            return true;
        if (before >= count)
            return false; // if before == count, the last word was changed, but no additional chars appended

        CharSequence origSeq = currentText.subSequence(start, start + before);
        CharSequence newSeq = now.subSequence(start, start + before);
        String origString = origSeq.toString();
        String newString = newSeq.toString();
        if (origString.equals(newString))
            return true;
        return false;
    }

    /**
     * Precondition: editOverlappedEnd()
     */
    private boolean charsOnlyDeleted(CharSequence s, int start, int before, int count) {
        if (before <= count)
            return false; // if before == count, the last word was changed, but no net chars deleted
        if (count == 0)
            return true;
        String origString = currentText.subSequence(start, start + count).toString();
        String newString = s.subSequence(start, start + count).toString();
        if (origString.equals(newString))
            return true;
        return false;
    }

    /**
     * Precondition: before == count
     * <p>
     * for some reason, when spaces are added, the previous word is reported as changing, but it doesn't really,
     * so we are checking that here
     */
    private boolean textActuallyChanged(CharSequence now, int start, int before, int count) {
        if (currentText == null)
            return true;
        String changed = now.subSequence(start, start + count).toString();
        String origStr = currentText.toString();
        if (origStr.regionMatches(start, changed, 0, before))
            return false;
        return true;
    }
    /*
     * end RTT methods ... following methods are for en bloc mode
     */
    

    /**
     * Used in the case when the user has chosen to send entire messages, not individual characters,
     * i.e. en bloc mode. The text in the field is sent to the other party and removed from the field.
     */
    public void checkAndSend() {
        synchronized (fieldToMonitor) {
            CharSequence text = fieldToMonitor.getText();
            if (text.length() > 0) {
                String toSend = text.toString() + '\n';
                texter.sendRTTChars(toSend);
                fieldToMonitor.setText(null);
            }
        }
    }

}
