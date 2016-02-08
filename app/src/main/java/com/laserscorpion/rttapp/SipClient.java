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
import android.text.TextUtils;
import android.util.Log;
import android.javax.sip.*;

import org.w3c.dom.Text;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Semaphore;


/**
 * Some of this class based on http://alex.bikfalvi.com/teaching/upf/2013/architecture_and_signaling/lab/sip/
 */
public class SipClient implements SipListener {
    private static SipClient instance;
    private static final String TAG = "SipClient";
    private static final int MAX_FWDS = 70;
    private static final int DEFAULT_REGISTRATION_LEN = 600;
    //private static final String ALLOWED_METHODS = Request.ACK + ", " + Request.BYE + ", "
    //                                            + Request.INVITE + ", " + Request.OPTIONS;
    private static final String ALLOWED_METHODS[] = {Request.ACK, Request.BYE, Request.INVITE, Request.OPTIONS};
    private static String allowed_methods;
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
    private Address globalSipAddress;
    private ContactHeader localContactHeader;
    private String registrationID;
    private ArrayList<TextListener> messageReceivers;
    private SecureRandom randomGen;
    private Semaphore callLock;

    /**
     * Initializes the SipClient to prepare it to register with a server and make calls. Must be called
     * before getInstance(). Calling again resets the parameters to the new ones (note that this will
     * remove all previous TextListeners).
     * @param context
     * @param username
     * @param server
     * @param password
     * @param listener
     * @throws SipException if the SIP stack can't be created, or the username/password/server can't be parsed correctly
     */
    public static void init(Context context, String username, String server, String password, TextListener listener)
            throws SipException {
        if (instance != null) {
            instance.reset(context, username, server, password, listener);
            return;
        }
        instance = new SipClient(context, username, server, password, listener);
    }

    // TODO possibly call reset()
    private SipClient(Context context, String username, String server, String password, TextListener listener)
            throws SipException {
        this.parent = context;
        this.username = username;
        this.server = server;
        this.password = password;
        org.apache.log4j.BasicConfigurator.configure();
        org.apache.log4j.Logger log = org.apache.log4j.Logger.getRootLogger();
        log.setLevel(org.apache.log4j.Level.ALL);
        messageReceivers = new ArrayList<TextListener>();
        messageReceivers.add(listener);
        randomGen = new SecureRandom();
        callLock = new Semaphore(1);
        allowed_methods = TextUtils.join(", ", ALLOWED_METHODS);
        finishInit();
    }

    private void finishInit() throws SipException {
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
        properties.setProperty("android.javax.sip.TRACE_LEVEL", "32");
        try {
            sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            listeningPoint = sipStack.createListeningPoint(localIP, port, protocol); // a ListeningPoint is a socket wrapper
            // TODO allow different ports if this one is not available
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
            Address localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            globalSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
        } catch (Exception e) {
            throw new SipException("Error: could not create SIP stack");
        }
    }

    public void reset(Context context, String username, String server, String password, TextListener listener) throws SipException {
        Address newGlobalSipAddress = null;
        Address localSipAddress = null;
        try {
            newGlobalSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
        } catch (ParseException e) {
            throw new SipException("Error: could not parse new account parameters");
        }
        this.parent = context;
        this.username = username;
        this.server = server;
        this.password = password;
        globalSipAddress = newGlobalSipAddress;
        messageReceivers = new ArrayList<TextListener>();
        addTextReceiver(listener);
    }

