package com.laserscorpion.rttapp.sip;

public interface CallReceiver {
    /**
     * Called when a new call is coming in. The receiver must then accept or decline the call
     * via the SipClient.
     */
    void callReceived(String from);
}
