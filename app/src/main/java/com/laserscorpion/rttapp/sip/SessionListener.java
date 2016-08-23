package com.laserscorpion.rttapp.sip;

import java.util.EventListener;

/**
 * Created by joel on 2/4/16.
 */
public interface SessionListener extends EventListener {

    void SessionEstablished(String userName);
    void SessionClosed();
    void SessionFailed(String reason);

}
