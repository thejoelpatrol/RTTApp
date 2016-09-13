package com.laserscorpion.rttapp.sip;

/**
 * A CallReceiver listens for an incoming call, and then <em>must</em> act on the call. It must either
 * accept or decline the call via the global SipClient.
 */
public interface CallReceiver {
    /**
     * Called when a new call is coming in. The receiver <em>must</em> then accept or decline the call
     * via the SipClient.
     */
    void callReceived(String from);
}
