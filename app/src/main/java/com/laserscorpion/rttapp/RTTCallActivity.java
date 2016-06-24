package com.laserscorpion.rttapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public class RTTCallActivity extends AppCompatActivity implements TextListener, SessionListener, TextWatcher {
    private class Edit {
        public int start;
        public int before;
        public int count;
    }
    private class CleanString {
        public int backspaces = 0;
        public String str = new String();
    }

    public static final String TAG = "RTTCallActivity";
    private static final String STATE_1 = "currentText";
    private static final String STATE_2 = "receivedText";
    private SipClient texter;
    private CharSequence currentText;
    private Edit previousEdit;
    private boolean makingManualEdit = false;
    private boolean needManualEdit = false; // flag that indicates that we need to undo the text change the user made - not allowed to edit earlier text
    private boolean screenRotated = false;
    private String otherParty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(false);
        TextView view = (TextView)findViewById(R.id.textview);
        EditText edit = (EditText)findViewById(R.id.compose_message);
        edit.addTextChangedListener(this);
        view.setMovementMethod(new ScrollingMovementMethod());

        texter = SipClient.getInstance();
        texter.addSessionListener(this);
        texter.addTextReceiver(this);

        otherParty = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
        setTitle("Call with " + otherParty);

        if (savedInstanceState != null) {
            CharSequence oldText = savedInstanceState.getCharSequence(STATE_1);
            currentText = oldText;
            CharSequence receivedText = savedInstanceState.getCharSequence(STATE_2);
            screenRotated = true;
            view.setText(receivedText);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        texter.removeTextReceiver(this);
        texter.removeSessionListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        TextView view = (TextView)findViewById(R.id.textview);
        EditText edit = (EditText)findViewById(R.id.compose_message);
        outState.putCharSequence(STATE_1, edit.getText());
        outState.putCharSequence(STATE_2, view.getText());
        super.onSaveInstanceState(outState);
    }

    private synchronized void addText(final String text) {
        final TextView view = (TextView)findViewById(R.id.textview);
        final CleanString toAdd = countAndRemoveBackspaces(text);
        final CharSequence existing;

        CharSequence textWithNoFEFF = cleanText(view.getText());
        if (toAdd.backspaces > 0) {
            int newlen = Math.max(0, textWithNoFEFF.length() - toAdd.backspaces);
            existing = textWithNoFEFF.subSequence(0, newlen);
        } else
           existing = textWithNoFEFF;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (view) {
                    view.setText(existing);
                    view.append(toAdd.str);
                    view.notify();
                }
            }
        });
        synchronized (view) {
            try {
                view.wait(1000);
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Android framework programmers think that \uFEFF is an acceptable thing to leave in a string,
     * especially at the end. Unicode has deprecated the usage of this character as a non-breaking
     * space. These Google devs don't care and use this invisible character to save having to edit
     * the string sometimes, when a char is deleted at the end. What, do they think that no one will
     * ever notice that charSequence.length() != actualRealUsefulCharacters.length()? Why don't they
     * just use a null char? I say this is a pure bug in CharSequence. Use null!
     * @param dirty the characters that stupidly end with some number of \uFEFF chars
     * @return the normal sensible part of this text
     */
    private CharSequence cleanText(CharSequence dirty) {
        int badChars = 0;
        for (int i = dirty.length() - 1; i >= 0; i--) {
            if (dirty.charAt(i) == '\uFEFF')
                badChars++;
            else break;
        }
        if (badChars > 0) {
            Log.d(TAG, "len: " + dirty.length() + " bad: " + badChars + " - " + dirty);
            return dirty.subSequence(0, dirty.length() - badChars);
        } else
            return dirty;

    }

    /**
     * Process incoming text and remove backspaces and the characters they are meant to delete, plus
     * count how many additional backspaces need to be used to modify other text
     * @param text the dirty string that may contain backspaces, which we need to remove
     * @return a CleanString, which is a small struct containing the actual clean string to print to the screen,
     *          and a count of how many additional backspaces were encountered that need to be used to delete chars
     *          already printed
     */
    private CleanString countAndRemoveBackspaces(String text) {
        CleanString clean = new CleanString();
        int i;
        int initialBS = 0;
        for (i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\u0008')
                break;
            initialBS++;
        }
        int additionalBS = 0;
        for (; i < text.length(); i++) {
            if (text.charAt(i) == '\u0008') {
                if (clean.str.length() > 0)
                    clean.str = clean.str.substring(0, clean.str.length() - 1);
                else
                    additionalBS++;
            } else {
                clean.str = clean.str + text.charAt(i);
            }
        }
        if (additionalBS > 0)
            clean.backspaces = initialBS + additionalBS;
        else
            clean.backspaces = initialBS;
        if (BuildConfig.DEBUG) Log.d(TAG, "bs: " + clean.backspaces);
        if (BuildConfig.DEBUG) Log.d(TAG, "adding: " + clean.str);
        return clean;
    }

    @Override
    public void ControlMessageReceived(String message) {
        addControlText(message);
    }

    @Override
    public void RTTextReceived(String text) {
        addText(text);
    }

    @Override
    public void SessionEstablished(String userName) {
        addControlText("Connected!");
        setCallerText(userName + " says:\n");
    }

    private void addControlText(final String text) {
        final TextView view = (TextView)findViewById(R.id.control_messages);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (view) {
                    view.append("\n" + text);
                    view.notify();
                }
            }
        });
        synchronized (view) {
            try {
                view.wait(1000);
            } catch (InterruptedException e) {}
        }
    }

    private void setCallerText(final String text) {
        final TextView view = (TextView)findViewById(R.id.other_party_label);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (view) {
                    view.append(text);
                    view.notify();
                }
            }
        });
        synchronized (view) {
            try {
                view.wait(1000);
            } catch (InterruptedException e) {}
        }
    }



    public void hangUp(View view) {
        texter.hangUp();
        finish();
    }

    // TODO maybe unify all these runonuithread() calls by passing in a smaller bit of code somehow?

    @Override
    public void SessionClosed() {
        addControlText("***" + otherParty + " hung up.");
        // TODO replace with dialog, ask to save text
        try {
            Thread.sleep(2000,0);
        } catch (InterruptedException e) { }
        finish();
    }

    @Override
    public void SessionFailed(String reason) {
        addControlText("Failed to establish call: " + reason); // TODO replace with dialog
        try {
            Thread.sleep(1000,0);
        } catch (InterruptedException e) {}
        finish();
    }

    @Override
    public synchronized void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (makingManualEdit)
            return;
        currentText = s.subSequence(0, s.length()); // deep copy the text before it is changed so we can compare before and after the edit
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
        if (BuildConfig.DEBUG) Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

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
        /*previousEdit = new Edit();
        previousEdit.start = start;
        previousEdit.before = before;
        previousEdit.count = count;*/
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
        } catch  (IllegalStateException e) {
            addText("Can't send text yet - call not connected\n");
            makingManualEdit = true;
            EditText edit = (EditText)findViewById(R.id.compose_message);
            edit.setText(null);
        }
    }

    /**
     * Precondition: charsOnlyDeleted()
     */
    private void sendDeletionsFromEnd(CharSequence now, int start, int before, int count) {
        Log.d(TAG, "chars deleted from end - " + start + " - " + before + " - " + count);
        sendBackspaces(before - count);
        /*if (previousEdit != null && previousEdit.start == start && previousEdit.count == 0) {
            // this is the case where the last word has been deleted in two steps by the keyboard, i.e. the cursor is in the middle of the word and it is replaced
            sendBackspaces(previousEdit.before);

            // this is no longer necessary - we now handle editing the last word all at once
        }*/
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
     *
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



