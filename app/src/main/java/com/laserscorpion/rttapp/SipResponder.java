package com.laserscorpion.rttapp;

import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
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
public class SipResponder extends AsyncTask<Response, String, String> {
    private static final String TAG = "BACKGROUND";
    private SipProvider sipProvider;
    private RequestEvent requestEvent;

    public SipResponder(SipProvider provider, RequestEvent event) {
        sipProvider = provider;
        requestEvent = event;
    }

    /**
     *
     * @param messages must be exactly 2 Messages, a Request and a Response, in that order
     * @return
     */
    @Override
    protected String doInBackground(Response... responses) {
        Request request = requestEvent.getRequest();
        Response response = (Response)responses[0];
        try {
            ServerTransaction transaction = requestEvent.getServerTransaction();
            if (transaction == null) {
                // why is this being called when a server transaction already exists?
                transaction = sipProvider.getNewServerTransaction(request);
            }
            transaction.sendResponse(response);
            return "Success";
        } catch (Exception e) {
            Log.e(TAG, "the response failed. UGH");
            e.printStackTrace();
            return null;
        }
    }
}
