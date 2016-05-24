package com.laserscorpion.rttapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class FirstActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.laserscorpion.rttapp.MESSAGE";
    public static final String TAG = "RTTApp";
    private String REGISTRAR_PREF_NAME; // these are basically constants

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        REGISTRAR_PREF_NAME = getString(R.string.pref_registrar_qualified);

        setContentView(R.layout.activity_first);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EditText serverField = (EditText)findViewById(R.id.server_addr);
        serverField.setText(getRegistrar());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private String getRegistrar() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(REGISTRAR_PREF_NAME, "fail");
    }

    public void register(View view) {
        setServerPref();
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
