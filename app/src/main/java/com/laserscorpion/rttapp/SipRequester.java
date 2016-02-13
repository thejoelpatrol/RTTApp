package com.laserscorpion.rttapp;

import android.javax.sip.ClientTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.message.Request;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Used to fire a one-off request in a new thread then die
 */
public class SipRequester extends AsyncTask<Request, String, String> {
    private static final String TAG = "BACKGROUND";
    private SipProvider sipProvider;

    public SipRequester(SipProvider provider) {
        sipProvider = provider;
    }

    @Override
    protected String doInBackground(Request... requests) {
        try {
            ClientTransaction transaction = sipProvider.getNewClientTransaction(requests[0]);
            transaction.sendRequest();
            return "Success";
        } catch (SipException e) {
            Log.e(TAG, "the request still failed. UGH");
            e.printStackTrace();
            return null;
        }
    }
}
