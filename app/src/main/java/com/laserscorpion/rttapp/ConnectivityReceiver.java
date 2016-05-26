package com.laserscorpion.rttapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;


public class ConnectivityReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectivityReceiver";
    private IPChangeListener listener;

    public ConnectivityReceiver(IPChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        //if (BuildConfig.DEBUG) Log.d(TAG, "Received some kind of broadcast");
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Received connectivity action");
            ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            NetworkInfo otherNetwork = intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
            if (networkInfo != null && networkInfo.isConnected() && otherNetwork == null)
                listener.IPAddrChanged();
        }
    }

    public interface IPChangeListener {
        void IPAddrChanged();
    }
}
