package com.laserscorpion.rttapp;

import java.util.EventListener;

/**
 * Created by joel on 2/4/16.
 */
public interface SessionListener extends EventListener {

    void SessionEstablished();
    void SessionClosed();
    void SessionFailed(String reason);

}
