package com.laserscorpion.rttapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.laserscorpion.rttapp.R;

/**
 * This is
 * in fact the first activity that starts when the app is launched but it is often not visible then.
 * THis is the screen where the user chooses a server to Register with, and initiates registration.
 * If credentials are available, i.e. the user has saved their preferences already, this activity
 * automatically launches the next one, RTTRegistrationActivity. If not, it automatically launches
 * SettingsActivity, where the user can enter credentials to save. This activity is displayed after
 * navigating up from either of those. THis activity has no methods that need to be called by any
 * other user classes, just automatic ones.
 */
public class FirstActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.laserscorpion.rttapp.MESSAGE";
    public static final String TAG = "RTTApp";
    private String REGISTRAR_PREF_NAME; // these are basically constants
    private String SIP_USER_PREF_NAME;
    private String SIP_PASSWORD_PREF_NAME;
    private boolean launchedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        REGISTRAR_PREF_NAME = getString(R.string.pref_registrar_qualified);
        SIP_USER_PREF_NAME = getString(R.string.pref_username_qualified);
        SIP_PASSWORD_PREF_NAME = getString(R.string.pref_password_qualified);

        setContentView(R.layout.activity_first);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        if (!launchedOnce) {
            // automatically show other activities only once
            // after that, the user has already interacted with the app and made changes,
            // so they may want to be on this screen
            if (prefsAreSet()) {
                doRegister();
            } else {
                launchSettings();
            }
            launchedOnce = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EditText serverField = (EditText)findViewById(R.id.server_addr);
        serverField.setText(getRegistrar());
        TextView user = (TextView)findViewById(R.id.sip_username);
        user.setText(getUsername() + "@");
    }

    private boolean prefsAreSet() {
        String registrar = getRegistrar();
        String username = getUsername();
        String password = getPassword();
        if (registrar.equals("") || username.equals("") || password.equals(""))
            return false;
        return true;
        //Log.e(TAG, registrar + ":" + username + ":" + password);
        //return registrar != null && username != null && password != null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private String getRegistrar() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(REGISTRAR_PREF_NAME, "");
    }

    private String getUsername() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(SIP_USER_PREF_NAME, "");
    }

    private String getPassword() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(SIP_PASSWORD_PREF_NAME, "");
    }

    public void register(View view) {
        setServerPref();
        doRegister();
    }

    private void doRegister() {
        Intent intent = new Intent(this, RTTRegistrationActivity.class);
        startActivity(intent);
    }

    private void setServerPref() {
        EditText serverField = (EditText)findViewById(R.id.server_addr);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(REGISTRAR_PREF_NAME, serverField.getText().toString());
        editor.commit();
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
