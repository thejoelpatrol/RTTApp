package com.laserscorpion.rttapp;

import android.content.Context;
import android.gov.nist.javax.sdp.fields.AttributeField;
import android.gov.nist.javax.sip.SipStackImpl;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.gov.nist.javax.sip.header.ContentEncoding;
import android.javax.sdp.Attribute;
import android.javax.sdp.Connection;
import android.javax.sdp.Media;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.Origin;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.SipException;
import android.javax.sip.address.*;
import android.javax.sip.header.*;
import android.javax.sip.message.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.javax.sip.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.*;

/**
 * Some of this class based on http://alex.bikfalvi.com/teaching/upf/2013/architecture_and_signaling/lab/sip/
 */
public class SipClient implements SipListener {
    private static SipClient instance;
    private static final String TAG = "SipClient";
    private static final int MAX_FWDS = 70;
    private static final int DEFAULT_REGISTRATION_LEN = 600;
    private static final int CALL_RINGING_TIME = 30;
    private static final String ALLOWED_METHODS[] = {Request.ACK, Request.BYE, Request.INVITE, Request.OPTIONS, Request.CANCEL};
    private android.content.Context parent;
    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private SdpFactory SDPFactory;
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
    private AllowHeader allowHeader;
    private MaxForwardsHeader maxForwardsHeader;
    private String registrationID;
    private List<TextListener> messageReceivers;
    private List<SessionListener> sessionReceivers;
    private CallReceiver callReceiver;
    private SecureRandom randomGen;

    /*  callLock is used in a fairly normal way: it must be held whenever making a change
        to currentCall. However, it is sometimes held by an earlier action, a precondition
        of the current action, so it is not necessarily acquired every time currentCall
        is modified.
     */
    private Semaphore callLock;
    //private Semaphore callDestructionLock;
    //private RTTCall incomingCall;
    private RTTCall currentCall;
    //private RTTCall connectedCall;
    //private RTTCall outgoingCall;

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
        messageReceivers = new LinkedList<TextListener>();
        messageReceivers.add(listener);
        sessionReceivers = new LinkedList<SessionListener>();
        randomGen = new SecureRandom();
        callLock = new Semaphore(1);
        //callDestructionLock = new Semaphore(1);
        //allowed_methods = TextUtils.join(", ", ALLOWED_METHODS);
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
        //properties.setProperty("android.javax.sip.REENTRANT_LISTENER", "true");
        //properties.setProperty("android.gov.nist.javax.sip.REENTRANT_LISTENER", "true");
        try {
            sipStack = sipFactory.createSipStack(properties);
            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            SDPFactory = SdpFactory.getInstance();
            listeningPoint = sipStack.createListeningPoint(localIP, port, protocol); // a ListeningPoint is a socket wrapper
            // TODO allow different ports if this one is not available
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
            Address localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            globalSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
            allowHeader = headerFactory.createAllowHeader(TextUtils.join(", ", ALLOWED_METHODS));
            maxForwardsHeader = headerFactory.createMaxForwardsHeader(MAX_FWDS);
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
        if (messageReceivers.contains(newReceiver))
            return;
        messageReceivers.add(newReceiver);
    }

    /**
     * Register some object as interested in listening for incoming calls. It becomes
     * the listener's job to accept or decline a call when one arrives.
     * @param receiver the CallReceiver who should be notified when a call comes in.
     *                 There can only be one receiver listening for this message at a time.
     */
    public void registerCallReceiver(CallReceiver receiver) {
        callReceiver = receiver;
    }

    /**
     *
     * @param receiver the TextReceiver that should no longer receive text messages
     */
    public void removeTextReceiver(TextListener receiver) {
        if (messageReceivers.contains(receiver))
            messageReceivers.remove(receiver);
    }

