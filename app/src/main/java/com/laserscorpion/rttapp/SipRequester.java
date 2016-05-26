package com.laserscorpion.rttapp;

import android.javax.sip.ClientTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.message.Request;
import android.os.AsyncTask;
import android.util.Log;

import java.net.UnknownHostException;

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
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        try {
            ClientTransaction transaction = sipProvider.getNewClientTransaction(requests[0]);
            transaction.sendRequest();
            return "Success";
        } catch (SipException e) {
            Log.e(TAG, "the request still failed. UGH");
            Log.e(TAG, "requests[0] = " + requests[0].toString());
            e.printStackTrace();
            if (e.getCause() != null) {
                return e.getCause().getMessage();
            }
            return null;
        }
    }
}
