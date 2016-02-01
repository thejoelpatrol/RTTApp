package com.laserscorpion.rttapp;

import android.content.Context;
import android.javax.sip.SipException;
import android.javax.sip.address.*;
import android.javax.sip.header.*;
import android.javax.sip.message.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.sip.*;
import android.os.AsyncTask;
import android.util.Log;
import android.javax.sip.*;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;


/**
 * This class based on http://alex.bikfalvi.com/teaching/upf/2013/architecture_and_signaling/lab/sip/
 */
public class SipClient implements SipListener{
    private static final String TAG = "SipClient";
    private static final int MAX_FWDS = 70;
    private static final int DEFAULT_REGISTRATION_LEN = 600;
    private android.content.Context parent;
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
    private int port = ListeningPoint.PORT_5060;
    private String protocol = ListeningPoint.UDP.toLowerCase();;
    private Address localSipAddress;
    private Address serverSipAddress;
    private ContactHeader localContactHeader;
    private SipRequester messageSender;

    public String message;

    public SipClient(Context parent, String username, String server, String password)
            throws java.text.ParseException, java.net.SocketException {
        this.parent = parent;
        this.username = username;
        this.server = server;
        this.password = password;
        finishInit();
    }

    private void finishInit() throws java.net.SocketException {
        try {
            findLocalIP();
        } catch (SocketException e) {
            // TODO: handle this case, throw up an error
            e.printStackTrace();
        }
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("android.gov.nist");
        properties = new Properties();
        properties.setProperty("android.javax.sip.STACK_NAME", "stack");
        properties.setProperty("android.javax.sip.IP_ADDRESS", localIP);
        try {
            Class[] e = new Class[]{Class.forName("java.util.Properties")};
            Constructor errmsg1 = Class.forName("android.gov.nist" + ".javax.sip.SipStackImpl").getConstructor(e);
            Object[] conArgs = new Object[]{properties};
            sipStack = (SipStack)errmsg1.newInstance(conArgs);

            //sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            listeningPoint = sipStack.createListeningPoint(localIP, port, protocol); // a ListeningPoint is a socket wrapper
                                                                        // TODO allow different ports if this one is not available
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
            messageSender = new SipRequester(sipProvider);
            localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            serverSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
        } catch (Exception e) {
            // TODO: print some error somehow
            // there are a ton of exceptions that could occur here, this doesn't seem smart
            // i just don't even understand why the SIP stack wouldn't support UDP or why it couldn't be found in the first place
            // maybe more realistically it wouldn't support _TCP_, ok fine
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
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        // chooses the first non-localhost IPv4 addr ... is this OK?
        while (interfaces.hasMoreElements()) {
            NetworkInterface intfc = interfaces.nextElement();
            Enumeration<InetAddress> addresses = intfc.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (isRealIPv4Address(address)) {
                    /* my belief is that this should be guaranteed to happen once we are sure there
                       is an internet connection and we have a valid list of interfaces
                      */
                    localIP = address.getHostAddress();
                    return;
                }
            }
        }
    }

    /*
        This method inspired by https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device
     */
    private boolean isRealIPv4Address(InetAddress address) {
        String strAddress = address.getHostAddress();
        if (address.isLoopbackAddress())
            return false;
        if (address.isAnyLocalAddress())
            return false;
        if (strAddress.contains("dummy"))
            return false;
        if (strAddress.contains("%"))
            return false;
        if (strAddress.contains(":"))
            return false;
        return true;
    }

    public void register() throws android.net.sip.SipException {
        doRegister(DEFAULT_REGISTRATION_LEN);
    }

    private void doRegister(int registrationLength) throws android.net.sip.SipException {
        int tag = (new Random()).nextInt();
        URI requestURI = serverSipAddress.getURI();

        try {
            ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
            ViaHeader viaHeader = headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                    listeningPoint.getPort(), protocol, null);
            viaHeaders.add(viaHeader);
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(MAX_FWDS);
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "REGISTER");
            FromHeader fromHeader = headerFactory.createFromHeader(serverSipAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(serverSipAddress, null);

            Request request = messageFactory.createRequest(requestURI, "REGISTER", callIdHeader,
                    cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
            request.addHeader(localContactHeader);

            Log.d(TAG, "Sending stateless registration for " + serverSipAddress);
            message = "Here's the actual request: " + request;

            SipRequester requester = new SipRequester(sipProvider);
            requester.execute(request);
            String result = requester.get();
            Log.d(TAG, "Result: " + result);
            //new SipRequester(sipProvider).execute(request);
        } catch (Exception e) {
            // TODO handle the again-numerous error cases
            e.printStackTrace();
        }
    }

    public void unregister() throws android.net.sip.SipException {
        try {
            Log.d(TAG, "re-registering for time 0 ... does this unregister on the server?");
            doRegister(0);
            sipStack.deleteListeningPoint(listeningPoint);
            Log.d(TAG, "deleted listening point");
        } catch (ObjectInUseException e) {
            e.printStackTrace();
        }
    }

    public void call(String username) {
        Log.d(TAG, "calling " + username);
    }


    @Override
    public void processRequest(RequestEvent requestEvent) {

    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        Log.d(TAG, "received a response: " + response.toString());
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {

    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {

    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {

    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {

    }

    private class SipRequester extends AsyncTask<Request, String, String> {
        private SipProvider sipProvider;

        public SipRequester(SipProvider provider) {
            sipProvider = provider;
        }

        @Override
        protected String doInBackground(Request... requests) {
                try {
                    sipProvider.sendRequest(requests[0]);
                } catch (SipException e) {
                    Log.e("BACKGROUND","the request still failed. UGH");
                    e.printStackTrace();
                }
                return null;
        }
    }
}
