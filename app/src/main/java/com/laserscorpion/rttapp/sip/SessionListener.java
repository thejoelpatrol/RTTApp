package com.laserscorpion.rttapp.sip;

import java.util.EventListener;

/**
 * SessionListeners are notified when call state changes.
 * This allows some non-SIP-layer class to listen for changes in the session state (e.g.
 * an upper layer that helps the user interact with the call).
 */
public interface SessionListener extends EventListener {

    void SessionEstablished(String userName);
    void SessionClosed();
    void SessionFailed(String reason);
    void SessionDisconnected(String reason);
}
