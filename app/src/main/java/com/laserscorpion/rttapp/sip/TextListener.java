package com.laserscorpion.rttapp.sip;

import java.util.EventListener;

/**
 * TextListeners are notified with text messages when two kinds of events occur:
 * 1. Real-time text is received
 * 2. Some kind of status information is available regarding registering, sending INVITE, etc
 * The first case is important, since this app is about sending/receiving real-time text.
 * The second case is not very well specified. This can probably be reworked to either become
 * more focused or be removed.
 */
public interface TextListener extends EventListener {

    void controlMessageReceived(String message);

    /**
     * Called when real-time text has been received from the RTP session
     * @param text the incoming characters
     */
    void RTTextReceived(String text);

}
