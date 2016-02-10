package com.laserscorpion.rttapp;

import android.javax.sip.SipException;
import android.javax.sip.TransactionUnavailableException;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.text.ParseException;


public class RTTCallActivity extends AppCompatActivity implements TextListener, SessionListener {
    public static final String TAG = "RTTCallActivity";
    private SipClient texter;
    private String contact_URI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView view = (TextView)findViewById(R.id.textview);
        view.setMovementMethod(new ScrollingMovementMethod());
        texter = SipClient.getInstance();
        texter.addTextReceiver(this);
        texter.addSessionListener(this);
        contact_URI = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            texter.call(contact_URI);
        } catch (ParseException e) {
            addText("Invalid contact address: " + contact_URI);
        } catch (TransactionUnavailableException e) {
            addText("Can't call right now - SIP stack busy");
        } catch (android.javax.sip.SipException e) {
            addText("Call failed: " + e.getMessage());
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        texter.hangUp();
    }


    private void addText(final String text) {
        final TextView view = (TextView)findViewById(R.id.textview);
        //String currentText = view.getText().toString();
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

    @Override
    public void SessionClosed() {

    }

    @Override
    public void SessionFailed(String reason) {
        addText("Failed to establish call: " + reason); // TODO replace with dialog
        try {
            Thread.sleep(2000,0);
        } catch (InterruptedException e) {
        }
        finish();
    }
}



