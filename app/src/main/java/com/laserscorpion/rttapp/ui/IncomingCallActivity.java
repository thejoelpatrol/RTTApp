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
import android.graphics.Color;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.laserscorpion.rttapp.R;
import com.laserscorpion.rttapp.sip.SessionListener;
import com.laserscorpion.rttapp.sip.SipClient;

import java.util.Timer;
import java.util.TimerTask;

public class IncomingCallActivity extends AppCompatActivity implements SessionListener, AbstractDialog.DialogListener {
    private static final long FLASH_TIME = 1000;
    private SipClient sipClient;
    private String from;
    private Timer flashTimer;
    private boolean white;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);
        sipClient = SipClient.getInstance();
        sipClient.addSessionListener(this);
        from = getIntent().getStringExtra("com.laserscorpion.rttapp.contact_uri");
        if (from.substring(0,4).equals("sip:"))
            from = from.substring(4);
        setTitle("Call from " + from);

        // https://stackoverflow.com/questions/22452326/wake-phone-on-incoming-call-in-android
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    protected void onStop() {
        close();
        super.onStop();
    }

    public void acceptCall(View view) {
        Intent intent = new Intent(this, RTTCallActivity.class);
        intent.putExtra("com.laserscorpion.rttapp.contact_uri", from);
        startActivity(intent);
        sipClient.acceptCall();
        close();
    }

    public void declineCall(View view) {
        sipClient.declineCall();
        close();
    }

    @Override
    public void onBackPressed() {
        sipClient.declineCall();
        close();
    }

    private void close() {
        sipClient.removeSessionListener(this);
        stopVibrating();
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        startVibrating();
        setFlashTimer();
    }

    private void startVibrating() {
        long pattern[] = {1000L, 1000L};
        Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        if (vibrator.hasVibrator())
            vibrator.vibrate(pattern, 0);
    }

    private void setFlashTimer() {
        flashTimer = new Timer();
        flashTimer.schedule(new ScreenFlashTimer(), FLASH_TIME);
    }

    private void stopVibrating() {
        Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        if (vibrator.hasVibrator())
            vibrator.cancel();
    }

    @Override
    public void SessionEstablished(String userName) {
        // this should not be called here
    }

    @Override
    public void SessionClosed() {
        stopVibrating();
        showFailDialog(from + " hung up.");
        // close() called from within dialog box handler
    }

    @Override
    public void SessionFailed(String reason) {
        stopVibrating();
        showFailDialog("Call failed: " + reason);
        // close() called from within dialog box handler
    }

    @Override
    public void SessionDisconnected(String reason) {
        stopVibrating();
        showFailDialog("Call failed: " + reason);
    }

    private void showFailDialog(String message) {
        DismissableDialog dialog = DismissableDialog.newInstance(message);
        dialog.show(getFragmentManager(), "error");
    }

    @Override
    public void dialogDismissed() {
        close();
    }


    private class ScreenFlashTimer extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View window = findViewById(R.id.incoming_call_window);
                    if (white)
                        window.setBackgroundColor(Color.CYAN);
                    else
                        window.setBackgroundColor(Color.WHITE);
                }
            });
            white = !white;
            setFlashTimer();
        }
    }
}
