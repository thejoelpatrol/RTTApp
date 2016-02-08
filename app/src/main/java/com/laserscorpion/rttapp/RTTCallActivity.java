package com.laserscorpion.rttapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;


public class RTTCallActivity extends AppCompatActivity implements TextListener {
    public static final String TAG = "RTTCallActivity";
    private SipClient texter;
    private TextListener previousTextListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView view = (TextView)findViewById(R.id.textview);
        view.setMovementMethod(new ScrollingMovementMethod());
        texter = (SipClient)savedInstanceState.get("com.laserscorpion.rttapp.SipClient");
        texter.addTextReceiver(this);
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
    }


    private void addText(String text) {
        TextView view = (TextView)findViewById(R.id.textview);
        String currentText = view.getText().toString();
        view.append(text);
    }

    @Override
    public void ControlMessageReceived(String message) {
    }

    @Override
    public void RTTextReceived(String text) {
        addText(text);
    }
}



