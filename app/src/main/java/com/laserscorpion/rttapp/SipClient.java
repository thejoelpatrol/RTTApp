package com.laserscorpion.rttapp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
//import android.net.sip.SipManager;
//import android.net.sip.SipProfile;
//import android.net.sip.SipRegistrationListener;
import android.gov.nist.javax.sip.header.Contact;
import android.javax.sip.address.*;
import android.javax.sip.header.*;
import android.javax.sip.message.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.util.Log;
import android.javax.sip.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Created by joel on 12/4/15.
 */
public class SipClient {
    private android.content.Context parent;
    private static final String TAG = "SipClient";
    private static final int DEFAULT_PORT = 5060;
    private static final String DEFAULT_PROTOCOL = "udp";
    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private ListeningPoint listeningPoint;
    private Properties properties;
    private String username;
    private String server;
    private String password;
    private String localIP;
    private int port = DEFAULT_PORT;
    private String protocol = DEFAULT_PROTOCOL;
    private Address localSipAddress;
    private ContactHeader localContactHeader;
    private DatagramSocket socket;

    public SipClient(Context parent, String username, String server, String password) throws java.text.ParseException {
        this.parent = parent;
        this.username = username;
        this.server = server;
        this.password = password;
        //createSipProfile();
        finishInit();
    }

    private void finishInit() {
        try {
            findLocalIP();
        } catch (SocketException e) {
            // TODO: handle this case, throw up an error
            e.printStackTrace();
        }
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "stack");
        properties.setProperty("javax.sip.IP_ADDRESS", localIP);
        try {
            sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
        } catch (PeerUnavailableException e) {
            // what the hell is this case? my library is right there in a jar. why would it be anywhere unfindable?
            e.printStackTrace();
        }
        try {
            listeningPoint = sipStack.createListeningPoint(localIP, port, protocol);
        } catch (TransportNotSupportedException e) {
            // look, this won't happen. what kind of SIP stack would not support UDP?
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO: this occurs when the port is invalid. should i just try all of them until i find one? that sucks.
            e.printStackTrace();
        }

    }

    private boolean hasInternetConnection() {
        ConnectivityManager connMgr = (ConnectivityManager) parent.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void findLocalIP() throws SocketException {
        if (!hasInternetConnection())
            throw new SocketException("no internet connection");
        Enumeration<NetworkInterface> Interfaces = NetworkInterface.getNetworkInterfaces();
        localIP = socket.getLocalAddress().getHostAddress();
    }

    /**
     * This method basically copied from
     * https://developer.android.com/guide/topics/connectivity/sip.html#profiles
     */
    public void register() throws android.net.sip.SipException {

    }

    public void unregister() {

    }

    public void call(String username) {
        Log.d(TAG, "calling " + username);
    }


}
