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
    public static final String TAG = "RTTCallActivity";
    private static final String STATE = "currentText";
    private SipClient texter;
    private CharSequence currentText;
    //private String contact_URI;

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
            currentText = s.subSequence(0, s.length());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

        synchronized (this) {
            //CharSequence changedChars = s.subSequence(start, start + count);

            if (count > before) {
                if (charsOnlyAppended(s, start, before, count)) {
                    CharSequence added = s.subSequence(start + before, s.length());
                    texter.sendRTTChars(added.toString());
                } else {
                    Log.d(TAG, "Some kind of complex edit occurred 1");
                }

            } else if (count < before) {
                if (charsOnlyDeleted(s, start, before, count)) {
                    sendBackspaces(before - count);
                } else {
                    Log.d(TAG, "Some kind of complex edit occurred 2");
                    /*if (textWasReplaced(s, start, before, count)) {

                    }*/
                }
            } else {
                if (textActuallyChanged(s, start, before, count)) {
                    sendCompoundReplacementText(s, start, before, count);
                } else {
                    Log.d(TAG, "text did not actually change");
                    return;
                }
            }
            //texter.sendRTTChars("a");
            //currentText = s;
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

    private boolean charsOnlyAppended(CharSequence now, int start, int before, int count) {
        if (currentText == null)
            return true;
        if (before == 0)
            return true;
        if (start + before != currentText.length())
            return false; // some text was changed that was not the LAST part of the string

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



