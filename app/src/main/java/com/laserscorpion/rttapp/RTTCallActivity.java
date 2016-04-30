package com.laserscorpion.rttapp;

import android.javax.sip.SipException;
import android.javax.sip.TransactionUnavailableException;
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
import java.text.ParseException;
import java.util.Arrays;


public class RTTCallActivity extends AppCompatActivity implements TextListener, SessionListener, TextWatcher {
    private class Edit {
        public int start;
        public int before;
        public int count;
    }

    public static final String TAG = "RTTCallActivity";
    private static final String STATE = "currentText";
    private SipClient texter;
    private CharSequence currentText;
    private Edit previousEdit;

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
        texter.addTextReceiver(this);
        texter.addSessionListener(this);

        if (savedInstanceState != null) {
            CharSequence oldText = savedInstanceState.getCharSequence(STATE);
            currentText = oldText;
        }
        //contact_URI = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
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
        //texter.hangUp();
        texter.removeTextReceiver(this);
        texter.removeSessionListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putCharSequence(STATE, currentText);
        super.onSaveInstanceState(outState);
    }

    private synchronized void addText(final String text) {
        final TextView view = (TextView)findViewById(R.id.textview);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.append(text);
            }
        });
    }

    @Override
    public void ControlMessageReceived(String message) {
        addText(message + '\n');
    }

    @Override
    public void RTTextReceived(String text) {
        addText(text);
    }

    @Override
    public void SessionEstablished() {
        addText("Connected!\n");
    }

    public void hangUp(View view) {
        texter.hangUp();
        finish();
    }

    @Override
    public void SessionClosed() {
        addText("Other party hung up.\n");
        // TODO replace with dialog, ask to save text
        try {
            Thread.sleep(2000,0);
        } catch (InterruptedException e) { }
        finish();
    }

    @Override
    public void SessionFailed(String reason) {
        addText("Failed to establish call: " + reason); // TODO replace with dialog
        try {
            Thread.sleep(1000,0);
        } catch (InterruptedException e) {
        }
        finish();
    }

    @Override
    public synchronized void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (s.length() > 0)
            currentText = s.subSequence(0, s.length()); // deep copy the text before it is changed so we can compare the new and old versions
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

        synchronized (this) {
            //CharSequence changedChars = s.subSequence(start, start + count);

            if (count > before) {
                if (charsOnlyAppended(s, start, before, count)) {
                    Log.d(TAG, "chars appended");
                    CharSequence added = s.subSequence(start + before, s.length());
                    texter.sendRTTChars(added.toString());
                } else {
                    if (editOverlappedEnd(start, before, count)) {
                        Log.d(TAG, "edit overlapped end");
                        sendBackspaces(before);
                        CharSequence added = s.subSequence(start, s.length());
                        texter.sendRTTChars(added.toString());
                    } else {
                        Log.d(TAG, "Some kind of complex edit occurred 1");
                    }
                }
            } else if (count < before) {
                if (charsOnlyDeleted(s, start, before, count)) {
                    Log.d(TAG, "chars deleted from end");
                    sendBackspaces(before - count);
                    if (previousEdit != null && previousEdit.start == start && previousEdit.count == 0) {
                        // this is the case where the last word has been deleted in two steps by the keyboard, i.e. the cursor is in the middle of the word and it is replaced
                        sendBackspaces(previousEdit.before);
                    }
                } else {
                    Log.d(TAG, "Some kind of complex edit occurred 2");
                }
            } else {
                // we know before == count
                if (editOverlappedEnd(start, before, count)) {
                    if (textActuallyChanged(s, start, before, count)) {
                        // when entering a space, the previous word is reported as changing for some reason, but it hasn't actually changed, so we must check
                        sendCompoundReplacementText(s, start, before, count);
                    } else {
                        Log.d(TAG, "text did not actually change");
                        //return;
                    }
                } else {
                    Log.d(TAG, "Some kind of complex edit occurred 3");
                }
            }
            previousEdit = new Edit();
            previousEdit.start = start;
            previousEdit.before = before;
            previousEdit.count = count;
        }
    }

    private void sendBackspaces(int howMany) {
        byte[] del = new byte[howMany];
        Arrays.fill(del, (byte) 0x08);
        texter.sendRTTChars(new String(del, StandardCharsets.UTF_8));
    }

    /**
     * Precondition: before == count
     */
    private void sendCompoundReplacementText(CharSequence now, int start, int before, int count) {
        sendBackspaces(count);
        CharSequence seq = now.subSequence(start, start + count);
        texter.sendRTTChars(seq.toString());
    }

    private boolean editOverlappedEnd(int start, int before, int count) {
        if (count < before)
            return false;
        if (start + before == currentText.length())
            return true;
        return false;
    }

    private boolean charsOnlyAppended(CharSequence now, int start, int before, int count) {
        if (currentText == null)
            return true;
        if (!editOverlappedEnd(start, before, count))
            return false; // some text was changed that was not the LAST part of the string
        if (before == 0)
            return true;
        if (before == count)
            return false; // the last word was changed, but no additional chars appended

        CharSequence origSeq = currentText.subSequence(start, start + before);
        CharSequence newSeq = now.subSequence(start, start + before);
        String origString = origSeq.toString();
        String newString = newSeq.toString();
        if (origString.equals(newString))
            return true;
        return false;
    }

    /**
     * Precondition: before > count
     */
    private boolean charsOnlyDeleted(CharSequence s, int start, int before, int count) {
        if (start + before != currentText.length())
            return false;
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

    /**
     * Precondition: before  count
    */
    /* private boolean textWasReplaced(CharSequence now, int start, int before, int count) {
        return false;
    }*/

    @Override
    public void afterTextChanged(Editable s) {

    }
}



