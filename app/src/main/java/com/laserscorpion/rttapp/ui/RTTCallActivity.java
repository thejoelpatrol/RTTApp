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
import com.laserscorpion.rttapp.db.ConversationHelper;
import com.laserscorpion.rttapp.sip.SessionListener;
import com.laserscorpion.rttapp.sip.SipClient;
import com.laserscorpion.rttapp.sip.TextListener;

import java.util.Date;

/**
 * <p>The screen for a call in progress.</p>
 *
 * <p>There are two call modes, selectable from Settings: real-time and en-bloc modes. In real-time
 * mode, chars are sent as they are entered. There is no notion of messages and no need for a "Send"
 * button. In en-bloc mode, chars are only sent in bunches when Send is pressed. The Send button is
 * only visible in en-bloc mode, and touching it calls sendText(), which is not used otherwise. In
 * both modes, outgoing text sending is handled by a helper, TextEntryMonitor. That class watches
 * the EditText in this activity for changes, and determines which characters to send via the
 * SipClient.</p>
 *
 * <p>This screen contains three TextViews, one of which is an EditText where the user enters their
 * own text. The largest TextView contains the incoming text, which is added in
 * addText(), called from RTTextReceived(). Adding text to the display fields is kind of a hassle
 * due to the need to run it on the UI thread. Android is rude enough to make us worry about this.</p>
 *
 * <p>A third, smaller TextView (R.id.control_messages) shows the call progress (Dialing, ringing,
 * connected, etc). There is probably a slicker way to display that information, like some icon that
 * changes state, or even a label whose entire text changes, rather than adding new lines to this
 * TextView.</p>
 *
 * <p>Incoming text must be scrubbed of 0xFFEF Unicode chars to maintain an accurate view of how many
 * characters are in the incoming text field. When editing CharSequences, for example when getting a
 * subsequence that chops off the end, invisible 0xFFEF characters may remain, which count as
 * characters but to a human are not really characters. We need to remove these if they ever occur,
 * since their presence in the received text will be due to changes made here, and they will not
 * likely be sent in the actual incoming text. If they are, I suppose there could be a bug here, but
 * it probably won't have any effect. We don't want them in the other party's displayed text field
 * because when a backspace char comes in, we need to delete one char, and it should be a real
 * visible one, not a fake invisible one.</p>
 *
 * <p>Text of the call may be saved at any time via the "Save Text" button, or at the end of the call
 * from an option in the dialog that pops up.</p>
 *
 * <p>The activity is discarded when a dialog callback indicates to the activity that the end-call
 * dialog has been dismissed. Various kinds of dialogs may be displayed depending on what caused
 * them (error, hanging up, etc), but all of them call a method of the activity to let it know that
 * the dialog is done and the activity may be destroyed.</p>
 */
public class RTTCallActivity extends AppCompatActivity implements TextListener,
                                                                    SessionListener,
                                                                    FailDialog.FailDialogListener,
                                                                    AbstractDialog.DialogListener,
                                                                    CallEndDialog.SaveDialogListener {

    /**
     * A struct that is used to store a String that contains no '\b' (0x) chars, and a count of how
     * many extra '\b' were encountered
     */
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
    private String myTextHistory = ""; // mainly used in the case of en bloc mode, since text is removed after typing
    private TextEntryMonitor textHandler; // this watches text input and sends the RTT chars
    private Date callStartTime;
    private boolean useRealTime;

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
        useRealTime = pref.getBoolean(getString(R.string.pref_use_realtime_qualified), true);;
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

        callStartTime = new Date();
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
     * Whoever programmed CharSequence thinks that \uFEFF is an acceptable thing to leave in a string,
     * especially at the end. Unicode has deprecated the usage of this character as a non-breaking
     * space. These Google (Apache?) devs don't care and use this invisible character to save having to edit
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
     * count how many additional backspaces need to be used to modify other text.
     *
     * That is, if the input contains more real chars than backspaces, the output contains text and
     * a backspace count of 0. If the input contains more backspaces than chars, the output contains
     * no text and a count of additional backspaces to delete from other text.
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

    /**
     * TextListener interface callback
     */
    @Override
    public void controlMessageReceived(String message) {
        addControlText(message);
    }

    /**
     * TextListener interface callback
     * @param text the incoming characters
     */
    @Override
    public void RTTextReceived(String text) {
        addText(text);
    }

    /**
     * SessionListener interface callback
     * @param userName the SIP URI of the other party
     */
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


    /**
     * Called by pressing the red Hang Up button
     * @param view the button
     */
    public void hangUp(View view) {
        texter.hangUp();
        showEndCallDialog("You hung up. Do you want to save the text of this call?");
        //finish();
    }

    /**
     * SessionListener interface callback
     */
     @Override
    public void SessionClosed() {
        showEndCallDialog(otherParty + " hung up. Do you want to save the text of this call?");
    }

    /**
     * SessionListener interface callback
     */
    @Override
    public void SessionFailed(String reason) {
        showFailDialog("Failed to establish call: " + reason);
    }

    /**
     * SessionListener interface callback
     */
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
     *
     * addToTextHistory() is used to keep a record of the messages the user sends, since the text is
     * removed from the EditText. It can then be saved to the DB later, if necessary.
     * @param view Send button
     */
    public void sendText(View view) {
        addToTextHistory();
        textHandler.checkAndSend();
    }

    private void addToTextHistory() {
        EditText textField = (EditText)findViewById(R.id.compose_message);
        String currentText = textField.getText().toString();
        myTextHistory = myTextHistory + '\n' + currentText;
    }

    private void setTextHistory() {
        EditText textField = (EditText)findViewById(R.id.compose_message);
        CharSequence seq = textField.getText();
        String currentText = seq.toString();
        myTextHistory = currentText;
    }

    /**
     * Called by the Save Text button, or at the end of a call. Both parties' text and the call time
     * is saved in a SQLiteDatabase, though unfortunately there is no UI yet for retrieving call
     * text.
     * @param view the Save Text button (or null, it's not used)
     */
    public void saveText(View view) {
        if (useRealTime)
            setTextHistory();
        TextView incoming = (TextView)findViewById(R.id.textview);
        String incomingText = incoming.getText().toString();
        ConversationHelper db = new ConversationHelper(this);
        // this should be made asynchronous
        db.save(otherParty, myTextHistory, incomingText, callStartTime, new Date());
    }

    private void showFailDialog(String message) {
        FailDialog dialog = FailDialog.newInstance(message);
        dialog.show(getFragmentManager(), "error");
    }

    private void showEndCallDialog(String message) {
        CallEndDialog dialog = CallEndDialog.newInstance(message);
        dialog.show(getFragmentManager(), "call end");
    }


    /**
     * FailDialogListener callback. This is called when the call fails and the resulting dialog is
     * done saying so.
     */
    @Override
    public synchronized void dialogFail() {
        finish();
    }

    /**
     * DialogListener callback.
     * This one happens to be called from the end-of-call save-text dialog when choosing "No" to
     * saving text.
     */
    @Override
    public void dialogDismissed() {
        finish();
    }

    /**
     * SaveDialogListener callback, which is called when the end-of-call dialog Save Text option is
     * chosen.
     */
    @Override
    public synchronized void dialogSaveText() {
        saveText(null);
        finish();
    }

}
