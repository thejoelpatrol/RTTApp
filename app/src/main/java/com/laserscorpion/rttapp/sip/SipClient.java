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

import android.content.Context;
import android.content.IntentFilter;
import android.gov.nist.javax.sip.address.AddressImpl;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.javax.sip.SipException;
import android.javax.sip.address.*;
import android.javax.sip.header.*;
import android.javax.sip.message.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.StrictMode;
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
import java.util.Timer;
import java.util.TooManyListenersException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.*;

import com.laserscorpion.rttapp.BuildConfig;
import com.laserscorpion.rttapp.rtp.RTTCall;

import gov.nist.jrtp.RtpException;

/**
 * <p>This is the core of the SIP layer, and the entire app. All SIP messages are received here, and
 * sent from here via SipRequester/SipResponder/SipTransactionRequester. It is a singleton, as it
 * makes up much of the app, so the proper usage is <em>first</em> call init() and <em>then</em>
 * call getInstance() whenever a handle to this class is needed. If necessary later, calling
 * reset() re-initializes some of the parameters from init(). When disconnecting from the server or
 * quitting, it is necessary to call close().</p>
 * <pre>
 *     --------------
 *     |  UI layer  |
 *     --------------
 *            |
 *     <strong>--------------
 *     | SIP layer  |  (SipClient)
 *     --------------</strong>
 *            |
 *     --------------
 *     | Call layer |  (RTTCall)
 *     --------------
 *            |
 *     --------------
 *     | RTP layer  |  (JRTP, Omnitor t140)
 *     --------------
 * </pre>
 * <p>Though RTTCall runs the actual RTP sessions, SipClient is responsible for negotiating the
 * session, and it can only manage one call at a time. It knows when a call is currently connected,
 * or ringing, because it arranges it via SIP messages, interaction from the UI layer above, and
 * creating each RTTCall. If a call comes in while another one is in progress, SipClient responds
 * 486 Busy Here. onACallNow() indicates whether a call is in progress. An RTTCall is created early
 * in the call establishing sequence, and SipClient later updates RTTCall when the call is fully
 * agreed upon and the RTP parameters are available.</p>
 *
 * <p>The incoming call (acceptance) sequence is a bit hairy and involves the other layers. This could
 * probably use some refactoring or at least refinement to ensure reliability. This sequence grew
 * out of experience with the concurrency problems surrounding duplicate or multiple requests coming
 * in simultaneously, and the desire to only handle one call at a time. The steps are as follows:</p>
 * <pre>
 * 1. INVITE received
 * 2. receiveCall():
 *      a. <strong>tries</strong> to acquire callLock, and holds it to prevent any other calls being created
 *      b. checks if onACallNow()
 *      c. responds 180 Ringing
 *      d. creates RTTCall
 *      e. alerts callReceiver (CallReceiver listener in UI layer) that a call is ringing
 * 3. UI layer accepts or declines call
 *      a. calls SipClient.acceptCall()
 *      b. or SipClient.declineCall()
 * 4. acceptCall():
 *      a. sends 200 OK response
 *      b. releases callLock, and now relies on onACallNow() to detect business
 * 5. ACK received
 * 6. beginCall():
 *      a. gives RTP params to RTTCall
 *      b. waits for UI to get ready to receive text
 *      c. updates RTTCall's list of TextReceivers and tells it to start
 *      d. tells UI layer that call is established with notifySessionEstablished()</pre>
 * <p>Some of this complication is unavoidable due to SIP's multi-step process, but the locking is
 * especially weird, I know. It's sketchy to acquire a lock, wait for some other part of the app
 * to do something, assume the lock is still held, and then later release it. The purpose of the
 * lock is to prevent simultaneous INVITEs from triggering receiveCall(), so it seems like it could
 * be released after creating the RTTCall, but my fuzzy memory suggests that some problem occurred.
 * There's a reason I didn't release the lock until later, but now that I have forgotten it, I feel
 * bad telling you not to change this. This might be ok to change, but beware. Getting it right with
 * all the incoming traffic I was seeing was tough, so I'm wary of breaking it, but it's probably
 * fragile as it is now anyway.</p>
 *
 * <p>All network communication in this class, and indeed this layer, is done by helper AsyncTask
 * subclasses, e.g. SipRequester. This might not really be necessary. Android is strict about using
 * the network on the main thread, which is usually good, but in this case sort of annoying. You don't
 * really want to be doing TCP on the main thread, but the only network stuff that SipClient is
 * doing is sending SIP messages over UDP, which basically happens immediately, no waiting involved.
 * So we could disable this restriction with StrictMode.ThreadPolicy.LAX? This would allow us to
 * get rid of the unwieldy AsyncTasks. The main concern is whether setting this global option would
 * have other unintended consequences in other parts of the app.</p>
 *
 * <p>SipClient is listening for changes in internet connectivity, specifically changes in the IP
 * address, which happens often enough to really care about it on a mobile device.
 * When the IP changes, it must re-register with the server at the new address. If this
 * happens during a call, the call will mysteriously hang, so SipClient will end a call if it
 * detects this change.</p>
 *
 * <p>Other classes often need to receive messages from SipClient, when network/call state changes.
 * Thus it has lists of listeners of various types, managed with methods such as
 * addSessionListener()/removeSessionListener(). When an event occurs that interests those listeners,
 * SipClient notifies all of them, if there is more than one, with a method such as e.g.
 * notifySessionClosed().</p>
 *
 * <p>Currently SipClient does not do a great job correlating sequences of messages, e.g. a request
 * we sent and the response to it that we get back. It should probably maintain a data structure of
 * Requests and Responses it has sent, and the transactions/dialogs involved in them, to
 * definitively know the purpose of some incoming message, whether a SIP message or something from
 * the SipStack, like a TransactionTerminated message. This would also help weed out stray messages
 * that we don't care about.</p>
 *
 * <p>Some of this class based on http://alex.bikfalvi.com/teaching/upf/2013/architecture_and_signaling/lab/sip/
 */
