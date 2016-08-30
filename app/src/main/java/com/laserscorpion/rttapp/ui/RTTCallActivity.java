package com.laserscorpion.rttapp.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.laserscorpion.rttapp.BuildConfig;
import com.laserscorpion.rttapp.R;
import com.laserscorpion.rttapp.sip.SessionListener;
import com.laserscorpion.rttapp.sip.SipClient;
import com.laserscorpion.rttapp.sip.TextListener;


public class RTTCallActivity extends AppCompatActivity implements TextListener,
                                                                    SessionListener,
        FailDialog.FailDialogListener,
                                                                    AbstractDialog.DialogListener,
                                                                    CallEndDialog.SaveDialogListener {

    private class CleanString {
        public int backspaces = 0;
        public String str = new String();
    }

    public static final String TAG = "RTTCallActivity";
    private static final String STATE_1 = "currentText";
    private static final String STATE_2 = "receivedText";
    private static final String STATE_3 = "controlText";
    private SipClient texter;
    private String otherParty;
    TextEntryMonitor textHandler; // this watches text input and sends the RTT chars

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rttcall);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(false);
        TextView view = (TextView) findViewById(R.id.textview);
        TextView control = (TextView) findViewById(R.id.control_messages);
        EditText edit = (EditText) findViewById(R.id.compose_message);
        view.setMovementMethod(new ScrollingMovementMethod());
        control.setMovementMethod(new ScrollingMovementMethod());

        texter = SipClient.getInstance();
        texter.addSessionListener(this);
        texter.addTextReceiver(this);

        otherParty = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
        setTitle("Call with " + otherParty);
        addControlText("Status:");

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useRealTime = pref.getBoolean(getString(R.string.pref_use_realtime_qualified), true);;
        if (!useRealTime) {
            Button send = (Button)findViewById(R.id.sendButton);
            send.setVisibility(View.VISIBLE);
        }

        if (savedInstanceState != null) {
            CharSequence currentText = savedInstanceState.getCharSequence(STATE_1);
            CharSequence receivedText = savedInstanceState.getCharSequence(STATE_2);
            view.setText(receivedText);
            CharSequence controlText = savedInstanceState.getCharSequence(STATE_3);
            control.setText(controlText);
            setCallerText(otherParty + " says:");
            textHandler = new TextEntryMonitor(edit, useRealTime, texter, currentText, true);
        } else {
            textHandler = new TextEntryMonitor(edit, useRealTime, texter, null, false);
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
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
        texter.removeTextReceiver(this);
        texter.removeSessionListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        TextView view = (TextView) findViewById(R.id.textview);
        EditText edit = (EditText) findViewById(R.id.compose_message);
        TextView control = (TextView) findViewById(R.id.control_messages);
        outState.putCharSequence(STATE_1, edit.getText());
        outState.putCharSequence(STATE_2, view.getText());
        outState.putCharSequence(STATE_3, control.getText());
        super.onSaveInstanceState(outState);
    }

    /**
     * add text to the other party's incoming text field, i.e. the main text area. If you don't jump
     * through some hoops involving runOnUiThread(), you never really know when it will be displayed
     * @param text
     */
    private synchronized void addText(final String text) {
        final TextView view = (TextView) findViewById(R.id.textview);
        final CleanString toAdd = countAndRemoveBackspaces(text);
        final CharSequence existing;

        CharSequence textWithNoFEFF = cleanText(view.getText());
        if (toAdd.backspaces > 0) {
            int newlen = Math.max(0, textWithNoFEFF.length() - toAdd.backspaces);
            existing = textWithNoFEFF.subSequence(0, newlen);
        } else
            existing = textWithNoFEFF;

        UiRunner lambda = (v, t, e) -> {
            v.setText(e);
            v.append(t);
        };
        runOnUiThread(view, lambda, toAdd.str, existing.toString());
    }

    interface UiRunner {
        void runonuithread(TextView view, String text1, String text2);
    }

    void runOnUiThread(final TextView view, final UiRunner lambda, final String text1, final String text2) {
        runOnUiThread(new Runnable() { // super
            @Override
            public void run() {
                synchronized (view) {
                    lambda.runonuithread(view, text1, text2);
                    view.notify();
                }
            }
        });
        synchronized (view) {
            try {
                view.wait(1000);
            } catch (InterruptedException e) {}
        }

    }


    /**
     * Android framework programmers think that \uFEFF is an acceptable thing to leave in a string,
     * especially at the end. Unicode has deprecated the usage of this character as a non-breaking
     * space. These Google devs don't care and use this invisible character to save having to edit
     * the string sometimes, when a char is deleted at the end. What, do they think that no one will
     * ever notice that charSequence.length() != actualRealUsefulCharacters.length()? Why don't they
     * just use a null char? I say this is a pure bug in CharSequence. Use null!
     *
     * @param dirty the characters that stupidly end with some number of \uFEFF chars
     * @return the normal sensible part of this text
     */
    private CharSequence cleanText(CharSequence dirty) {
        int badChars = 0;
        for (int i = dirty.length() - 1; i >= 0; i--) {
            if (dirty.charAt(i) == '\uFEFF')
                badChars++;
            else break;
        }
        if (badChars > 0) {
            Log.d(TAG, "len: " + dirty.length() + " bad: " + badChars + " - " + dirty);
            return dirty.subSequence(0, dirty.length() - badChars);
        } else
            return dirty;

    }

    /**
     * Process incoming text and remove backspaces and the characters they are meant to delete, plus
     * count how many additional backspaces need to be used to modify other text
     *
     * @param text the dirty string that may contain backspaces, which we need to remove
     * @return a CleanString, which is a small struct containing the actual clean string to print to the screen,
     * and a count of how many additional backspaces were encountered that need to be used to delete chars
     * already printed
     */
    private CleanString countAndRemoveBackspaces(String text) {
        CleanString clean = new CleanString();
        int i;
        int initialBS = 0;
        for (i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\u0008')
                break;
            initialBS++;
        }
        int additionalBS = 0;
        for (; i < text.length(); i++) {
            if (text.charAt(i) == '\u0008') {
                if (clean.str.length() > 0)
                    clean.str = clean.str.substring(0, clean.str.length() - 1);
                else
                    additionalBS++;
            } else {
                clean.str = clean.str + text.charAt(i);
            }
        }
        if (additionalBS > 0)
            clean.backspaces = initialBS + additionalBS;
        else
            clean.backspaces = initialBS;
        //if (BuildConfig.DEBUG) Log.d(TAG, "bs: " + clean.backspaces);
        //if (BuildConfig.DEBUG) Log.d(TAG, "adding: " + clean.str);
        return clean;
    }

    @Override
    public void controlMessageReceived(String message) {
        addControlText(message);
    }

    @Override
    public void RTTextReceived(String text) {
        addText(text);
    }

    @Override
    public void SessionEstablished(String userName) {
        addControlText("Connected!");
        setCallerText(userName + " says:");
    }

    private void addControlText(final String text) {
        final TextView view = (TextView) findViewById(R.id.control_messages);
        UiRunner lambda = (v, t, e) -> v.append(t);
        runOnUiThread(view, lambda, '\n' + text, null);
    }

    private void setCallerText(final String text) {
        final TextView view = (TextView) findViewById(R.id.other_party_label);
        UiRunner lambda = (v, t, e) -> v.setText(t);
        runOnUiThread(view, lambda, text, null);
    }


    public void hangUp(View view) {
        texter.hangUp();
        showEndCallDialog("You hung up. Do you want to save the text of this call?");
        //finish();
    }

    @Override
    public void SessionClosed() {
        showEndCallDialog(otherParty + " hung up. Do you want to save the text of this call?");
    }

    @Override
    public void SessionFailed(String reason) {
        showFailDialog("Failed to establish call: " + reason);
    }

    @Override
    public void SessionDisconnected(String reason) {
        showEndCallDialog("Call disconnected: " + reason + ". \n\nDo you want to saved the text of this call?");
    }

    /**
     * Only used in the case where the user has chosen to send text en bloc rather than
     * character-by-character (for example, if they don't trust AutoCorrect). This is determined
     * at activity creation time by the preference com.laserscorpion.rttapp.pref_use_realtime
     * When the user presses the "Send" button, this method is called, and the text they have
     * entered is removed from the text entry field and sent by the TextEntryWatcher, due to the
     * call here. Basically, the purpose of this method is to pass the button signal along to the
     * TextEntryWatcher. It can only be called if the Send button is visible, which occurs when the
     * preference is set.
     * @param view
     */
    public void sendText(View view) {
        textHandler.checkAndSend();
    }

    public void saveText(View view) {

    }

    private void showFailDialog(String message) {
        FailDialog dialog = FailDialog.newInstance(message);
        dialog.show(getFragmentManager(), "error");
    }

    private void showEndCallDialog(String message) {
        CallEndDialog dialog = CallEndDialog.newInstance(message);
        dialog.show(getFragmentManager(), "call end");
    }


    @Override
    public synchronized void dialogFail() {
        finish();
    }

    @Override
    public void dialogDismissed() {
        // called from the end-of-call save-text dialog when choosing "No" to saving text
        finish();
    }

    @Override
    public synchronized void dialogSaveText() {
        saveText(null);
        finish();
    }

}
