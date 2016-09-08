package com.laserscorpion.rttapp.ui;

import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.laserscorpion.rttapp.R;

/**
 * Nothing to see here, move along.
 * This is sort of a wrapper around the SettingsActivityFragment. Why it is necessary to have both,
 * I'm not exactly sure. I'm just doing what the documentation said to do. It says to use Fragments
 * (e.g. PreferenceFragment) now instead of entire Activities, but there is a PreferenceActivity we
 * could probably use instead. I think at the time I created this, I could only find an example of
 * the PreferenceFragment in the official guides, but now I'm not sure we have to do it that way.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
           /* getFragmentManager().beginTransaction().replace(android.R.id.content,
                    new SettingsActivityFragment()).commit(); */
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return true;
    }



    private static int getContentViewCompat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH ?
                android.R.id.content : R.id.action_bar_activity_content;
    }
}
