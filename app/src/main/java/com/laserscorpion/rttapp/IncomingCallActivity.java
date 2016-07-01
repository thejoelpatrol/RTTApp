package com.laserscorpion.rttapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import java.util.Timer;
import java.util.TimerTask;

public class IncomingCallActivity extends AppCompatActivity implements SessionListener {
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
        // TODO throw up dialog: other party hung up
        close();
    }

    @Override
    public void SessionFailed(String reason) {
        // TODO throw up dialog: reason
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
