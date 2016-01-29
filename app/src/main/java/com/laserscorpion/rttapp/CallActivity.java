package com.laserscorpion.rttapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class CallActivity extends AppCompatActivity {
    private TextView textView;
    private SharedPreferences prefs;

    private void addText(String toAdd) {
        String message = textView.getText().toString();
        String newMessage = message + toAdd + '\n';
        textView.setText(newMessage);
    }

    private void setUser(String username) {
        SharedPreferences.Editor editor = prefs.edit();
        if (prefs.contains("username")) {
            String oldUser = prefs.getString("username", "--");
            if (oldUser.equals(username)) {
                addText("Same user, no change\n");
            } else {
                addText("Replacing " + oldUser + " with " + username + '\n');
                editor.putString("username", username);
            }
        } else {
            addText("Adding new user: " + username + '\n');
            editor.putString("username", username);
        }
        editor.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String message = intent.getStringExtra(FirstActivity.EXTRA_MESSAGE) + '\n';

        textView = new TextView(this);
        textView.setTextSize(12);
        textView.setText(message);
        setContentView(textView);

        prefs = this.getPreferences(Context.MODE_PRIVATE);
        setUser(message);
    }



    @Override
    protected void onStart() {
        super.onStart();
        addText("Started");
    }

    @Override
    protected void onResume() {
        super.onResume();
        addText("Resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        addText("Paused");
    }

    @Override
    protected void onStop() {
        super.onStop();
        addText("Stopped");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        addText("Restarted");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        addText("Destoyed");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        addText("Saving");
        String currentText = textView.getText().toString();
        savedInstanceState.putString("text", currentText);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String savedText = savedInstanceState.getString("text");
        textView.setText(savedText);
        addText("Restoring");
    }
}
