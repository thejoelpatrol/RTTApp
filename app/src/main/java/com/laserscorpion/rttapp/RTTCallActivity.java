package com.laserscorpion.rttapp;

import android.javax.sip.SipException;
import android.javax.sip.TransactionUnavailableException;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.text.ParseException;


public class RTTCallActivity extends AppCompatActivity implements TextListener, SessionListener {
    public static final String TAG = "RTTCallActivity";
    private SipClient texter;
    //private String contact_URI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(false);
        TextView view = (TextView)findViewById(R.id.textview);
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

    }

    public void hangUp(View view) {
        texter.hangUp();
        finish();
    }

    @Override
    public void SessionClosed() {
        addText("Other party hung up.\n");
        try {
            Thread.sleep(2000,0);
        } catch (InterruptedException e) {
        }
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
}