    /**
     * Get a handle to the single global SipClient
     * Precondition: init() must be called first at least once
     * @return a handle to the single global SipClient
     */
    public static SipClient getInstance() {
        if (instance == null)
            throw new IllegalStateException("Singleton SipClient has not been initialized yet - init() before getInstance()");
        return instance;
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

    /**
     * This method should be called when a user of SipClient needs to register a new
     * receiver for the SipClient's text output. e.g. when the SipClient is handed from
     * one Activity to another when the foremost activity changes. The old receiver is
     * removed.
     * @param newReceiver the new object (e.g. an Activity) that wants to handle text
     *                    messages from from the SipClient. If null, the SipClient's
     *                    text output will not be processed by anyone.
     */
    public void addTextReceiver(TextListener newReceiver) {
        messageReceivers.add(newReceiver);
    }

    private void sendControlMessage(String message) {
        for (TextListener listener : messageReceivers) {
            listener.ControlMessageReceived(message);
        }
    }

    private void sendRTTChars(String add) {
        for (TextListener listener : messageReceivers) {
            listener.RTTextReceived(add);
        }
    }

    public void register() throws android.net.sip.SipException {
        doRegister(DEFAULT_REGISTRATION_LEN);
    }

    private void doRegister(int registrationLength) throws android.net.sip.SipException {
        int tag = randomGen.nextInt();
        URI requestURI = globalSipAddress.getURI();

        try {
            ArrayList<ViaHeader> viaHeaders = createViaHeaders();
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(MAX_FWDS);            
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "REGISTER");
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            if (registrationLength == 0) {
                cSeqHeader.setSeqNumber(2L);
                callIdHeader.setCallId(registrationID);
            }
            else
                registrationID = callIdHeader.getCallId();

            FromHeader fromHeader = headerFactory.createFromHeader(globalSipAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(globalSipAddress, null);
            Request request = messageFactory.createRequest(requestURI, "REGISTER", callIdHeader,
                    cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
            ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(registrationLength);
            request.addHeader(expiresHeader);
            request.addHeader(localContactHeader);

            Log.d(TAG, "Sending stateful registration for " + globalSipAddress);
            SipRequester requester = new SipRequester(sipProvider);
            requester.execute(request);
            if (requester.get().equals("Success")) {
                // get() waits on the other thread
                // we must wait for the request to send, but not for its response
                // we (probably) aren't waiting long enough to lose the benefit of threading
                sendControlMessage("Sent registration request");
            }

        } catch (Exception e) {
            // TODO handle the again-numerous error cases
            e.printStackTrace();
        }
    }

    private ArrayList<ViaHeader> createViaHeaders() throws ParseException, InvalidArgumentException {
        ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        ViaHeader viaHeader = headerFactory.createViaHeader(listeningPoint.getIPAddress(),
                listeningPoint.getPort(), protocol, null);
        viaHeaders.add(viaHeader);
        return viaHeaders;
    }

    public void unregister() throws android.net.sip.SipException {
        try {
            Log.d(TAG, "re-registering for time 0");
            doRegister(0);
        } catch (Exception e) {
            // TODO handle this
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
            // TODO handle this
            e.printStackTrace();
        }
    }

    public void call(String URI) throws SipException, ParseException {
        boolean available = callLock.tryAcquire();
        if (!available)
            throw new TransactionUnavailableException();
        Address contact = addressFactory.createAddress("sip:" + URI);
        int tag = randomGen.nextInt();

        try {
            ArrayList<ViaHeader> viaHeaders = createViaHeaders();
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(MAX_FWDS);
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "INVITE");
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            FromHeader fromHeader = headerFactory.createFromHeader(globalSipAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(contact, null);

        } catch (Exception e) {

        }
        try {
            Thread.sleep(500, 0);
        } catch (InterruptedException e) {}
        Log.d(TAG, "calling " + URI);
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
            default:
                Log.d(TAG, "Not implemented yet");
                break;
        }
    }

    private void sendOptions(Request request) {
        int tag = randomGen.nextInt();
        try {
            AllowHeader allowHeader = headerFactory.createAllowHeader(allowed_methods);
            Response response = messageFactory.createResponse(getCurrentStatusCode(), request);
            response.addHeader(allowHeader);
            ToHeader toHeader = (ToHeader)response.getHeader("To");
            toHeader.setTag(String.valueOf(tag));
            response.removeHeader("To");
            response.addHeader(toHeader);
            if (request.getHeader("Accept") != null) {
                // TODO send the message body that this request is demanding
            }
            SipResponder responder = new SipResponder(sipProvider);
            responder.execute(request, response);
        } catch (Exception e) {
            // again, this is a lot of exceptions to catch all at once. oh well...
            // TODO handle this
            e.printStackTrace();
        }

    }

    private int getCurrentStatusCode() {
        if (callLock.tryAcquire()) {
            callLock.release();
            return Response.OK;
        } else {
            return Response.BUSY_HERE;
        }
    }

    private void receiveCall(Request request) {

    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        //Log.d(TAG, "received a response: ");
        //Log.d(TAG, response.toString().substring(0,100));
        int responseCode = responseEvent.getResponse().getStatusCode();
        if (responseCode >= 300) {
            sendControlMessage("SIP error: " + responseCode);
        } else if (responseCode >= 200) {
            sendControlMessage("SIP OK");
        } else {
            if (responseCode == Response.RINGING) {
                sendControlMessage("Ringing...");
            }
            // maybe not do anything else for 1xx?
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

    // these nested classes are used to fire one-off threads and then die
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
    private class SipResponder extends AsyncTask<Message, String, String> {
        private static final String TAG = "BACKGROUND";
        private SipProvider sipProvider;

        public SipResponder(SipProvider provider) {
            sipProvider = provider;
        }

        /**
         *
         * @param messages must be exactly 2 Messages, a Request and a Response, in that order
         * @return
         */
        @Override
        protected String doInBackground(Message... messages) {
            Request request = (Request)messages[0];
            Response response = (Response)messages[1];
            try {
                ServerTransaction transaction = sipProvider.getNewServerTransaction(request);
                transaction.sendResponse(response);
                return "Success";
            } catch (Exception e) {
                Log.e(TAG, "the response failed. UGH");
                e.printStackTrace();
                return null;
            }
        }
    }
}
