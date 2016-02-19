package com.laserscorpion.rttapp;

import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.message.Request;

import java.util.concurrent.Semaphore;

/**
 * Created by joel on 2/12/16.
 */
public class RTTCall {
    private SipClient sipClient;
    private Dialog dialog;
    private Request creationRequest;

    private RequestEvent incomingRequest;

    private Semaphore creationLock;
    private Semaphore destructionLock;
    private boolean ringing;
    private boolean connected;
    private boolean calling;

    /*public RTTCall(Dialog dialog) {
        this.dialog = dialog;
        sipClient = SipClient.getInstance();
    }*/

    public RTTCall(RequestEvent requestEvent) {
        //this.dialog = requestEvent.getDialog();
        //sipClient = SipClient.getInstance();
        this(requestEvent.getRequest(), requestEvent.getDialog());
        incomingRequest = requestEvent;
    }

    public RTTCall(Request creationRequest, Dialog dialog) {
        this.creationRequest = creationRequest;
        this.dialog = dialog;
        sipClient = SipClient.getInstance();
        destructionLock = new Semaphore(1);
    }

    /**
     * Used to change the Dialog associated with the call, especially if one was not
     * available at creation of the RTTCall
     * @param dialog the new Dialog to associate with the call
     */
    public void addDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    /**
     * Precondition: call was created by an incoming RequestEvent. If call
     * was created by an outgoing request, will return null.
     * @return the incoming event that created the call, or null
     */
    public RequestEvent getCreationEvent() {
        return incomingRequest;
    }
    public Request getCreationRequest() {
        return creationRequest;
    }
    public Dialog getDialog() {
        return dialog;
    }

    public boolean equals(RTTCall otherCall) {
        //Request existingRequest = incomingRequest.getRequest();
        Request newRequest = otherCall.getCreationRequest();
        return newRequest.equals(creationRequest);
        /*if (newRequest.equals(creationRequest))
            return true;
        return false;*/
    }

    public void setCalling() {
        calling = true;
    }
    public void setRinging() {
        ringing = true;
    }

    public void accept() throws IllegalStateException {
        if (!ringing)
            throw new IllegalStateException("call is not ringing - cannot accept");
        connected = true;
        ringing = false;
        setUpStream();
    }
    public void callAccepted() {
        if (!calling)
            throw new IllegalStateException("not calling anyone - what was accepted?");
        connected = true;
        calling = false;
        setUpStream();
    }

    /**
     * End a call at any stage. Calling multiple times has no effect; the
     * first call ends the session.
     */
    public void end() {
        if (destructionLock.tryAcquire()) {
            ringing = false;
            connected = false;
            calling = false;
            // TODO tear down RTP session
        } else
            return;
        /*  We do not release destructionLock.
            Once it is acquired by calling this method,
            future calls should do nothing.
         */
    }
    public boolean isRinging() {
        return ringing;
    }
    public boolean isConnected() {
        return connected;
    }
    public boolean isCalling() {
        return calling;
    }
    private void setUpStream() {
        //TODO implement this
    }
}
