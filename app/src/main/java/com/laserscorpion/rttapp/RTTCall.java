package com.laserscorpion.rttapp;

import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
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
    private ServerTransaction inviteTransaction;

    private Semaphore creationLock;
    private Semaphore destructionLock;
    private boolean ringing;
    private boolean connected;
    private boolean calling;

    private int localPort;
    private int remotePort;
    private String remoteIP;
    /**
     * Use this constructor for an incoming call - the requestEvent is the INVITE,
     * the transaction is the ServerTransaction used to respond to the INVITE,
     * for both 180 Ringing and the final response. If planning to send 180 Ringing,
     * it must be sent already, so the ServerTransaction can be used here.
     * @param requestEvent the incoming INVITE event
     * @param transaction used to send 180 Ringing, and the final response. null if no 180 has been
     *                    sent yet and therefore no transaction is used yet. In that case, only
     *                    one response can be sent,
     */
    public RTTCall(RequestEvent requestEvent, ServerTransaction transaction) {
        this(requestEvent.getRequest(), requestEvent.getDialog());
        incomingRequest = requestEvent;
        inviteTransaction = transaction;
    }

    /**
     * Use this constructor for an outgoing call. Ideally you will pass in the dialog that is
     * created for the call, but this may not be available yet,
     * so you will need to call addDialog() in that case.
     * @param creationRequest the INVITE Request sent to the other party to initiate the call
     * @param dialog
     */
    public RTTCall(Request creationRequest, Dialog dialog) {
        this.creationRequest = creationRequest;
        this.dialog = dialog;
        sipClient = SipClient.getInstance();
        destructionLock = new Semaphore(1);
    }

    /**
     *
     * @return the transaction used to respond to the original INVITE, or null if none
     */
    public ServerTransaction getInviteTransaction() {
        return inviteTransaction;
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
        Request newRequest = otherCall.getCreationRequest();
        return newRequest.equals(creationRequest);
    }

    public void setCalling() {
        calling = true;
    }
    public void setRinging() {
        ringing = true;
    }

    /**
     *
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @throws IllegalStateException if no call is currently ringing
     */
    public void accept(String remoteIP, int remotePort, int localRTPPort) throws IllegalStateException {
        if (!ringing)
            throw new IllegalStateException("call is not ringing - cannot accept");
        connectCall(remoteIP, remotePort, localRTPPort);
        /*connected = true;
        ringing = false;
        setUpStream();*/
    }
    /**
     *
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @throws IllegalStateException if no call is currently outgoing
     */
    public void callAccepted(String remoteIP, int remotePort, int localRTPPort) {
        if (!calling)
            throw new IllegalStateException("not calling anyone - what was accepted?");
        connectCall(remoteIP, remotePort, localRTPPort);
     /*connected = true;
        calling = false;
        setUpStream();*/
    }

    private void connectCall(String remoteIP, int remotePort, int localRTPPort) {
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.localPort = localRTPPort;
        connected = true;
        ringing = false;
        calling = false;
    }

    /**
     * End a call at any stage. Invoking multiple times has no effect; the
     * first invocation ends the session.
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
