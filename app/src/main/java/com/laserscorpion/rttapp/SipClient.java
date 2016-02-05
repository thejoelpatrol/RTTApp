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

import org.w3c.dom.Text;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;


/**
 * Some of this class based on http://alex.bikfalvi.com/teaching/upf/2013/architecture_and_signaling/lab/sip/
 */
public class SipClient implements SipListener {
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
    //private ClientTransaction transaction;
    private String registrationID;
    private TextMessageListener messageReceiver;

    public SipClient(Context parent, String username, String server, String password, TextMessageListener listener)
            throws java.text.ParseException, java.net.SocketException {
        this.parent = parent;
        this.username = username;
        this.server = server;
        this.password = password;
        org.apache.log4j.BasicConfigurator.configure();
        org.apache.log4j.Logger log = org.apache.log4j.Logger.getRootLogger();
        log.setLevel(org.apache.log4j.Level.ALL);
        messageReceiver = listener;
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
        //properties.setProperty("android.javax.sip.REENTRANT_LISTENER", "true");
        properties.setProperty("android.javax.sip.TRACE_LEVEL", "32");
        try {
            /*Class[] e = new Class[]{Class.forName("java.util.Properties")};
            Constructor errmsg1 = Class.forName("android.gov.nist" + ".javax.sip.SipStackImpl").getConstructor(e);
            Object[] conArgs = new Object[]{properties};
            sipStack = (SipStack)errmsg1.newInstance(conArgs);*/

            sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            listeningPoint = sipStack.createListeningPoint(localIP, port, protocol); // a ListeningPoint is a socket wrapper
                                                                        // TODO allow different ports if this one is not available
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
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

        // chooses the first non-localhost IPv4 addr ... this may not be ideal
        while (interfaces.hasMoreElements()) {
            NetworkInterface intfc = interfaces.nextElement();
            Enumeration<InetAddress> addresses = intfc.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (isRealIPv4Address(address)) {
                    /* my understanding is that this should be guaranteed to succeed at least once if 
                       we are sure there is an internet connection and we have a valid list of interfaces
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
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "REGISTER");
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            if (registrationLength == 0) {
                cSeqHeader.setSeqNumber(2L);
                callIdHeader.setCallId(registrationID);
            }
            else
                registrationID = callIdHeader.getCallId();

            FromHeader fromHeader = headerFactory.createFromHeader(serverSipAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(serverSipAddress, null);
            ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(registrationLength);
            Request request = messageFactory.createRequest(requestURI, "REGISTER", callIdHeader,
                    cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
            request.addHeader(expiresHeader);
            request.addHeader(localContactHeader);

            Log.d(TAG, "Sending stateful registration for " + serverSipAddress);


            SipRequester requester = new SipRequester(sipProvider);
            requester.execute(request);
            // we must wait for the request to send, but not for its response
            // get() waits on the other thread, yes this removes the benefit of threading
            if (requester.get().equals("Success")) {
                messageReceiver.TextMessageReceived("Sent registration request");
            }

        } catch (Exception e) {
            // TODO handle the again-numerous error cases
            e.printStackTrace();
        }
    }

    // should use the same call ID as the original register, and increment the CSeq
    public void unregister() throws android.net.sip.SipException {
        try {
            Log.d(TAG, "re-registering for time 0");
            doRegister(0);
            //sipProvider.removeSipListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            sipProvider.removeListeningPoint(listeningPoint);
            sipProvider.removeSipListener(this);
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
        Request request = requestEvent.getRequest();
        Log.d(TAG, "received a request: ");
        Log.d(TAG, request.toString().substring(0,100));
        switch (request.getMethod()) {
            case Request.OPTIONS:
                sendOptions(request);
                break;
            case Request.INVITE:
                receiveCall(request);
                break;
        }
    }

    private void sendOptions(Request request) {

    }

    private void receiveCall(Request request) {

    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        Log.d(TAG, "received a response: ");
        Log.d(TAG, response.toString().substring(0,100));
        int responseCode = responseEvent.getResponse().getStatusCode();
        if (responseCode >= 300) {
            messageReceiver.TextMessageReceived("SIP error: " + responseCode);
        } else if (responseCode >= 200) {
            messageReceiver.TextMessageReceived("SIP OK");
        } else {
            // maybe not do anything?
        }
    }


    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        Log.d(TAG, "received a Timeout message");
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        Log.d(TAG, "received a IOException message: " + ioExceptionEvent.toString());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        Log.d(TAG, "received a TransactionTerminated message: " + transactionTerminatedEvent.toString());
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        Log.d(TAG, "received a DialogTerminated message");
    }

    private class SipRequester extends AsyncTask<Request, String, String> {
        private static final String TAG = "BACKGROUND";
        private SipProvider sipProvider;

        public SipRequester(SipProvider provider) {
            sipProvider = provider;
        }

        @Override
        protected String doInBackground(Request... requests) {
            try {
                ClientTransaction transaction = sipProvider.getNewClientTransaction(requests[0]);
                transaction.sendRequest();
                return "Success";
            } catch (SipException e) {
                Log.e(TAG, "the request still failed. UGH");
                e.printStackTrace();
                return null;
            }
        }
    }
}
