package com.laserscorpion.rttapp;

import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.sip.SipException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.text.ParseException;


public class RTTCallActivity extends AppCompatActivity implements TextListener {
    public static final String TAG = "RTTCallActivity";
    private SipClient texter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView view = (TextView)findViewById(R.id.textview);
        view.setMovementMethod(new ScrollingMovementMethod());

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
        view.setText(currentText + text);
    }

    @Override
    public void TextMessageReceived(String message) {
        addText(message + '\n');
    }
}



