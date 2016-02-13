package com.laserscorpion.rttapp;

/**
 * Created by joel on 2/12/16.
 */
public interface CallReceiver {
    /**
     * Called when a new call is coming in. The receiver should accept or decline the call.
     */
    void callReceived(/*RTTCall incomingCall*/);
}
