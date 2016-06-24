package com.laserscorpion.rttapp;

import android.util.Log;

import java.util.TimerTask;

public class RegistrationTimerTask extends TimerTask {
    SipClient registrationListener;

    public RegistrationTimerTask(SipClient sipClient) {
        registrationListener = sipClient;
    }

    @Override
    public void run() {
        registrationListener.registrationExpired();
    }
}
