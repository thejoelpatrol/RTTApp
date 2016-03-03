package com.laserscorpion.rttapp;

import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.TransactionAlreadyExistsException;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.os.AsyncTask;
import android.util.Log;

/**
 * fires a one-off response in another thread and dies
 */
public class SipResponder extends AsyncTask<Response, String, ServerTransaction> {
    private static final String TAG = "BACKGROUND";
    private SipProvider sipProvider;
    private RequestEvent requestEvent;
    private ServerTransaction transaction;

    public SipResponder(SipProvider provider, RequestEvent event, ServerTransaction transaction) {
        sipProvider = provider;
        requestEvent = event;
        this.transaction = transaction;
    }

    /**
     *
     * @param
     * @return
     */
    @Override
    protected ServerTransaction doInBackground(Response... responses) {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        Request request = requestEvent.getRequest();
        Response response = responses[0];

        try {
            if (transaction == null)
                transaction = requestEvent.getServerTransaction();
            if (transaction == null) {
                transaction = sipProvider.getNewServerTransaction(request);
            }

            transaction.sendResponse(response);
            return transaction;
        } catch (TransactionAlreadyExistsException e) {
            Log.e(TAG, "that race condition. UGH");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            Log.e(TAG, "the response failed. UGH");
            e.printStackTrace();
            return null;
        }
    }
}
