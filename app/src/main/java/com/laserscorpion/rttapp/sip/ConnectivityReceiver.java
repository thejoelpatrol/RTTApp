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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.laserscorpion.rttapp.BuildConfig;

/**
 * Register an IPChangeListener with a new ConnectivityReceiver to be notified when the IP
 * address may have changed.
 */
public class ConnectivityReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectivityReceiver";
    private IPChangeListener listener;

    /**
     * Create a new ConnectivityReceiver and register your IPChangeListener with it here to alert
     * that listener whenever the IP address may have changed.
     * @param listener the listener interested in knowing when the IP address changes
     */
    public ConnectivityReceiver(IPChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Received connectivity action");
            ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            NetworkInfo otherNetwork = intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
            if (networkInfo != null && networkInfo.isConnected() && otherNetwork == null)
                listener.IPAddrChanged();
        }
    }

    /**
     * An IPChangeListener that is registered with a ConnectivityReceiver is notified when the IP
     * address may have changed.
     */
    public interface IPChangeListener {
        void IPAddrChanged();
    }
}