public class SipClient implements SipListener, ConnectivityReceiver.IPChangeListener {
    private static final String TAG = "SipClient";
    private static final int MAX_FWDS = 70;
    private static final int DEFAULT_REGISTRATION_LEN = 600;
    private static final int CALL_RINGING_TIME = 30;
    private static final String ALLOWED_METHODS[] = {Request.ACK, Request.BYE, Request.INVITE, Request.OPTIONS, Request.CANCEL};
    private static SipClient instance;
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
    private AllowHeader allowHeader;
    private MaxForwardsHeader maxForwardsHeader;
    private String registrationID;
    private List<TextListener> messageReceivers;
    private List<SessionListener> sessionReceivers;
    private CallReceiver callReceiver;
    private SecureRandom randomGen;
    private ConnectivityReceiver connectivityReceiver;
    private boolean registrationPending = false;
    private Timer registrationTimer;

    /*  callLock has a fairly normal purpose: it must be held whenever making a change
        to the currentCall reference. However, it is sometimes held by an earlier action, a
        precondition of the current action, so it is not necessarily acquired every time currentCall
        is modified. This usage is strange, I know. See above.
     */
    private Semaphore callLock;
    private RTTCall currentCall;

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
        connectivityReceiver = new ConnectivityReceiver(this);
        registrationTimer = new Timer();

