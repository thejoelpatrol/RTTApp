package com.laserscorpion.rttapp;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.javax.sip.TransactionUnavailableException;
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


public class RTTRegistrationActivity extends AppCompatActivity implements TextListener {
    public static final String TAG = "RTTRegistrationActivity";
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
        setContentView(R.layout.activity_rttregistration);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView view = (TextView)findViewById(R.id.textview);
        view.setMovementMethod(new ScrollingMovementMethod());
        REGISTRAR_PREF_NAME = getString(R.string.pref_registrar_qualified);
        USERNAME_PREF_NAME = getString(R.string.pref_username_qualified);
        PASSWORD_PREF_NAME = getString(R.string.pref_password_qualified);
        try {
            SipClient.init(this, getUsername(), getRegistrar(), getPassword(), this);
        } catch (android.javax.sip.SipException e) {
            addText("Error: failed to initialize SIP stack");
            // TODO replace this with a dialog
            // TODO prevent anything else from being attempted - kill the activity
        }
        texter = SipClient.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        register();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* not sure how i want to handle onDestroy - it seems we should
           unregister and close the socket when dying, but onDestroy is
           also called when rotating the screen
        */
        /*try {
            texter.unregister();
            texter.close();
        } catch (SipException e) {
            addText("Failed to unregister: " + e);
        }*/
        //this.unregisterReceiver(callReceiver);
    }

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

    /**
     * Register with server
     */
    private void register() {
        try {
            addText("Registering...\n");
            texter.register();
        } catch (android.net.sip.SipException e) {
            // TODO send up a dialog or something
            addText("Failed to register with server: " + e.getMessage());
            return;
        }
        
        //registerCallReceiver();
    }

    /*private void registerCallReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.laserscorpion.rttapp.INCOMING_CALL");
        callReceiver = new RTTIncomingCallReceiver();
        this.registerReceiver(callReceiver, filter);
    }*/

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
        if (contact.equals("")) {
            // TODO throw up a dialog
            addText("Error: enter an address to call\n");
            return;
        }
        Intent intent = new Intent(this, RTTCallActivity.class);
        intent.putExtra("com.laserscorpion.rttapp.contact_uri", contact);
        startActivity(intent);
        //texter.call(contact);
    }



    @Override
    public void ControlMessageReceived(String message) {
        addText(message + '\n');
    }

    @Override
    public void RTTextReceived(String text) {

    }
}



