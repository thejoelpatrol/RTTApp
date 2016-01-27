package com.laserscorpion.rttapp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created by joel on 12/4/15.
 */
public class RTTClient {
    private android.content.Context parent;
    private static final String TAG = "RTTClient";
    private SipRTTManager sipManager;
    private SipProfile mySipProfile;
    //private SipProfile peerSipProfile;
    private String username;
    private String server;
    private String password;

    public RTTClient(Context parent, SipRTTManager manager, String username, String server, String password) throws java.text.ParseException {
        this.parent = parent;
        this.username = username;
        this.server = server;
        this.password = password;
        createMySipProfile();
        sipManager = manager;
    }

    /**
     * This method basically copied from
     * https://developer.android.com/guide/topics/connectivity/sip.html#profiles
     */
    public void register() throws android.net.sip.SipException {
        Log.d(TAG, "calling register...once...?");
        Intent intent = new Intent();
        intent.setAction("com.laserscorpion.rttapp.INCOMING_CALL");
        SystemClock.sleep(1000);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(parent, 0, intent, Intent.FILL_IN_DATA);
        sipManager.open(mySipProfile, pendingIntent, null);
        sipManager.setRegistrationListener(mySipProfile.getUriString(), new SipRegistrationListener() {
            @Override
            public void onRegistering(String localProfileUri) {
                Log.d(TAG, "Trying to register " + mySipProfile.getUserName() + " with " + server);
            }

            @Override
            public void onRegistrationDone(String localProfileUri, long expiryTime) {
                Log.d(TAG, "Registered " + mySipProfile.getUserName() + " with " + server + '!');
            }

            @Override
            public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
                //throw new android.net.sip.SipException("Unable to register " + localProfileUri +
                //        " - SIP response " + errorCode + " - " + errorMessage);
                Log.e(TAG, "Failed to register " + mySipProfile.getUserName() + "@" + server
                        + " - SIP error " + errorCode + " - " + errorMessage);
                // unregistering here seems to be bad
                // it appears that the multiple registrations that are occurring are OK
                // and at least one of them will succeed if i use SystemClock.sleep()
                /*try {
                    sipManager.close(mySipProfile.getUriString());
                } catch (android.net.sip.SipException e) {
                    // now what?
                    Log.e(TAG, "uhhh...?");
                }*/
            }
        });
    }

    public void unregister() {
        String uriString = mySipProfile.getUriString();
        try {
            if (sipManager.isRegistered(uriString)) {
                sipManager.close(uriString);
            }
        } catch (android.net.sip.SipException e) {
            // this is a braindead place to throw an exception
            // wtf am I supposed to do if isRegistered() fails?
            // I can't very well quit if it's still registered
            // but htf do I know if it is registered or not at this point?
            // wait, both of those methods can throw the exception
            // what on earth could I possibly do in this situation?
            Log.e(TAG, "Android sucks. I can't unregister, or even tell if we're still registered. Sorry.");
        }
    }

    public void call(String username) {
        Log.d(TAG, "calling " + username);
    }

    private void createMySipProfile() throws java.text.ParseException {
        SipProfile.Builder builder = new SipProfile.Builder(username, server);
        builder.setPassword(password);
        //builder.setAutoRegistration(false);
        mySipProfile = builder.build();
    }


}
