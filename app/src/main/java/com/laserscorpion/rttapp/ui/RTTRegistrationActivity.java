/* © 2016 Joel Cretan
 *
 * This is part of RTTAPP, an Android RFC 4103 real-time text app
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.laserscorpion.rttapp.sip.CallReceiver;
import com.laserscorpion.rttapp.R;
import com.laserscorpion.rttapp.sip.SipClient;
import com.laserscorpion.rttapp.sip.TextListener;

import java.text.ParseException;

/**
 * This activity initiates SIP registration on launch, and basically serves as the "home" screen
 * when the user is not on a call. Outgoing calls are started from this screen. It could probably
 * use some re-designing to remove the SIP log and replace it with something more useful, such as a
 * list of recent calls or some interface for viewing saved conversations.
 */
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
    protected void onRestart() {
        // onRestart may be called after changing settings, so make sure credentials are up to date with the SipClient
        super.onRestart();
        try {
            texter.reset(this, getUsername(), getRegistrar(), getPassword(), this);
        } catch (SipException e) {
            Log.e(TAG, "Failed to initialize SIP stack", e);
            showFailDialog(e.getMessage());
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_first, menu);
        return true;
    }

    private void launchSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            launchSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
