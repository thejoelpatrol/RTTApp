package com.laserscorpion.rttapp.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import com.laserscorpion.rttapp.BuildConfig;
import com.laserscorpion.rttapp.R;
import com.laserscorpion.rttapp.sip.SipClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The TextEntryMonitor, one of which is created by the RTTCallActivity at a time,
 * listens for input on the user's text field (EditText), and keeps track of which
 * characters have changed. If text is added or deleted at the end, it tells the SipClient to send
 * the new characters (possibly '\d') in the real-time call. If edits are made earlier in the text,
 * it undoes them, since the user is not allowed to add or remove text anywhere besides the
 * end of the field.
 */
public class TextEntryMonitor implements TextWatcher {
    private static final String TAG = "TextWatcher";
    private Context parent;
    private EditText fieldToMonitor;
    private CharSequence currentText;
    private SipClient texter;
    private boolean makingManualEdit = false;
    private boolean needManualEdit = false; // flag that indicates that we need to undo the text change the user made - not allowed to edit earlier text
    private boolean screenRotated = false;

    public TextEntryMonitor(Context context, EditText watchThis, SipClient sipClient, CharSequence startText, boolean screenRotated) {
        parent = context;
        fieldToMonitor = watchThis;
        fieldToMonitor.addTextChangedListener(this);
        currentText = startText;
        texter = sipClient;
        this.screenRotated = screenRotated;
    }

    /**
     * This is the bulk of the logic to determine which characters to send to the other party when the user enters text
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
            Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

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
        if (BuildConfig.DEBUG) Log.d(TAG, "chars appended");
        CharSequence added = now.subSequence(start + before, now.length());
        try {
            texter.sendRTTChars(added.toString());
        } catch (IllegalStateException e) {
            //addText("Can't send text yet - call not connected\n");
            makingManualEdit = true;
            fieldToMonitor.setText(null);
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

    @Override
    public synchronized void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (makingManualEdit)
            return;
        currentText = s.subSequence(0, s.length()); // deep copy the text before it is changed so we can compare before and after the edit
    }

    @Override
    public synchronized void afterTextChanged(Editable s) {
        if (needManualEdit) {
            makingManualEdit = true;
            needManualEdit = false;
            s.replace(0, s.length(), currentText);
            if (BuildConfig.DEBUG) Log.d(TAG, "initiating replacement to undo prohibited edit");
        }
        if (makingManualEdit) {
            makingManualEdit = false;
        }

    }


}
