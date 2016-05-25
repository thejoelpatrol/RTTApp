package com.laserscorpion.rttapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class IncomingCallActivity extends AppCompatActivity implements SessionListener {
    SipClient sipClient;
    String from;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);
        sipClient = SipClient.getInstance();
        sipClient.addSessionListener(this);
        from = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
        setTitle("Call from " + from);
    }

    public void acceptCall(View view) {
        Intent intent = new Intent(this, RTTCallActivity.class);
        intent.putExtra("com.laserscorpion.rttapp.contact_uri", from);
        startActivity(intent);
        sipClient.acceptCall();
        close();
    }

    public void declineCall(View view) {
        sipClient.declineCall();
        close();
    }

    @Override
    public void onBackPressed() {
        sipClient.declineCall();
        close();
    }

    private void close() {
        sipClient.removeSessionListener(this);
        finish();
    }

    @Override
    public void SessionEstablished(String userName) {
        // this should not be called here
    }

    @Override
    public void SessionClosed() {
        // TODO throw up dialog: other party hung up
        close();
    }

    @Override
    public void SessionFailed(String reason) {
        // TODO throw up dialog: reason
        close();
    }
}
