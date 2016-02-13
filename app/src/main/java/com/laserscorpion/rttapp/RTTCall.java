package com.laserscorpion.rttapp;

import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.message.Request;

/**
 * Created by joel on 2/12/16.
 */
public class RTTCall {
    private SipClient sipClient;
    private Dialog dialog;
    public RequestEvent incomingRequest;

    public RTTCall(Dialog dialog) {
        this.dialog = dialog;
        sipClient = SipClient.getInstance();
    }

    public RTTCall(RequestEvent requestEvent) {
        this.dialog = requestEvent.getDialog();
        sipClient = SipClient.getInstance();
        incomingRequest = requestEvent;
    }

    public boolean equals(RTTCall otherCall) {
        Request existingRequest = incomingRequest.getRequest();
        Request newRequest = incomingRequest.getRequest();
        if (newRequest.equals(existingRequest))
            return true;
        return false;
    }

    public void accept() {

    }
    public void decline() {

    }
    public void end() {

    }
}
