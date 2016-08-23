package com.laserscorpion.rttapp.sip;

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
