package com.laserscorpion.rttapp;

import android.gov.nist.javax.sip.SipStackImpl;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.gov.nist.javax.sip.stack.SIPServerTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.TransactionAlreadyExistsException;
import android.javax.sip.message.Message;
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
        Request request = requestEvent.getRequest();
        Response response = (Response)responses[0];

        try {
            /*
            // use of methods from the Impl classes make this not compliant with the overall JAIN API
            // these are restricted to the NIST implementation ... but this is necessary ... this should be in the API
            SipStackImpl stack = (SipStackImpl)sipProvider.getSipStack();
            ServerTransaction transaction = (SIPServerTransaction)stack.findTransaction((SIPMessage)response, true);*/

            //ServerTransaction transaction = sipProvider.getNewServerTransaction(request);
            if (transaction == null)
                transaction = requestEvent.getServerTransaction();
            if (transaction == null) {
                //Log.d(TAG, "server transaction: " + transaction);
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