    public void addSessionListener(SessionListener newListener) {
        if (sessionReceivers.contains(newListener))
            return;
        sessionReceivers.add(newListener);

    }
    public void removeSessionListener(SessionListener existingListener) {
        if (sessionReceivers.contains(existingListener))
            sessionReceivers.remove(existingListener);
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

    private void notifySessionFailed(String reason) {
        for (SessionListener listener : sessionReceivers) {
            listener.SessionFailed(reason);
        }
    }
    private void notifySessionClosed() {
        try {
            /* this is a bad concurrency hack to make sure the SessionListener has
            registered before it must be killed due to receiving an immediate BYE */
            Thread.sleep(500, 0);
        } catch (InterruptedException e) {}

        for (SessionListener listener : sessionReceivers) {
            listener.SessionClosed();
        }
    }

    public void register() throws android.net.sip.SipException {
        doRegister(DEFAULT_REGISTRATION_LEN, null);
    }

    private void doRegister(int registrationLength, Header extraHeader) throws android.net.sip.SipException {
        int tag = randomGen.nextInt();
        URI requestURI = globalSipAddress.getURI();

        try {
            ArrayList<ViaHeader> viaHeaders = createViaHeaders();
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
            if (extraHeader != null)
                request.addHeader(extraHeader);
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
            doRegister(0, null);
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

    public void call(String URI) throws SipException, ParseException, TransactionUnavailableException {
        boolean available = callLock.tryAcquire();
        if (!available)
            throw new TransactionUnavailableException("Can't call now -- already modifying call state");
        if (currentCall != null) {
            callLock.release();
            Log.d(TAG, "555555555 should be 1:" + callLock.availablePermits());
            throw new TransactionUnavailableException("Can't call now -- already on a call");
        }
        Address contact = addressFactory.createAddress("sip:" + URI);
        int tag = randomGen.nextInt();

        try {
            ArrayList<ViaHeader> viaHeaders = createViaHeaders();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            FromHeader fromHeader = headerFactory.createFromHeader(globalSipAddress, String.valueOf(tag));
            ToHeader toHeader = headerFactory.createToHeader(contact, null);
            Request request = messageFactory.createRequest(contact.getURI(), Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwardsHeader);
            request.addHeader(allowHeader);
            request.addHeader(localContactHeader);
            ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(CALL_RINGING_TIME);
            request.addHeader(expiresHeader);
            addSDPContentAndHeader(request, 0);
            SipRequester requester = new SipRequester(sipProvider);
            requester.execute(request);
            currentCall = new RTTCall(request, null);
            currentCall.setCalling();
            if (requester.get().equals("Success")) {
                // get() waits on the other thread
                // we must confirm the request sent, but no need to wait for its response
                // we (probably) aren't waiting long enough to lose the benefit of threading
                sendControlMessage("Sent INVITE request");
            } else {
                callLock.release();
                Log.d(TAG, "6666666666 should be 1:" + callLock.availablePermits());
                throw new SipException("async thread failed to send request");
            }
        } catch (Exception e) {
            callLock.release();
            Log.d(TAG, "777777777777 should be 1:" + callLock.availablePermits());
            e.printStackTrace();
            throw new SipException("couldn't send request; " + e.getMessage());
        }
    }

    private boolean onACallNow() {
        return currentCall != null;
    }

    private void addSDPContentAndHeader(Message message, int preferredMap) {
        String sdp = createSDPContent(preferredMap);
        ContentTypeHeader typeHeader;
        try {
            typeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            message.setContent(sdp, typeHeader);
        } catch (Exception e) {
            // TODO do i need to handle this?
            e.printStackTrace();
        }

    }

    private String createSDPContent(int mapNum) {
        final int SAMPLE_RATE = 1000; // defined by RFC 4103 p.15
        int sessionID = Math.abs(randomGen.nextInt());
        if (mapNum == 0)
            mapNum = 100;
        try {
            int codecs[] = {mapNum};
            MediaDescription textMedia = SDPFactory.createMediaDescription("text", port+1, 1, "RTP/AVP", codecs);
            textMedia.setAttribute("rtpmap", mapNum + " t140/" + SAMPLE_RATE);
            AttributeField sendrecv = new AttributeField();
            sendrecv.setName("sendrecv");
            sendrecv.setValueAllowNull(null);
            textMedia.addAttribute(sendrecv);

            /* creating the session description requires checking the IP address (poorly)
               this is a "network" operation so isn't normally allowed by Android on the main thread
               what's annoying is that we have to replace the garbage that createSessionDescription()
               puts in session anyway */
            StrictMode.ThreadPolicy tp0 = StrictMode.getThreadPolicy();
            StrictMode.ThreadPolicy tp1 = StrictMode.ThreadPolicy.LAX;
            StrictMode.setThreadPolicy(tp1);
            SessionDescription session = SDPFactory.createSessionDescription();
            Origin origin = SDPFactory.createOrigin(username, sessionID, 1, "IN", "IP4", localIP);
            session.setOrigin(origin);
            StrictMode.setThreadPolicy(tp0);

            Connection connection = SDPFactory.createConnection(localIP);
            session.setConnection(connection);
            Vector mediaDescs = session.getMediaDescriptions(true);
            mediaDescs.add(textMedia);
            session.setMediaDescriptions(mediaDescs);
            session.setSessionName(SDPFactory.createSessionName("RTT_SDP_v0.1"));
            return session.toString();
        } catch (SdpException e) {
            Log.e(TAG, "could not create SDP");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        Log.d(TAG, "received a request: " + request.getMethod());
        Log.d(TAG, request.toString().substring(0, 100));
        switch (request.getMethod()) {
            case Request.OPTIONS:
                sendOptions(requestEvent);
                break;
            case Request.INVITE:
                receiveCall(requestEvent);
                break;
            case Request.ACK:
                //Log.d(TAG, "Not really implemented yet");
                if (currentCall != null && currentCall.isRinging())
                    currentCall.accept();
                else
                    Log.e(TAG, "stray ACK, what do I do? In response to a 488?");
                break;
            case Request.BYE:
                endCall(requestEvent);
                break;
            case Request.CANCEL:
                cancelCall(requestEvent);
                break;
            default:
                Log.d(TAG, "Not implemented yet");
                break;
        }
    }

    private void sendOptions(RequestEvent requestEvent) {
        int tag = randomGen.nextInt();
        Request request = requestEvent.getRequest();
        try {
            Response response = messageFactory.createResponse(getCurrentStatusCode(), request);
            response.addHeader(allowHeader);
            ToHeader toHeader = (ToHeader)response.getHeader("To");
            toHeader.setTag(String.valueOf(tag));
            response.removeHeader("To");
            response.addHeader(toHeader);
            if (request.getHeader("Accept") != null) {
                // TODO send the message body that this request is demanding
            }
            SipResponder responder = new SipResponder(sipProvider, requestEvent, null);
            responder.execute(response);
        } catch (Exception e) {
            // again, this is a lot of exceptions to catch all at once. oh well...
            // TODO handle this
            e.printStackTrace();
        }

    }

    private int getCurrentStatusCode() {
        if (!onACallNow()) {
            return Response.OK;
        } else {
            return Response.BUSY_HERE;
        }
    }

    /**
     * Precondition: currently connected on or creating a call
     */
    public void hangUp() {
        if (onACallNow()) {
            if (currentCall.isCalling()) {
                sendCancel();
                callLock.release();
                Log.d(TAG, "88888888888 should be 1:" + callLock.availablePermits());
            }
            else if (currentCall.isConnected())
                sendBye(currentCall.getDialog());
            else
                Log.d(TAG, "Unsure what is being hung up");
        }
        terminateCall();
    }

    /**
     * Precondition: a CallReceiver has been notified that a call is coming in
     * @throws IllegalStateException if there is no call waiting to be accepted (possibly because it already hung up),
     *      or if we are otherwise not in the midst of setting up a new call
     */
    /*  Precondition: callLock is locked, and currentCall has a copy of the incoming request event.
        If a call is truly coming in, these will be satisfied.
     */
    public void acceptCall() throws IllegalStateException {
        if (!onACallNow())
            throw new IllegalStateException("no call to accept");
        if (callLock.availablePermits() != 0)
            throw new IllegalStateException("call state is not locked, we must not be setting up a new call");
        RequestEvent incomingEvent = currentCall.getCreationEvent();
        if (incomingEvent == null)
            throw new IllegalStateException("current call is not incoming, it was created outgoing");

        Request request = currentCall.getCreationRequest();
        try {
            Response response = messageFactory.createResponse(Response.OK, request);
            int suggestedT140Map = getT140MapNum(request);
            addSDPContentAndHeader(response, suggestedT140Map);
            response.addHeader(localContactHeader);
            if (request.getHeader("Accept") != null) {
                // TODO send the message body that this request is demanding
            }
            SipResponder responder = new SipResponder(sipProvider, incomingEvent, currentCall.getInviteTransaction());
            responder.execute(response);
        } catch (Exception e) {
            // again, this is a lot of exceptions to catch all at once. oh well...
            // TODO handle this
            e.printStackTrace();
            currentCall.end();
        }
        callLock.release();
        Log.d(TAG, "9999999999 should be 1:" + callLock.availablePermits());
    }

    /* I'll be mad if it turns out there is a way to do this automatically with the
    SDP library, but as far as I can tell, all it does is structure the string into
    discrete lines of different types
     */
    private int getT140MapNum(Request otherPartyRequest) {
        String body = new String(otherPartyRequest.getRawContent(), StandardCharsets.UTF_8);
        try {
            SessionDescription suggestedSession = SDPFactory.createSessionDescription(body);
            Vector<MediaDescription> mediaDescriptions = suggestedSession.getMediaDescriptions(true);
            for (MediaDescription mediaDescription : mediaDescriptions) {
                Media media = mediaDescription.getMedia();
                if (media.getMediaType().equals("text")) {
                    Vector<Attribute> attributes = mediaDescription.getAttributes(true);
                    for (Attribute attr : attributes) {
                        String attrValue = attr.getValue().toLowerCase();
                        String attrName = attr.getName();
                        if (attrName.equals("rtpmap") && attrValue.contains("t140") && !attrValue.contains("red")) {
                            String mapNum = attrValue.split(" ")[0];
                            return Integer.decode(mapNum);
                        }
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /*  Precondition: callLock is locked, and currentCall has a copy of the incoming request event.
        If a call is truly coming in, these will be satisfied.
     */
    public void declineCall() throws IllegalStateException {
        if (!onACallNow())
            throw new IllegalStateException("no call to decline");
        if (!currentCall.isRinging())
            throw new IllegalStateException("no call ringing now");
        RequestEvent incomingRequest = currentCall.getCreationEvent();
        if (incomingRequest == null)
            throw new IllegalStateException("current call is not incoming, can't decline it");
        if (callLock.availablePermits() > 0)
            throw new IllegalStateException("call state is not locked, we must not be receiving a call");

        Request request = incomingRequest.getRequest();
        respondGeneric(incomingRequest, currentCall.getInviteTransaction(), Response.BUSY_HERE);
        currentCall.end();
        currentCall = null;
        callLock.release();
        Log.d(TAG, "154254124512 should be 1:" + callLock.availablePermits());
    }

    private void receiveCall(RequestEvent requestEvent) {
        boolean lockAvailable = callLock.tryAcquire();
        if (lockAvailable && !onACallNow()) {
            Log.d(TAG, "just acquired the lock - permits remaining: " + callLock.availablePermits());
            Log.d(TAG, "we're available...");
            if (callReceiver != null) {
                Log.d(TAG, "asking receiver to accept...");
                ServerTransaction transaction = respondGeneric(requestEvent, null, Response.RINGING);
                currentCall = new RTTCall(requestEvent, transaction);
                currentCall.setRinging();
                callReceiver.callReceived();
            } else {
                // TODO respond 4xx
                Log.e(TAG, "uh oh, we're releasing this lock...why?");
                callLock.release();
                Log.d(TAG, "9807896778956789 should be 1:" + callLock.availablePermits());
            }
        } else {
            if (lockAvailable) {
                callLock.release(); // we just acquired this, but can't use it after all
                Log.d(TAG, "545456767887 should be 1:" + callLock.availablePermits());
            }
            RTTCall latestCall = new RTTCall(requestEvent, null);
            if (currentCall.equals(latestCall)) {
                // asterisk sends many duplicate invites
                Log.d(TAG, "ignoring a duplicate INVITE");
                return;
            }
            respondGeneric(requestEvent, null, Response.BUSY_HERE);
        }
    }

    private void cancelCall(RequestEvent requestEvent) {
        if (currentCall.isRinging() && cancelApplies(requestEvent, currentCall)) {
            RequestEvent initialINVITE = currentCall.getCreationEvent();
            respondGeneric(initialINVITE, initialINVITE.getServerTransaction(), Response.REQUEST_TERMINATED);
            notifySessionFailed("Caller cancelled call");
            callLock.release();
            Log.d(TAG, "1111111111 should be 1:" + callLock.availablePermits());
            terminateCall();
        }
    }

    private boolean cancelApplies(RequestEvent cancelRequest, RTTCall cancelableCall) {
        Dialog dialog = cancelRequest.getDialog();
        CallIdHeader cancelID = dialog.getCallId();
        String cancelCallID = cancelID.toString();

        Request callCreationRequest = cancelableCall.getCreationRequest();
        CallIdHeader callID = (CallIdHeader)callCreationRequest.getHeader("Call-ID");
        String checkID = callID.toString();

        return cancelCallID.equals(checkID);
    }


    private ServerTransaction respondGeneric(RequestEvent requestEvent, ServerTransaction existingTransaction, int sipResponse) {
        Log.d(TAG, "sending generic response: " + sipResponse);
        int tag = randomGen.nextInt();
        try {
            Response response = messageFactory.createResponse(sipResponse, requestEvent.getRequest());
            ToHeader toHeader = (ToHeader)response.getHeader("To");
            toHeader.setTag(String.valueOf(tag));
            response.removeHeader("To");
            response.addHeader(toHeader);
            response.addHeader(localContactHeader);
            SipResponder responder = new SipResponder(sipProvider, requestEvent, existingTransaction);
            responder.execute(response);
            ServerTransaction transaction = responder.get();
            return transaction;
        } catch (Exception e) {
            // again, this is a lot of exceptions to catch all at once. oh well...
            // TODO handle this
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Precondition: currently on a call, which requestEvent is trying to end
     * @param requestEvent a BYE request we have just received
     */
    private void endCall(RequestEvent requestEvent) {
        // TODO do I need to do anything else to tear down the session? maybe close the RTP streams once I have them
        Request request = requestEvent.getRequest();
        respondGeneric(requestEvent, null, Response.OK);
        notifySessionClosed();
        terminateCall();
    }

    private void terminateCall() {
        callLock.acquireUninterruptibly();
        if (onACallNow()) {
            // multiple calls to terminateCall() may occur in quick succession due to duplicate BYEs
            // therefore we must safely check that there is still a call to end
            currentCall.end();
            currentCall = null;
        }
        callLock.release();
        Log.d(TAG, "2222222222 should be 1:" + callLock.availablePermits());
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int responseCode = response.getStatusCode();
        Log.d(TAG, "received a response: " + responseCode);
        if (responseCode >= 300) {
            sendControlMessage("SIP error: " + responseCode);
            handleFailure(responseEvent);
        } else if (responseCode >= 200) {
            sendControlMessage("SIP OK");
            handleSuccess(responseEvent);
        } else {
            if (responseCode == Response.RINGING) {
                sendControlMessage("Ringing...");
            }
            // maybe not do anything else for 1xx?
        }
    }

    /* Precondition: if responseEvent is in response to an INVITE,
                     callLock was locked when initiating the call */
    private void handleFailure(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        if (response.getStatusCode() == Response.UNAUTHORIZED || response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED) {
            handleChallenge(responseEvent);
        } else if (isInviteResponse(responseEvent)) {
            switch (response.getStatusCode()) {
                case Response.BUSY_HERE:
                    notifySessionFailed("busy");
                    break;
                case Response.DECLINE:
                    notifySessionFailed("call declined");
                    break;
                case Response.NOT_FOUND:
                    notifySessionFailed("user not found");
                    break;
                case Response.NOT_ACCEPTABLE:
                    notifySessionFailed("callee doesn't support RTT");
                    break;
                default:
                    notifySessionFailed("call failed");
                    break;
            }
            hangUp();
        } else if (isRegisterResponse(responseEvent)) {
            switch (response.getStatusCode()) {
                default:
                    Log.d(TAG, "registration failure not implemented");
                    break;

            }
        }
    }

    private boolean isXResponse(ResponseEvent responseEvent, String method) {
        Response response = responseEvent.getResponse();
        String inResponseTo = response.getHeader("CSeq").toString();
        if (inResponseTo.contains(method))
            return true;
        return false;
    }

    private boolean isInviteResponse(ResponseEvent responseEvent) {
        return isXResponse(responseEvent, Request.INVITE);
    }

    private boolean isRegisterResponse(ResponseEvent responseEvent) {
        return isXResponse(responseEvent, Request.REGISTER);
    }

    /* This usage inspired by https://stackoverflow.com/questions/21840496/asterisk-jain-sip-why-do-i-need-to-authenticate-several-times   */
    private void handleChallenge(ResponseEvent responseEvent) {
        Log.d(TAG, "!!!! 44444 handling challenge!");
        ClientTransaction origTransaction = responseEvent.getClientTransaction();
        AccountManagerImpl manager = new AccountManagerImpl();
        SipStackExt stack = (SipStackExt)sipStack; // this cast is legal, but sketchy, we need v2.0 of NIST JAIN SIP
        AuthenticationHelper authenticator = stack.getAuthenticationHelper(manager, headerFactory);
        try {
            ClientTransaction transaction = authenticator.handleChallenge(responseEvent.getResponse(), origTransaction, sipProvider, 10);
            SipTransactionRequester requester = new SipTransactionRequester(sipProvider);
            requester.execute(transaction);
        } catch (Exception e) {
            Log.e(TAG, "Unable to respond to challenge");
            e.printStackTrace();
        }
    }

    private void handleSuccess(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        Dialog dialog = responseEvent.getDialog();
        if (isInviteResponse(responseEvent))
            handleInviteSuccess(responseEvent);
        else if (isRegisterResponse(responseEvent))
            sendControlMessage("Registered");
        else
            Log.d(TAG, "need to implement handling other successes");
    }

    /*
        Preconditions:  -callLock is locked when the call was initiated
                        -currentCall is not null and is waiting for a response to an outgoing call
     */
    private void handleInviteSuccess(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        Dialog dialog = responseEvent.getDialog();

        ACKResponse(response, dialog);

        currentCall.addDialog(dialog);
        if (sessionIsAcceptable(response)) {
            Log.d(TAG, "acceptable!");
            currentCall.callAccepted();
        } else {
            Log.d(TAG, "not acceptable");
            sendBye(dialog);
            currentCall.end();
            currentCall = null;
            notifySessionFailed("other party doesn't support RTT");
        }
        callLock.release();
        Log.d(TAG, "44444444444 should be 1:" + callLock.availablePermits());
    }

    private void ACKResponse(Response response, Dialog dialog) {
        CSeqHeader cseq = (CSeqHeader)response.getHeader("CSeq");
        long seqNo = cseq.getSeqNumber();
        AckSender acknowledger = new AckSender(dialog);
        acknowledger.doInBackground(seqNo);
    }

    private boolean sessionIsAcceptable(Response response) {
        /* this is slightly imprecise because we are not looking specifically
        at the ContentTypeHeader and are not looking specifically inside the media
        descriptions and attributes in the SDP ... but it works */
        ListIterator<Header> list = response.getHeaders("Content-Type");
        while (list.hasNext()) {
            Header header = list.next();
            if (header.toString().contains("application/sdp")) {
                 byte content[] = response.getRawContent();
                String strContent = new String(content, StandardCharsets.UTF_8);
                if (strContent.toLowerCase().contains("t140/1000")) {
                    Log.d(TAG, "It's ACCEPTABLE!!!");
                    return true;
                }
            }
        }
        return false;
    }

    private void sendBye(Dialog dialog) {
        Log.d(TAG, "sending BYE");
        ByeSender bye = new ByeSender(sipProvider);
        bye.execute(dialog);
    }

    private void sendCancel() {
        // TODO implement this, which is probably nontrivial
        Log.d(TAG, "(Not actually) sending CANCEL");
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        //Log.d(TAG, "received a Timeout message");
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        //Log.d(TAG, "received a IOException message: " + ioExceptionEvent.toString());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        //Log.d(TAG, "received a TransactionTerminated message: " + transactionTerminatedEvent.toString());
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        //Log.d(TAG, "received a DialogTerminated message");
        // TODO should I have used this to detect call ending?
    }

    // these nested classes are used to fire one-off threads and then die
    private class AckSender extends AsyncTask<Long, String, String> {
        private Dialog dialog;
        public AckSender(Dialog dialog) {
            this.dialog = dialog;
        }

        @Override
        protected String doInBackground(Long... params) {
            try {
                Request ack = dialog.createAck(params[0]);
                dialog.sendAck(ack);
            } catch (InvalidArgumentException e) {
                e.printStackTrace();
            } catch (SipException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    private class ByeSender extends AsyncTask<Dialog, String, String> {
        SipProvider sipProvider;
        public ByeSender(SipProvider sipProvider ) {
            this.sipProvider = sipProvider;
        }

        @Override
        protected String doInBackground(Dialog... dialogs) {
            try {
                Dialog dialog = dialogs[0];
                Request bye = dialog.createRequest(Request.BYE);
                ClientTransaction transaction = sipProvider.getNewClientTransaction(bye);
                dialog.sendRequest(transaction);
            } catch (SipException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    /* this helper class from https://stackoverflow.com/questions/21840496/asterisk-jain-sip-why-do-i-need-to-authenticate-several-times */
    private class AccountManagerImpl implements AccountManager {
        @Override
        public UserCredentials getCredentials(ClientTransaction clientTransaction, String s) {
            return new UserCredentials() {
                @Override
                public String getUserName() { return username; }
                @Override
                public String getPassword() { return password; }
                @Override
                public String getSipDomain() { return server; }
            };
        }
    }
}
