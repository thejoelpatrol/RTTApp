package com.laserscorpion.rttapp;

import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import android.widget.EditText;
import android.widget.TextView;

public class RTTCallActivity extends AppCompatActivity {
    public static final String TAG = "RTTCallActivity";
    private String REGISTRAR_PREF_NAME; // these are basically constants
    private String USERNAME_PREF_NAME; // but you can't access xml resources statically
    private String PASSWORD_PREF_NAME;
    protected SipRTTCall call = null;
    protected SipRTTManager sipManager;
    protected RTTIncomingCallReceiver callReceiver;
    private SipClient texter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        REGISTRAR_PREF_NAME = getString(R.string.pref_registrar_qualified);
        USERNAME_PREF_NAME = getString(R.string.pref_username_qualified);
        PASSWORD_PREF_NAME = getString(R.string.pref_password_qualified);

        sipManager = new SipRTTManager(this);
        resetTexter();
    }

    @Override
    protected void onStart() {
        super.onStart();
        register();
    }

    @Override
    protected void onStop() {
        texter.unregister();
        this.unregisterReceiver(callReceiver);
        Log.d(TAG, "Unregistering " + getUsername() + '@' + getRegistrar());
        super.onStop();
    }

    /*@Override
    protected void onDestroy() {
        super.onDestroy();
    }*/

    private String getRegistrar() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(REGISTRAR_PREF_NAME, "fail");
    }

    private String getUsername() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(USERNAME_PREF_NAME, "fail");
    }

    private String getPassword() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(PASSWORD_PREF_NAME, "fail");
    }

    private void resetTexter() {
        try {
            texter = new SipClient(this, sipManager, getUsername(), getRegistrar(), getPassword());
        } catch (java.text.ParseException e) {
            // TODO throw up a dialog about unable to parse username/server
            Log.e(TAG, "Unable to parse username/server");
        }
    }

    /**
     * Register with server
     */
    private void register() {
        try {
            texter.register();
        } catch (android.net.sip.SipException e) {
            // TODO send up a dialog or something
            Log.e(TAG, "SIP exception: " + e.getMessage());
            return;
        }
        addText("Registered\n");
        registerCallReceiver();
    }

    private void registerCallReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.laserscorpion.rttapp.INCOMING_CALL");
        callReceiver = new RTTIncomingCallReceiver();
        this.registerReceiver(callReceiver, filter);
    }

    private void addText(String text) {
        TextView view = (TextView)findViewById(R.id.textview);
        String currentText = view.getText().toString();
        view.setText(currentText + text);
    }

    public void call(View view) {
        if (texter == null) {
            // TODO throw up a dialog
            return;
        }
        EditText editText = (EditText)findViewById(R.id.contact_name);
        String contact = editText.getText().toString();
        if (contact == null) {
            // TODO throw up a dialog
            return;
        }
        addText("Calling...\n");
        texter.call(contact);
    }
}