        parent.registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        finishInit();
    }

    private synchronized void finishInit() throws SipException {
        resetLocalIP();
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
            openListeningPoint();
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
            Address localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            globalSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
            allowHeader = headerFactory.createAllowHeader(TextUtils.join(", ", ALLOWED_METHODS));
            maxForwardsHeader = headerFactory.createMaxForwardsHeader(MAX_FWDS);
        } catch (ParseException e) {
            parent.unregisterReceiver(connectivityReceiver);
            throw new SipException("Error: could not parse SIP address (" + username + "@" + server +")", e);
        } catch (Exception e) {
            parent.unregisterReceiver(connectivityReceiver);
            throw new SipException("Error: could not create SIP stack", e);
        }
    }

    private void openListeningPoint() throws SipException {
        //Log.d(TAG, "openListeningPoint()");
        while (listeningPoint == null) {
            try {
                listeningPoint = sipStack.createListeningPoint(localIP, port, protocol); // a ListeningPoint is a socket wrapper
                return;
            } catch (InvalidArgumentException e) {
                if (e.getMessage().contains("BindException")) { // getCause() strangely returns e, not the BindException that caused it, so check the string
                    if (port > 65535)
                        throw new SipException("could not create listening point - no ports available", e);
                    port++; // loop, try the next port
                } else {
                    throw new SipException("could not create listening point", e);
                }
            } catch (SipException e) {
                throw new SipException("could not create listening point", e);
            }
        }
    }

    private synchronized void resetLocalIP() throws SipException {
        try {
            String oldIP = localIP;
            findLocalIP();
            if (oldIP == null || !oldIP.equals(localIP)) {
                if (sipStack != null) {
                    // assumption: if sipStack != null, neither is addressFactory, headerFactory, etc
                    if (listeningPoint != null) {
                        sipProvider.removeListeningPoint(listeningPoint);
                        sipStack.deleteListeningPoint(listeningPoint);
                    }
                    listeningPoint = sipStack.createListeningPoint(localIP, port, protocol);
                    sipProvider.addListeningPoint(listeningPoint);
                    Address localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
                    localContactHeader = headerFactory.createContactHeader(localSipAddress);
                }
            }
        } catch (SocketException e) {
            throw new SipException("Error: Unable to get local IP, are you online?", e);
        } catch (InvalidArgumentException e) {
            throw new SipException("Error: could not open port for new IP address", e);
        } catch (ParseException e) {
            throw new SipException("Error: this shouldn't ever happen! (?) could not create local contact header", e);
        }
    }

    public synchronized void reset(Context context, String username, String server, String password, TextListener listener) throws SipException {
        this.parent = context;
        this.username = username;
        this.server = server;
        this.password = password;
        resetLocalIP();
        Address newGlobalSipAddress = null;
        Address localSipAddress = null;
        try {
            openListeningPoint();
            newGlobalSipAddress = addressFactory.createAddress("sip:" + username + "@" + server);
            localSipAddress = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + listeningPoint.getPort());
            localContactHeader = headerFactory.createContactHeader(localSipAddress);
            sipProvider.addSipListener(this);
            sipProvider.addListeningPoint(listeningPoint);
            parent.registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (ParseException e) {
            throw new SipException("Error: could not parse new account parameters");
        } catch (TooManyListenersException e) {
            e.printStackTrace();
            throw new SipException("Error: too many listeners, must not have unregistered with the sipProvider via close()", e);
        }
        globalSipAddress = newGlobalSipAddress;
        messageReceivers = new ArrayList<TextListener>();
        addTextReceiver(listener);
    }

    /**
     * Get a handle to the single global SipClient
     * Precondition: init() must be called first at least once
     * @return a handle to the single global SipClient
     */
    public static SipClient getInstance() throws IllegalStateException {
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

    public String getLocalIP() {
        return localIP;
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
    public synchronized void addTextReceiver(TextListener newReceiver) {
        synchronized (messageReceivers) {
            if (messageReceivers.contains(newReceiver))
                return;
            messageReceivers.add(newReceiver);
        }
        notify();
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
        synchronized (messageReceivers) {
            if (messageReceivers.contains(receiver))
                messageReceivers.remove(receiver);
        }
    }

    /**
     * register a listener as interested in receiving session-related notifications
     * @param newListener the new listener. If it already registered, nothing happens
     */
    public void addSessionListener(SessionListener newListener) {
        synchronized (sessionReceivers) {
            if (sessionReceivers.contains(newListener))
                return;
            sessionReceivers.add(newListener);
            sessionReceivers.notify();
        }
    }

    /**
     * remove a listener from receiving session notifications
     * @param existingListener the listener to remove
     */
    public void removeSessionListener(SessionListener existingListener) {
        synchronized (sessionReceivers) {
            if (sessionReceivers.contains(existingListener))
                sessionReceivers.remove(existingListener);
        }
    }
    private void sendControlMessage(String message) {
        synchronized (messageReceivers) {
            for (TextListener listener : messageReceivers) {
                listener.controlMessageReceived(message);
            }
        }
    }

    /**
     * Send some real-time text within the current call. A pretty important capability...
     * @param add the characters to send in the RTT session
     * @throws IllegalStateException if not connected on a call
     */
    public void sendRTTChars(String add) throws IllegalStateException {
        if (!onACallNow())
            throw new IllegalStateException("no call connected, cannot send chars");
        currentCall.sendText(add);
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
            Thread.sleep(500);
        } catch (InterruptedException e) {}

        for (SessionListener listener : sessionReceivers) {
            listener.SessionClosed();
        }
    }
    private void notifySessionDisconncted(String reason) {
        for (SessionListener listener : sessionReceivers) {
            listener.SessionDisconnected(reason);
        }
    }


    private void notifySessionEstablished() {
        //Log.d(TAG, "Notifying that call is connected");
        try {
            synchronized (sessionReceivers) {
                sessionReceivers.wait(500); // make sure call activity has time to register itself as a listener
            }
        } catch (InterruptedException e) {}
        for (SessionListener listener : sessionReceivers) {
            //Log.d(TAG, "Notifying a listener that call is connected");
            SipURI uri = (SipURI)currentCall.getOtherParty().getURI();
            listener.SessionEstablished(uri.getUser() + "@" + uri.getHost());
        }
    }

    /**
     * tell the SipClient to register with the SIP server, given the credentials it currently has
     * on hand.
     */
    public void register() throws SipException {
        doRegister(DEFAULT_REGISTRATION_LEN, null);
    }

    /**
     * The actual logic of registering, which can be called internally
     * @param registrationLength the time in seconds to request registration with the server
     * @param extraHeader another header to add to the REGISTER request, if desired
     */
    private void doRegister(int registrationLength, Header extraHeader) throws SipException  {
        int tag = randomGen.nextInt();
        URI requestURI = globalSipAddress.getURI();

        try {
            resetLocalIP(); // every time we register, we should probably make sure we're telling the server the right IP address to reach us...
            ArrayList<ViaHeader> viaHeaders = createViaHeaders();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "REGISTER");
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            if (registrationLength == 0) {
                cSeqHeader.setSeqNumber(2L);
                callIdHeader.setCallId(registrationID);
            } else
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
            Log.d(TAG, "Sending stateful registration " /*+ globalSipAddress*/);
            SipRequester requester = new SipRequester(sipProvider);
            requester.execute(request);
            String result = requester.get();
            if (result == null) {
                sendControlMessage("Failed to send registration request. Check logcat");
                sendControlMessage("Not registered.");
            } else if (result.equals("Success")) {
                // get() waits on the other thread that is sending the request
                // doing this in another thread doesn't seem to make sense if we are waiting on get()
                // maybe disable Android's strict mode to allow network on main thread?
                //sendControlMessage("Sent registration request");
                registrationPending = true;
            } else {
                Log.e(TAG, "Failed to register: " + result);
                sendControlMessage(result);
                sendControlMessage("Not registered.");
            }
        } catch (SipException e) {
            sendControlMessage("Failed to send registration request. Trouble finding own IP");
        } catch (Exception e) {
            sendControlMessage("Failed to send registration request. Check logcat");
            sendControlMessage("Not registered.");
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

    /**
     * Tell the SipClient to remove its registration with the server, e.g. if the app is quitting
     */
    public void unregister() throws SipException {
        try {
            Log.d(TAG, "re-registering for time 0");
            doRegister(0, null);
        } catch (Exception e) {
            // TODO handle this
            e.printStackTrace();
        }
    }

    /**
     * In case close() is not called properly by the rest of the app
     */
    @Override
    public void finalize() {
        close();
    }

    /**
     * This must be called when the SipClient is done and will not be used again, to close ports
     * and delete broadcast receivers.
     */
    public void close() {
        try {
            sipProvider.removeListeningPoint(listeningPoint);
            sipProvider.removeSipListener(this);
            sipStack.deleteListeningPoint(listeningPoint);
            listeningPoint = null;
            Log.d(TAG, "deleted listening point");
            parent.unregisterReceiver(connectivityReceiver);
        } catch (ObjectInUseException e) {
            // TODO handle this
            e.printStackTrace();
        } /*catch (IllegalArgumentException e) {
            // TODO why?!
            Log.e(TAG, "connectivityReceiver not registered?!");
            e.printStackTrace();
        }*/
    }

    /**
     * Initiate a new call to the given SIP URI.
     * @param URI the other party's SIP address
     * @throws SipException if the request couldn't be sent for some other reason
     * @throws ParseException if the URI can't be parsed, or the contact's server is not valid
     * @throws TransactionUnavailableException if already on a call, or if another error occurs
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws InvalidArgumentException
     */
    public void call(String URI) throws SipException, ParseException, TransactionUnavailableException, InterruptedException, ExecutionException, InvalidArgumentException {
        boolean available = callLock.tryAcquire();
        if (!available)
            throw new TransactionUnavailableException("Can't call now -- already modifying call state");
        if (currentCall != null) {
            callLock.release();
            if (BuildConfig.DEBUG) Log.d(TAG, "555555555 should be 1:" + callLock.availablePermits());
            throw new TransactionUnavailableException("Can't call now -- already on a call");
        }

        try {
            Address contact = addressFactory.createAddress("sip:" + URI);
            int tag = randomGen.nextInt();
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
            request = (Request) SDPBuilder.addSDPContentAndHeader(request, 0, 0, port + 1);
            StrictMode.ThreadPolicy tp0 = StrictMode.getThreadPolicy();
            ClientTransaction transaction;
            try {
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
                transaction = sipProvider.getNewClientTransaction(request);
            } catch (TransactionUnavailableException e) {
                throw new ParseException("couldn't find contact's server", 0);
            } finally {
                StrictMode.setThreadPolicy(tp0);
            }
            SipTransactionRequester requester = new SipTransactionRequester(sipProvider);
            requester.execute(transaction);
            currentCall = new RTTCall(request, null, messageReceivers);
            currentCall.addInviteTransaction(transaction);
            currentCall.setCalling();
            String result = requester.get();
            if (result.equals("Success")) {
                // get() waits on the other thread that is sending the request
                // doing this in another thread doesn't seem to make sense if we are waiting on get()
                // maybe disable Android's strict mode to allow network on main thread?
                sendControlMessage("Sent INVITE request");
            } else {
                throw new SipException(result);
            }
        } catch (Exception e) {
            callLock.release();
            if (currentCall != null)
                currentCall.end();
            currentCall = null;
            if (BuildConfig.DEBUG) Log.d(TAG, "777777777777 should be 1:" + callLock.availablePermits());
            e.printStackTrace();
            if (e instanceof ParseException || e instanceof TransactionUnavailableException)
                throw e;
            throw new SipException("couldn't send request; ", e);
        }
    }

    private boolean onACallNow() {
        return currentCall != null;
    }


    /**
     * SipListener interface callback.
     * This method dispatches the incoming request to a helper depending on the SIP method
     */
    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        Log.d(TAG, "received a request: " + request.getMethod());
        Log.d(TAG, request.toString().substring(0, 100));
        if (!isForUs(requestEvent)) {
            Log.d(TAG, "not for us, ignoring");
            return;
        }
        switch (request.getMethod()) {
            case Request.OPTIONS:
                sendOptions(requestEvent);
                break;
            case Request.INVITE:
                receiveCall(requestEvent);
                break;
            case Request.ACK:
                if (currentCall != null && currentCall.isRinging())
                    beginCall(requestEvent);
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

    /**
     * Tell the other layers that the incoming call is now connected, thanks to receiving the ACK
     *
     * See the class description above for a detailed explanation of the incoming call sequence,
     * including this weird lock handling.
     * @param requestEvent the ACK event
     */
    private void beginCall(RequestEvent requestEvent) {
        Request originalInvite = currentCall.getCreationEvent().getRequest();
        int suggestedT140Map = SDPBuilder.getT140MapNum(originalInvite, SDPBuilder.mediaType.T140);
        int suggestedT140RedMap = SDPBuilder.getT140MapNum(originalInvite, SDPBuilder.mediaType.T140RED);
        try {
            currentCall.accept(SDPBuilder.getRemoteIP(originalInvite), SDPBuilder.getT140PortNum(originalInvite), port+1, suggestedT140Map, suggestedT140RedMap);
            currentCall.addDialog(requestEvent.getDialog());
            synchronized (this) {
                try {
                    wait(1000); // wait for addTextReceiver() since a new UI Activity is spawning
                } catch (InterruptedException e) {}
            }
            currentCall.resetMessageReceivers(messageReceivers);
            notifySessionEstablished();
        } catch (RtpException e) {
            Log.d(TAG, "call failed");
            currentCall = null;
            notifySessionFailed("couldn't establish RTP session");
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
     * This is a public method for an upper layer class to notify the SipClient that it wants to end
     * the call.
     * Precondition: currently connected on or creating a call
     */
    public void hangUp() {
        if (onACallNow()) {
            if (currentCall.isCalling()) {
                sendCancel();
                callLock.release();
                if (BuildConfig.DEBUG) Log.d(TAG, "88888888888 should be 1:" + callLock.availablePermits());
            }
            else if (currentCall.isConnected())
                sendBye(currentCall.getDialog());
            else
                Log.d(TAG, "Unsure what is being hung up");
        }
        terminateCall();
    }

    /**
     * The CallReceiver (likely on an upper layer) <em>must</em> call either this or declineCall()
     * when an incoming call is ringing. This begins the call.
     * Precondition: a CallReceiver has been notified that a call is coming in
     * @throws IllegalStateException if there is no call waiting to be accepted (possibly because it already hung up),
     *      or if we are otherwise not in the midst of setting up a new call
     */
    /*  Precondition: callLock is locked, and currentCall has a copy of the incoming request event.
     *  If a call is truly coming in, these will be satisfied.
     * See the class description above for a detailed explanation of the incoming call sequence,
     * including this weird lock handling.
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
            int suggestedT140Map = SDPBuilder.getT140MapNum(request, SDPBuilder.mediaType.T140);
            int suggestedT140RedMap = SDPBuilder.getT140MapNum(request, SDPBuilder.mediaType.T140RED);
            response.addHeader(localContactHeader);
            response = (Response)SDPBuilder.addSDPContentAndHeader(response, suggestedT140Map, suggestedT140RedMap, port+1);
            if (request.getHeader("Accept") != null) {
                // TODO send the message body that this request is demanding
            }
            SipResponder responder = new SipResponder(sipProvider, incomingEvent, currentCall.getInviteTransaction());
            responder.execute(response);
        } catch (Exception e) {
            // again, this is a lot of exceptions to catch all at once. oh well...
            if (BuildConfig.DEBUG) e.printStackTrace();
            currentCall.end();
            currentCall = null;
            notifySessionFailed("couldn't establish call");
        }
        callLock.release();
        if (BuildConfig.DEBUG) Log.d(TAG, "9999999999 should be 1:" + callLock.availablePermits());
    }


    private String getContactIP(Message msg) {
        ContactHeader contact = (ContactHeader)msg.getHeader("Contact");
        AddressImpl address = (AddressImpl)contact.getAddress(); // this cast is correct for NIST implementation
        return address.getHost();
    }

    /*  Precondition: callLock is locked, and currentCall has a copy of the incoming request event.
        If a call is truly coming in, these will be satisfied.
     */

    /**
     * The CallReceiver (likely on an upper layer) <em>must</em> call either this or acceptCall()
     * when an incoming call is ringing. This ignores the call.
     * @throws IllegalStateException if there is no call waiting to be accepted (possibly because it already hung up),
     *      or if we are otherwise not in the midst of setting up a new call
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
        if (BuildConfig.DEBUG) Log.d(TAG, "154254124512 should be 1:" + callLock.availablePermits());
    }

    /**
     * Handle an incoming INVITE request and create a new call if one is not ongoing.
     *
     * See the class description above for a detailed explanation of the incoming call sequence,
     * including this weird lock handling.
     * @param requestEvent the INVITE event
     */
    private void receiveCall(RequestEvent requestEvent) {
        boolean lockAvailable = callLock.tryAcquire();
        if (lockAvailable && !onACallNow()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "just acquired the lock - permits remaining: " + callLock.availablePermits());
            Log.d(TAG, "we're available...");
            if (callReceiver != null) {
                Log.d(TAG, "asking receiver to accept...");
                ServerTransaction transaction = respondGeneric(requestEvent, null, Response.RINGING);
                currentCall = new RTTCall(requestEvent, transaction, messageReceivers);
                currentCall.setRinging();
                callReceiver.callReceived(currentCall.getOtherParty().getURI().toString());
            } else {
                // TODO respond 4xx
                Log.e(TAG, "uh oh, we're releasing this lock...why?");
                callLock.release();
                if (BuildConfig.DEBUG) Log.d(TAG, "9807896778956789 should be 1:" + callLock.availablePermits());
            }
        } else {
            if (lockAvailable) {
                callLock.release(); // we just acquired this, but can't use it after all
                if (BuildConfig.DEBUG) Log.d(TAG, "545456767887 should be 1:" + callLock.availablePermits());
            }
            RTTCall latestCall = new RTTCall(requestEvent, null, messageReceivers);
            if (currentCall.equals(latestCall)) {
                // asterisk sends many duplicate invites
                Log.d(TAG, "ignoring a duplicate INVITE");
                return;
            }
            respondGeneric(requestEvent, null, Response.BUSY_HERE);
        }
    }

    /**
     * Is this incoming request even for us, according to its To: header? Or is it just some mass
     * scan that is checking us out and should be ignored?
     * @param requestEvent incoming request
     * @return definitely returns true if we know this is for us. Definitively returns false
     * if we can tell the call is for somebody else. If we can't tell due to some error, also returns
     * false
     */
    private boolean isForUs(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ToHeader toHeader = (ToHeader)request.getHeader("To");
        if (toHeader == null)
            return false; // should actually return a 400 here, but whatever, just ignore it
        Address to = toHeader.getAddress();
        try {
            Address us = addressFactory.createAddress("sip:" + username + "@" + server);
            if (to.equals(us))
                return true;
            us = addressFactory.createAddress("sip:" + username + "@" + localIP);
            if (to.equals(us))
                return true;
            us = addressFactory.createAddress("sip:" + username + "@" + localIP + ":" + port);
            if (to.equals(us))
                return true;
        } catch (ParseException e) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Unable to check address - assuming it is not for us");
        }
        return false;
    }


    private void cancelCall(RequestEvent requestEvent) {
        if (currentCall.isRinging() && cancelApplies(requestEvent, currentCall)) {
            RequestEvent initialINVITE = currentCall.getCreationEvent();
            respondGeneric(initialINVITE, initialINVITE.getServerTransaction(), Response.REQUEST_TERMINATED);
            notifySessionFailed("Caller hung up/cancelled call");
            callLock.release();
            if (BuildConfig.DEBUG) Log.d(TAG, "1111111111 should be 1:" + callLock.availablePermits());
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
        // TODO make sure this is actually appropriate/for us and not a random stray request!

        Request request = requestEvent.getRequest();
        respondGeneric(requestEvent, null, Response.OK);
        notifySessionClosed();
        terminateCall();
    }

    /**
     * This method handles the nitty-gritty of ending a call in progress, no matter how the decision
     * was made to do so (BYE, user hanging up, etc).
     */
    private void terminateCall() {
        callLock.acquireUninterruptibly();
        if (onACallNow()) {
            // multiple calls to terminateCall() may occur in quick succession due to duplicate BYEs
            // therefore we must safely check that there is still a call to end
            currentCall.end();
            currentCall = null;
        }
        callLock.release();
        if (BuildConfig.DEBUG) Log.d(TAG, "2222222222 should be 1:" + callLock.availablePermits());
    }

    /**
     * SipListener interface callback.
     * This dispatches the incoming SIP response to a helper depending on the response code
     */
    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int responseCode = response.getStatusCode();
        Log.d(TAG, "received a response: " + responseCode);
        if (responseCode >= 300) {
            if (responseCode != Response.UNAUTHORIZED && responseCode != Response.PROXY_AUTHENTICATION_REQUIRED)
                sendControlMessage("SIP error: " + responseCode);
            handleFailure(responseEvent);
        } else if (responseCode >= 200) {
            //sendControlMessage("SIP OK");
            handleSuccess(responseEvent);
        } else {
            if (responseCode == Response.RINGING && currentCall != null && currentCall.isCalling()) {
                sendControlMessage("Ringing...");
            }
            // maybe not do anything else for 1xx?
        }
    }

    /* Precondition: if responseEvent is in response to an INVITE,
                     callLock was locked when initiating the call */
    private void handleFailure(ResponseEvent responseEvent) {
        // TODO make sure this response is actually for us, and not some random stray thing!
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
                case Response.REQUEST_TIMEOUT:
                    notifySessionFailed("callee didn't answer");
                    break;
                case Response.TEMPORARILY_UNAVAILABLE:
                    notifySessionFailed("callee not reachable");
                    break;
                case Response.REQUEST_TERMINATED:
                    break; // we probably initiated this on our end
                default:
                    notifySessionFailed("INVITE failed");
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
        ClientTransaction origTransaction = responseEvent.getClientTransaction();
        AccountManagerImpl manager = new AccountManagerImpl();
        SipStackExt stack = (SipStackExt)sipStack; // this cast is legal, but sketchy, we need v2.0 of NIST JAIN SIP
        AuthenticationHelper authenticator = stack.getAuthenticationHelper(manager, headerFactory);
        try {
            ClientTransaction transaction = authenticator.handleChallenge(responseEvent.getResponse(), origTransaction, sipProvider, 10);
            if (isInviteResponse(responseEvent))
                currentCall.addInviteTransaction(transaction);
            SipTransactionRequester requester = new SipTransactionRequester(sipProvider);
            Log.d(TAG, "Sending credentials in response to challenge");
            requester.execute(transaction);
            Log.d(TAG, "Sent credentials in response to challenge");
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
        else if (isRegisterResponse(responseEvent)) {
            sendControlMessage("Registered");
            registrationPending = false;
            setRegistrationTimer(response);
        } else
            Log.d(TAG, "need to implement handling other successes");
    }

    /**
     *
     * @param response the 200 response to the REGISTER
     */
    private void setRegistrationTimer(Response response) {
        registrationTimer.cancel();
        registrationTimer = new Timer();
        ExpiresHeader expires = response.getExpires();
        int registrationLen = expires.getExpires() * 1000;
        registrationTimer.schedule(new RegistrationTimerTask(this), registrationLen);
    }

    /*
        Preconditions:  -callLock is locked when the call was initiated
                        -currentCall is not null and is waiting for a response to an outgoing call
     */
    private void handleInviteSuccess(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        Dialog dialog = responseEvent.getDialog();

        try {
            ACKResponse(response, dialog);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "call failed");
            e.printStackTrace();
            currentCall.end();
            currentCall = null;
            notifySessionFailed("couldn't establish call");
            return;
        }

        currentCall.addDialog(dialog);
        if (sessionIsAcceptable(response)) {
            Log.d(TAG, "acceptable!");
            int agreedT140MapNum = SDPBuilder.getT140MapNum(response, SDPBuilder.mediaType.T140);
            int agreedT140RedMapNum = SDPBuilder.getT140MapNum(response, SDPBuilder.mediaType.T140RED);
            try {
                currentCall.callAccepted(SDPBuilder.getRemoteIP(response), SDPBuilder.getT140PortNum(response), port + 1, agreedT140MapNum, agreedT140RedMapNum);
                notifySessionEstablished();
            } catch (RtpException e) {
                if (BuildConfig.DEBUG) Log.d(TAG, "call failed");
                currentCall.end();
                currentCall = null;
                notifySessionFailed("couldn't establish RTP session");
            }
        } else {
            Log.d(TAG, "not acceptable");
            sendBye(dialog);
            currentCall.end();
            currentCall = null;
            notifySessionFailed("other party doesn't support RTT");
        }
        callLock.release();
        if (BuildConfig.DEBUG) Log.d(TAG, "44444444444 should be 1:" + callLock.availablePermits());
    }

    private void ACKResponse(Response response, Dialog dialog) throws Exception {
        CSeqHeader cseq = (CSeqHeader)response.getHeader("CSeq");
        long seqNo = cseq.getSeqNumber();
        AckSender acknowledger = new AckSender(dialog);
        acknowledger.execute(seqNo);
        String result = acknowledger.get();
        if (result == null || !result.equals("Success"))
            throw new SipException("failed to send ACK, call not started");
    }

    private boolean sessionIsAcceptable(Response response) {
        /* this is imprecise because we are not looking specifically
        at the ContentTypeHeader and are not looking specifically inside the media
        descriptions and attributes in the SDP ... but it works for now */
        ListIterator<Header> list = response.getHeaders("Content-Type");
        while (list.hasNext()) {
            Header header = list.next();
            if (header.toString().contains("application/sdp")) {
                 byte content[] = response.getRawContent();
                String strContent = new String(content, StandardCharsets.UTF_8);
                if (strContent.toLowerCase().contains("t140/1000")) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "It's ACCEPTABLE!!!");
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
        Log.d(TAG, "sending CANCEL");
        ClientTransaction transaction = currentCall.getInviteClientTransaction();
        try {
            Request cancelRequest = transaction.createCancel();
            ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(cancelRequest);
            SipTransactionRequester requester = new SipTransactionRequester(sipProvider);
            requester.execute(cancelTransaction);
        } catch (SipException e) {
            sendControlMessage("Failed to send cancel! Check logcat!");
            e.printStackTrace();
        }

    }

    /**
     * RegistrationTimerTask interface callback
     */
    public void registrationExpired() {
        try {
            register();
        } catch (SipException e) {
            // TODO handle this
            e.printStackTrace();
        }
    }

    /**
     * SipListener interface callback
     */
    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        // TODO: use timeoutEvent methods to find out the actual cause of this
        // probably need to save all the transactions in a hash table or something
        // so we know what transactions are outstanding
        if (registrationPending) {
            Log.e(TAG, "Registration apparently timed out?");
            sendControlMessage("Registration timed out");
            registrationPending = false;
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "received a Timeout message");
        }
    }

    /**
     * SipListener interface callback
     */
    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
       /* if (registrationPending) {
            Log.e(TAG, "failed to send registration due to: " + ioExceptionEvent.getSource());
            sendControlMessage("Sending registration to " + ioExceptionEvent.getHost() + ":" +
                    ioExceptionEvent.getPort() + " failed, check logcat");
            registrationPending = false;
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "received a IOException message: " + ioExceptionEvent.toString());
        }*/
        // not sure if i should actually do anything here, since there is NO WAY to tell what really caused this exception
    }

    /**
     * SipListener interface callback
     */
    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "received a TransactionTerminated message: " + transactionTerminatedEvent.toString());
    }

    /**
     * SipListener interface callback
     */
    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "received a DialogTerminated message");
        // TODO should I have used this to detect call ending? no, probably not
    }

    /**
     * ConnectivityReceiver.IPChangeListener interface callback
     */
    @Override
    public void IPAddrChanged() {
        try {
            String oldIP = localIP;
            if (BuildConfig.DEBUG) Log.d(TAG, "Resetting IP due to connectivity change");
            if (BuildConfig.DEBUG) Log.d(TAG, "current IP: " + localIP);
            resetLocalIP();
            if (BuildConfig.DEBUG) Log.d(TAG, "new IP: " + localIP);
            if (oldIP.equals(localIP)) {
                // on at least one device, this appears to be called before the new IP is truly set
                // so, here is a bad concurrency hack for Motorola's problem, not mine!
                // wait a bit, then try again just to make sure it is reset when it needs to be
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {}
                resetLocalIP();
            }
            if (!oldIP.equals(localIP)) {
                // IPChangeListener calling IPAddrChanged does not actually mean it changed
                sendControlMessage("Network changed, reregistering");
                register();
                if (onACallNow()) {
                    hangUp();
                    notifySessionDisconncted("IP address changed, call lost");
                }
            }
        } catch (SipException e) {
            sendControlMessage("Error: troubling re-finding own IP ... connection possibly lost");
        }
    }

    // these nested classes are used to fire one-off threads and then die
    private class AckSender extends AsyncTask<Long, String, String> {
        private Dialog dialog;
        public AckSender(Dialog dialog) {
            this.dialog = dialog;
        }

        @Override
        protected String doInBackground(Long... params) {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                Request ack = dialog.createAck(params[0]);
                dialog.sendAck(ack);
                return "Success";
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
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
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
