package com.laserscorpion.rttapp;

import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.message.Message;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.os.AsyncTask;
import android.util.Log;

/**
 * fires a one-off response in another thread and dies
 */
public class SipResponder extends AsyncTask<Message, String, String> {
    private static final String TAG = "BACKGROUND";
    private SipProvider sipProvider;

    public SipResponder(SipProvider provider) {
        sipProvider = provider;
    }

    /**
     *
     * @param messages must be exactly 2 Messages, a Request and a Response, in that order
     * @return
     */
    @Override
    protected String doInBackground(Message... messages) {
        Request request = (Request)messages[0];
        Response response = (Response)messages[1];
        try {
            ServerTransaction transaction = sipProvider.getNewServerTransaction(request);
            transaction.sendResponse(response);
            return "Success";
        } catch (Exception e) {
            Log.e(TAG, "the response failed. UGH");
            e.printStackTrace();
            return null;
        }
    }
}
