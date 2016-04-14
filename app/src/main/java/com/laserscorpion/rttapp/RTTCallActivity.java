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


public class RTTCallActivity extends AppCompatActivity implements TextListener, SessionListener, TextWatcher {
    public static final String TAG = "RTTCallActivity";
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
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Log.d(TAG, "text changed! start: " + start + " | before: " + before + " | count: " + count);

        synchronized (this) {
            CharSequence changedChars = s.subSequence(start, start + count);

            if (count > before) {
                if (charsOnlyAppended(changedChars, start, before)) {
                    CharSequence added = s.subSequence(start + before, s.length());
                    texter.sendRTTChars(added.toString());
                }

            } else {
                if (charsOnlyDeleted()) {
                    byte[] del = new byte[1];
                    del[0] = (byte)0x08;
                    texter.sendRTTChars(new String(del, StandardCharsets.UTF_8));
                }
            }
            //texter.sendRTTChars("a");
            currentText = s;
        }
    }

    private boolean charsOnlyAppended(CharSequence added, int start, int before) {
        if (currentText == null)
            return true;
        if (before == 0)
            return true;
        CharSequence origSeq = currentText.subSequence(start, start + before);
        CharSequence currentSeq = added.subSequence(0, before);
        if (origSeq.equals(currentSeq))
            return true;
        return false;
    }

    private boolean charsOnlyDeleted() {
        return true;
    }

    @Override
    public void afterTextChanged(Editable s) {

    }
}



