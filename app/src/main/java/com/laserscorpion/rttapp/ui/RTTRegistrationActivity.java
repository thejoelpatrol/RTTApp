package com.laserscorpion.rttapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.javax.sip.SipException;
import android.javax.sip.TransactionUnavailableException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.laserscorpion.rttapp.sip.CallReceiver;
import com.laserscorpion.rttapp.R;
import com.laserscorpion.rttapp.sip.SipClient;
import com.laserscorpion.rttapp.sip.TextListener;

import java.text.ParseException;


public class RTTRegistrationActivity extends AppCompatActivity implements TextListener,
        CallReceiver,
                                                                            AbstractDialog.DialogListener,
        FailDialog.FailDialogListener {
    public static final String TAG = "RTTRegistrationActivity";
    private String REGISTRAR_PREF_NAME; // these are basically constants
    private String USERNAME_PREF_NAME; // but you can't access xml resources statically
    private String PASSWORD_PREF_NAME;
    private SipClient texter;
    private boolean initialized = false;

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
            texter = SipClient.getInstance();
            texter.registerCallReceiver(this);
            initialized = true;
        } catch (SipException e) {
            Log.e(TAG, "Failed to initialize SIP stack", e);
            showFailDialog(e.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (initialized) {
            texter.addTextReceiver(this);
            register();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (texter != null)
            texter.removeTextReceiver(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* not sure how i want to handle onDestroy - it seems we should
           unregister and close the socket when dying, but onDestroy is
           also called when rotating the screen

           For now, screen rotation is disabled in this activity
        */
        try {
            if (texter != null) {
                texter.unregister();
                texter.close();
            }
        } catch (SipException e) {
            addText("Failed to unregister: " + e);
        }
    }

    private synchronized void waitUninteruptably() {
        while (true) {
            try {
                wait();
                return;
            } catch (InterruptedException e1) {}
        }
    }

    private void showErrorDialog(String message) {
        DismissableDialog dialog = DismissableDialog.newInstance(message);
        dialog.show(getFragmentManager(), "error");
    }

    private void showFailDialog(String message) {
        FailDialog dialog = FailDialog.newInstance(message);
        dialog.show(getFragmentManager(), "error");
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
        } catch (SipException e) {
            showFailDialog(e.getMessage());
            return;
        }
    }

    private void addText(final String text) {
        final TextView view = (TextView)findViewById(R.id.textview);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.append(text);
            }
        });
    }

    public void call(View view) {
        if (texter == null) {
            showFailDialog("Error: failed to initialize app, can't call. Quit and try again.");
            return;
        }
        EditText editText = (EditText)findViewById(R.id.contact_name);
        String contact = editText.getText().toString();
        if (contact.equals("")) {
            showErrorDialog("Error: enter an address to call");
            return;
        }
        try {
            texter.call(contact);
            Intent intent = new Intent(this, RTTCallActivity.class);
            intent.putExtra("com.laserscorpion.rttapp.contact_uri", contact);
            startActivity(intent);
        } catch (ParseException e) {
            showErrorDialog("Bad contact address: " + contact + " (" + e.getMessage() + ")\n");
        } catch (TransactionUnavailableException e) {
            showErrorDialog("Can't call: " + e.getMessage() + "\n");
        } catch (Exception e) {
            showErrorDialog("Call failed: " + e.getMessage() + "\n");
        }
    }

    /*
     * Java really encourages you to use a lot of interface callbacks, doesn't it?
     */
    @Override
    public void controlMessageReceived(String message) {
        addText(message + '\n');
    }

    @Override
    public void RTTextReceived(String text) {}

    @Override
    public void callReceived(String from) {
        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.putExtra("com.laserscorpion.rttapp.contact_uri", from);
        startActivity(intent);
    }

    @Override
    public synchronized void dialogDismissed() {
        //notify();
    }
    @Override
    public synchronized void dialogFail() {
        finish();
    }

}
