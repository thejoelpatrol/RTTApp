package com.laserscorpion.rttapp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.net.sip.SipSession;
//import copy.android.net.sip.ISipSession;
import android.util.Log;


/**
 * This is an ugly wrapper around SipManager.
 * SipManager is not intended to be subclassed. Its constructor is private.
 * But SipManager is audio chauvinistic. SIP is not only for audio, Google.
 * I just need a SipManager with a slight addition, so here we are.
 * That's what I get for trying to modify the Android API in my user code.
 * Maybe RFC 4103 should be included in Android.
 * That pull request might take more than just part of a semester.
 */
public class SipRTTManager {
    private SipManager sipManager;
    protected SipRTTCall call;

    public SipRTTManager(Context context) {
        sipManager = SipManager.newInstance(context);
    }

    /**
     * This is one method I needed
     */
    public SipRTTCall makeRTTCall(String localProfileUri, String peerProfileUri, SipAudioCall.Listener listener, int timeout) {
        // TODO implement this
        Log.d("SipRTTManager", "Making a call");
        return call;
    }

    /**
     * This is another
     */
    public SipRTTCall makeRTTCall(SipProfile localProfile, SipProfile peerProfile, SipAudioCall.Listener listener, int timeout) {
        // TODO implement this
        Log.d("SipRTTManager", "Making a call");
        return call;
    }

    /**
     * This is the last one
     */
    public SipRTTCall takeRTTCall(Intent incomingCallIntent,
                                                           android.net.sip.SipAudioCall.Listener listener) throws android.net.sip.SipException {
        if (incomingCallIntent == null) {
            throw new android.net.sip.SipException("Cannot retrieve session with null intent");
        }

        String callId = getCallId(incomingCallIntent);
        if (callId == null) {
            throw new android.net.sip.SipException("Call ID missing in incoming call intent");
        }

        String offerSd = getOfferSessionDescription(incomingCallIntent);
        if (offerSd == null) {
            throw new android.net.sip.SipException("Session description missing in incoming "
                    + "call intent");
        }
        return call;
        /*try {
            ISipSession session = mSipService.getPendingSession(callId);
            if (session == null) {
                throw new copy.android.net.sip.SipException("No pending session for the call");
            }
            copy.android.net.sip.SipAudioCall call = new copy.android.net.sip.SipAudioCall(
                    mContext, session.getLocalProfile());
            call.attachCall(new copy.android.net.sip.SipSession(session), offerSd);
            call.setListener(listener);
            return call;
        } catch (Throwable t) {
            throw new android.net.sip.SipException("takeAudioCall()", t);
        }*/
    }

    /**
     * Opens the profile for making calls and/or receiving generic SIP calls.
     */
    public void open(SipProfile localProfile, PendingIntent incomingCallPendingIntent, SipRegistrationListener listener) throws SipException {
        sipManager.open(localProfile, incomingCallPendingIntent, listener);
    }

    /**
     * Opens the profile for making generic SIP calls.
     */
    public void open(SipProfile localProfile) throws SipException {
        sipManager.open(localProfile);
    }

    /**
     * Manually registers the profile to the corresponding SIP provider for receiving calls.
     */
    public void register(SipProfile localProfile, int expiryTime, SipRegistrationListener listener) throws SipException {
        sipManager.register(localProfile, expiryTime, listener);
    }

    /**
     * Manually unregisters the profile from the corresponding SIP provider for stop receiving further calls.
     */
    public void unregister(SipProfile localProfile, SipRegistrationListener listener) throws SipException {
        sipManager.unregister(localProfile, listener);
    }

    public void close(String localProfileUri) throws SipException {
        sipManager.close(localProfileUri);
    }

    public void setRegistrationListener(String localProfileUri, SipRegistrationListener listener) throws SipException {
        sipManager.setRegistrationListener(localProfileUri, listener);
    }

    public SipSession createSipSession(SipProfile localProfile, SipSession.Listener listener) throws SipException {
        return sipManager.createSipSession(localProfile, listener);
    }

    public static String getCallId(Intent incomingCallIntent) {
        return SipManager.getCallId(incomingCallIntent);
    }

    public static String getOfferSessionDescription(Intent incomingCallIntent) {
        return SipManager.getOfferSessionDescription(incomingCallIntent);
    }

    public SipSession getSessionFor(Intent incomingCallIntent) throws SipException {
        return sipManager.getSessionFor(incomingCallIntent);
    }

    public static boolean isApiSupported(Context context) {
        return SipManager.isApiSupported(context);
    }

    public static boolean isIncomingCallIntent(Intent intent) {
        return SipManager.isIncomingCallIntent(intent);
    }

    public boolean isOpened(String localProfileUri) throws SipException {
        return sipManager.isOpened(localProfileUri);
    }

    public boolean isRegistered(String localProfileUri) throws SipException {
        return sipManager.isRegistered(localProfileUri);
    }

    public boolean isVoipSupported(Context context) {
        return sipManager.isVoipSupported(context);
    }

}
