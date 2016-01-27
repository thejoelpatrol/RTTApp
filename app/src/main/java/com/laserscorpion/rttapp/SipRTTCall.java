package com.laserscorpion.rttapp;

import android.content.Context;
import android.net.sip.SipAudioCall;
import android.net.sip.SipProfile;
import android.util.Log;

/**
 * Created by joel on 12/5/15.
 */
public class SipRTTCall extends android.net.sip.SipAudioCall {

    public SipRTTCall(Context context, SipProfile localprofile) {
        super(context, localprofile);
    }

    @Override
    public void answerCall(int timeout) {
        Log.d("SipRTTCall", "Answering call!");
    }



}
