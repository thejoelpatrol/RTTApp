package com.laserscorpion.rttapp.sip;

import android.javax.sip.ClientTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.message.Request;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Used to fire a one-off request in a new thread then die
 */
public class SipTransactionRequester extends AsyncTask<ClientTransaction, String, String> {
    private static final String TAG = "BACKGROUND";
    private SipProvider sipProvider;

    public SipTransactionRequester(SipProvider provider) {
        sipProvider = provider;
    }

    @Override
    protected String doInBackground(ClientTransaction... transactions) {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        try {
            ClientTransaction transaction = transactions[0];
            transaction.sendRequest();
            return "Success";
        } catch (Exception e) {
            Log.e(TAG, "the request still failed. UGH");
            e.printStackTrace();
            return null;
        }
    }
}
