package com.laserscorpion.rttapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class IncomingCallActivity extends AppCompatActivity {
    SipClient sipClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);
        sipClient = SipClient.getInstance();
    }

    public void acceptCall(View view) {
        Intent intent = new Intent(this, RTTCallActivity.class);
        //intent.putExtra("com.laserscorpion.rttapp.contact_uri", contact);
        startActivity(intent);
        sipClient.acceptCall();
        finish();
    }

    public void declineCall(View view) {
        sipClient.declineCall();
        finish();
    }

    @Override
    public void onBackPressed() {
        sipClient.declineCall();
        finish();
    }

}
