/* © 2016 Joel Cretan
 *
 * This is part of RTTAPP, an Android RFC 4103 real-time text app
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.laserscorpion.rttapp.sip;

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
