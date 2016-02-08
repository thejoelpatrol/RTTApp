package com.laserscorpion.rttapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import android.net.sip.SipProfile;
import android.util.Log;

/**
 * Created by joel on 12/5/15.
 */
public class RTTIncomingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "RTTIncomingCallReceiver";

    /**
     * Processes the incoming call, answers it, and hands it over to the
     * RTTRegistrationActivity. This class mostly copied from
     * https://developer.android.com/guide/topics/connectivity/sip.html#intent_filter
     * @param context the RTTRegistrationActivity parent.
     * @param intent The intent being received.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        SipRTTCall incomingCall = null;
        try {
            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                /**
                 *
                 * @param call this should actually be a SipRTTCall!
                 * @param caller the peer on the other end of the call
                 */
                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    Log.d(TAG, "it's ringing...");
                    try {
                        if (!(call instanceof SipRTTCall))
                            throw new Exception("A regular audio call got sent in here somehow...");
                        call.answerCall(30);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            RTTRegistrationActivity callActivity = (RTTRegistrationActivity)context;
            incomingCall = callActivity.sipManager.takeRTTCall(intent, listener);
            incomingCall.answerCall(30);
            incomingCall.startAudio();
            incomingCall.setSpeakerMode(true);
            if(incomingCall.isMuted()) {
                incomingCall.toggleMute();
            }
            callActivity.call = incomingCall;


        } catch (Exception e) {
            //Log.e(TAG, "a bad thing happened...");
            e.printStackTrace();
            if (incomingCall != null) {
                incomingCall.close();
            }
        }

    }
}
