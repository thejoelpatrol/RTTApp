package com.laserscorpion.rttapp;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.address.Address;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.Request;
import android.util.Log;

import com.laserscorpion.rttapp.sip.SipClient;
import com.laserscorpion.rttapp.sip.TextListener;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;

import gov.nist.jrtp.RtpErrorEvent;
import gov.nist.jrtp.RtpException;
import gov.nist.jrtp.RtpListener;
import gov.nist.jrtp.RtpManager;
import gov.nist.jrtp.RtpPacket;
import gov.nist.jrtp.RtpPacketEvent;
import gov.nist.jrtp.RtpSession;
import gov.nist.jrtp.RtpStatusEvent;
import gov.nist.jrtp.RtpTimeoutEvent;

import se.omnitor.protocol.rtp.RtpTextReceiver;
import se.omnitor.protocol.rtp.RtpTextTransmitter;
import se.omnitor.protocol.rtp.packets.RTPPacket;
import se.omnitor.protocol.rtp.text.SyncBuffer;
import se.omnitor.util.*;

/**
 * RTTcall is one of the core classes, establishing the RTP session and interfacing with Omnitor's
 * RFC 4103 implementation. It communicates in both directions with the layers above and below, amd
 * coordinates the two RTP libraries underneath (JRTP and Omnitor t140).  It receives information
 * from the SIP layer above about how to set up the call, and then initiates the RTP session.
 * It then continuously receives text input from above, which it places into the Omnitor buffer, to
 * be sent by the lower layer. It also listens for incoming RT text from Omnitor's library, and
 * passes it up to any TextListeners that are registered with it (this may be something of a layer
 * violation, though TextListener is within the SIP package).
 *
 * <code>
 * <br>    --------------
 * <br>    |&nbsp; UI layer&nbsp;&nbsp;|
 * <br>    --------------
 * <br>    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|
 * <br>    --------------
 * <br>    | SIP layer &nbsp;| &nbsp;(SipClient)
 * <br>    --------------
 * <br>    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|
 * <br>    <strong>--------------
 * <br>    | Call layer | &nbsp;(RTTCall)
 * <br>    --------------</strong>
 * <br>    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;|
 * <br>    --------------
 * <br>    | RTP layer &nbsp;| &nbsp;(JRTP, Omnitor t140)
 * <br>    --------------
 * </code>
 * <br>
 * RTTCall stores the state of one call at a time, which can be calling (outgoing),
 * ringing (incoming), connected, or ended.  A new one should be created for each logical
 * call, since each one needs its own new RTP session. Since the state transitions are caused by
 * user interaction or SIP messages, the upper layer needs to tell RTTCall when to change state, by
 * calling methods such as setRinging(). State changes span layers to some extent, so the RTTCall
 * stores some SIP requests and events involved in its own creation, for later reference when the
 * SIP layer is sending messages within dialogs or transactions. The upper layer may therefore
 * need to call such methods as getInviteTransaction() or getCreationEvent().
 */
public class RTTCall {
    private static final String TAG = "RTTCall";
    private static final int RFC4103_BUFFER_TIME = 300;
    //private static final int TEXT_BUFFER_DELAY_MS = RFC4103_BUFFER_TIME; // too slow, it's only RECOMMENDED anyway
    private static final int TEXT_BUFFER_DELAY_MS = 50;
    private static final int REDUNDANT_TEXT_GENERATIONS = 3;
    private SipClient sipClient;
    private Dialog dialog;
    private Request creationRequest;
    private ClientTransaction inviteClientTransaction;

    private RequestEvent incomingRequest;
    private ServerTransaction inviteTransaction;

    private Semaphore destructionLock;
    private boolean ringing = false;
    private boolean connected = false;
    private boolean calling = false;


    private int localPort;
    private int remotePort;
    private String remoteIP;

    private RtpManager manager;
    private RtpSession session;
    private FifoBuffer recvBuf;
    private SyncBuffer outgoingBuf;
    private ReceiveThread recvThread;
    private TextPrintThread printThread;
    private RtpTextTransmitter transmitter;
    private int t140PayloadNum;
    private int t140RedPayloadNum;
    private Address otherParty;
    private List<TextListener> messageReceivers;

    /**
     * Use this constructor for an incoming call. The requestEvent is the incoming INVITE.
     * The transaction is the ServerTransaction used to respond to the INVITE,
     * for both 180 Ringing and the final response. If planning to send 180 Ringing,
     * it must be sent already, so the ServerTransaction can be used here.
     * @param requestEvent the incoming INVITE event
     * @param transaction used to send 180 Ringing, and the final response. null if no 180 has been
     *                    sent yet and therefore no transaction is used yet. In that case, only
     *                    one response can be sent,
     * @param messageReceivers the TextListeners that need to be notified when there is incoming text
     */
    public RTTCall(RequestEvent requestEvent, ServerTransaction transaction, List<TextListener> messageReceivers) {
        this(requestEvent.getRequest(), requestEvent.getDialog(), messageReceivers);
        incomingRequest = requestEvent;
        inviteTransaction = transaction;
        FromHeader contact = (FromHeader)requestEvent.getRequest().getHeader("From");
        otherParty = contact.getAddress();
    }

    /**
     * Use this constructor for an outgoing call. Ideally you will pass in the dialog that is
     * created for the call, but this may not be available yet,
     * so you will need to call addDialog() in that case.
     * @param creationRequest the INVITE Request sent to the other party to initiate the call
     * @param dialog the dialog that is already created for the call is available, or null if none yet
     * @param messageReceivers the TextListeners that need to be notified when there is incoming text
     */
    public RTTCall(Request creationRequest, Dialog dialog, List<TextListener> messageReceivers) {
        this.creationRequest = creationRequest;
        this.dialog = dialog;
        ToHeader contact = (ToHeader)creationRequest.getHeader("To");
        otherParty = contact.getAddress();
        sipClient = SipClient.getInstance();
        destructionLock = new Semaphore(1);
        recvBuf = new FifoBuffer();
        this.messageReceivers = messageReceivers;
        printThread = new TextPrintThread(this, recvBuf);
        printThread.start();
        try {
            manager = new RtpManager(sipClient.getLocalIP());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the ServerTransaction from the original INVITE dialog of an incoming call.
     * This is useful when the transaction needs to be used in responding to the INVITE,
     * which will likely happen well after it is initially received.
     * @return the transaction used to respond to the original INVITE, or null if none
     */
    public ServerTransaction getInviteTransaction() {
        return inviteTransaction;
    }

    /**
     * Get the ClientTransaction from the original INVITE dialog of an outgoing call.
     * This is useful when the transaction needs to be used again in cancelling the INVITE.
     * @return the transaction used to send the original INVITE, or null if none
     */
    public ClientTransaction getInviteClientTransaction() {
        return inviteClientTransaction;
    }


    /**
     * Use this to change the Dialog associated with the call, especially if one was not
     * available at creation of the RTTCall.
     * @param dialog the new Dialog to associate with the call,
     */
    public synchronized void addDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    /**
     * Use this to change the ClientTransaction that was used to send the original INVITE request
     * e.g. when sending that INVITE, or when sending another INVITE after authenticating
     * @param transaction the new ClientTransaction to associate with the call
     */
    public synchronized void addInviteTransaction(ClientTransaction transaction) {
        this.inviteClientTransaction = transaction;
    }

    /**
     * Replace the existing list of TextListeners with the new one, if the list of current activities
     * changes.
     * @param messageReceivers the new TextListeners
     */
    public synchronized void resetMessageReceivers(List<TextListener> messageReceivers) {
       this.messageReceivers = messageReceivers;
    }

    /**
     * This method returns the incoming RequestEvent that created the call, if any. This is useful
     * in connecting the call. We need to store that event somewhere, since the call completion can
     * only come after user interaction, which will take quite a while after the SIP event is received.
     * Precondition: call was created by an incoming RequestEvent. If call
     * was created by an outgoing request, will return null.
     * @return the incoming event that created the call, or null
     */
    public RequestEvent getCreationEvent() {
        return incomingRequest;
    }

    /**
     * This method returns the outgoing Request that created the call, if any. This
     * Precondition: call was created by an outgoing Request. If call
     * was created by an incoming request, will return null.
     * @return the outgoing Request that was sent to the other party when the call was created, if any
     */
    public Request getCreationRequest() {
        return creationRequest;
    }

    /**
     * Get the dialog for an ongoing connected call, so as to send more messages in that dialog,
     * for example when sending BYE.
     * @return the dialog for the established call
     */
    public Dialog getDialog() {
        return dialog;
    }

    /**
     * This is not a true equals() override, since it does not compare
     * arbitrary objects. It can only be called to check equality of
     * RTTCalls, which is based on the requests that create them.
     */
    public boolean equals(RTTCall otherCall) {
        Request newRequest = otherCall.getCreationRequest();
        return newRequest.equals(creationRequest);
    }

    /**
     * Set the call to the calling state, when the SIP INVITE has been sent and before the other
     * party has sent a final response.
     */
    public synchronized void setCalling() {
        calling = true;
    }

    /**
     * Set the call to the ringing state after the incoming INVITE is received but before the user
     * has accepted or declined it.
     */
    public synchronized void setRinging() {
        ringing = true;
    }

    /**
     * Connect an incoming call that is currently ringing. Call this method when the SIP interactions
     * indicate that the other party has accepted the call and is ready to receive text. Pass in the
     * RTP parameters agreed to in the SDP interactions.
     * Precondition: this call is currently in the ringing state
     * Postcondition: this call is now in the connected state, and the RTP stream is set up
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @param t140MapNum the RTP payload map number corresponding to t140 in the agreed session description
     * @param t140RedMapNum must be &lt;= 0 if not using redundancy! This is the RTP payload map number corresponding to
     *                      "red", the redundant media type, in the agreed session description
     * @throws IllegalStateException if no call is currently ringing
     * @throws RtpException if the call can't be connected
     */
    public void accept(String remoteIP, int remotePort, int localRTPPort, int t140MapNum, int t140RedMapNum) throws IllegalStateException, RtpException {
        if (!ringing)
            throw new IllegalStateException("call is not ringing - cannot accept");
        connectCall(remoteIP, remotePort, localRTPPort, t140MapNum, t140RedMapNum);
    }

    /**
     * Connect an outgoing call has been accepted by the remote party, to set up the RTP
     * session according to the agreed parameters.
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @param t140MapNum the RTP payload map number corresponding to t140 in the agreed session description
     * @param t140RedMapNum must be &lt;= 0 if not using redundancy! This is the RTP payload map number corresponding to
     *                      "red", the redundant media type, in the agreed session description
     * @throws IllegalStateException if no call is currently outgoing
     * @throws RtpException if the call can't be connected
     */
    public void callAccepted(String remoteIP, int remotePort, int localRTPPort, int t140MapNum, int t140RedMapNum) throws IllegalStateException, RtpException {
        if (!calling)
            throw new IllegalStateException("not calling anyone - what was accepted?");
        connectCall(remoteIP, remotePort, localRTPPort, t140MapNum, t140RedMapNum);
    }

    private synchronized void connectCall(String remoteIP, int remotePort, int localRTPPort, int t140MapNum, int t140RedMapNum) throws RtpException {
        if (connected)
            throw new IllegalStateException("can't connect call -- already connected on a call");
        if (!ringing && !calling)
            throw new IllegalStateException("can't connect call -- no incoming or outgoing call pending");
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.localPort = localRTPPort;
        this.t140PayloadNum = t140MapNum;
        this.t140RedPayloadNum = t140RedMapNum;
        recvThread = new ReceiveThread(recvBuf); // this must be created only once t140PayloadNum and t140RedPayloadNum are set
        recvThread.start();
        boolean useRed = (t140RedMapNum > 0);
        int redGenerations = useRed  ? REDUNDANT_TEXT_GENERATIONS : 0;
        outgoingBuf = new SyncBuffer(redGenerations, TEXT_BUFFER_DELAY_MS);
        outgoingBuf.start();
        try {
            session = manager.createRtpSession(localRTPPort, remoteIP, remotePort);
            session.addRtpListener(recvThread);
            session.receiveRTPPackets();
            int payloadType = useRed ? t140RedMapNum : t140MapNum;
            transmitter = new RtpTextTransmitter(session, true, t140MapNum, useRed,
                                                    t140RedMapNum, redGenerations, outgoingBuf, false);
            transmitter.start();
        } catch (RtpException e) {
            e.printStackTrace();
            end();
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
            end();
            throw new RtpException(e.getMessage(), e);
        }
        connected = true;
        ringing = false;
        calling = false;

    }

    /**
     * The most important method: send real-time text in the connected call. Queued outgoing text
     * is sent after the buffering interval, so calling this method more often than that interval
     * will add text to the buffer. The buffering interval is short enough that a human does not
     * notice.
     * @param text the characters to send
     * @throws IllegalStateException if the call is not connected yet
     */
    public void sendText(String text) {
        if (!connected)
            throw new IllegalStateException("call is not connected, no one to send text to");
        byte[] t140Text = text.getBytes(StandardCharsets.UTF_8);
        outgoingBuf.setData(t140Text);
    }


    /**
     * End a call at any stage. Invoking multiple times has no effect; the
     * first invocation ends the session. This method must be called or else the RTP session will
     * remain open.
     */
    public synchronized void end() {
        if (destructionLock.tryAcquire()) {
            ringing = false;
            connected = false;
            calling = false;
            if (printThread != null)
                printThread.stopPrinting();
            if (recvThread != null)
                recvThread.stopReceiving();
            if (transmitter != null)
                transmitter.stop();
            if (session != null) {
                session.stopRtpPacketReceiver();
                session.shutDown();
            }
        } else
            return;
        /*  We do not release destructionLock.
            Once it is acquired by calling this method,
            future calls should do nothing.
         */
    }
    public boolean isRinging() {
        return ringing;
    }
    public boolean isConnected() {
        return connected;
    }
    public boolean isCalling() {
        return calling;
    }
    public Address getOtherParty() {
        return otherParty;
    }


    /**
     * This thread receives incoming RTP packets from JRTP's session,
     * repackages them into Omnitor's expected RTPPacket format, and
     * hands them over to a modified version of Omnitor's RtpTextReceiver,
     * which removes duplicates and extracts the text and puts it in the
     * FIFO buffer for PrintThread to read.
     *
     *
     */
    private class ReceiveThread extends Thread implements RtpListener {
        private boolean stop = false;

        private RtpTextReceiver textReceiver;

        public ReceiveThread(FifoBuffer buffer) {
            // RtpTextReceiver must be created only once t140PayloadNum and t140RedPayloadNum are set
            textReceiver = new RtpTextReceiver(localPort, (t140RedPayloadNum > 0), t140PayloadNum, t140RedPayloadNum, buffer);
        }

        /* synchronizing on the boolean stop is probably not necessary */
        public void stopReceiving() {
            stop = true;
            // now run() completes and the thread dies
        }

        @Override
        public void run() {
            while (!stop) {
                // please don't die on us, thread!
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { /* go back to sleep */ }
            }
        }

        @Override
        public void handleRtpPacketEvent(RtpPacketEvent rtpEvent) {
            RtpPacket packet = rtpEvent.getRtpPacket();
            RTPPacket convertedPacket = convertPacket(packet);
            textReceiver.handleRTPEvent(convertedPacket);
        }

        private RTPPacket convertPacket(RtpPacket incoming) {
            RTPPacket packet = new RTPPacket();
            packet.setCsrcCount(incoming.getCC());
            packet.setSequenceNumber(incoming.getSN());
            packet.setTimeStamp(incoming.getTS());
            packet.setSsrc(incoming.getSSRC());
            packet.setPayloadData(incoming.getPayload());
            packet.setMarker(incoming.getM() == 1 ? true : false);
            return packet;
        }

        @Override
        public void handleRtpStatusEvent(RtpStatusEvent rtpEvent) {
            Log.d(TAG, "!!! RTP STATUS!!");
        }

        @Override
        public void handleRtpTimeoutEvent(RtpTimeoutEvent rtpEvent) {
            Log.d(TAG, "!!! RTP TIMEOUT!!");
        }

        @Override
        public void handleRtpErrorEvent(RtpErrorEvent rtpEvent) {
            Log.e(TAG, "!!! RTP ERROR!!");
        }
    }


    /**
     * This thread is constantly waiting for ReceiveThread to add
     * some more text to the FIFO buffer, which it removes and prints
     * it to the UI class(es) that are waiting to display it.
     */
    private class TextPrintThread extends Thread {
        RTTCall parent;
        FifoBuffer buffer;
        boolean stop = false;

        public TextPrintThread(RTTCall parent, FifoBuffer buffer) {
            this.parent = parent;
            this.buffer = buffer;
        }

        public void stopPrinting() {
            stop = true;
            synchronized (buffer) {
                buffer.notifyAll();
            }
            // now run() completes and the thread dies
        }

        @Override
        public void run() {
            byte[] received;
            while (!stop) {
                try {
                    received = buffer.getData(); // this blocks until there is something in the fifo
                    if (received != null) {
                        synchronized (parent) {
                            synchronized (messageReceivers) {
                                for (TextListener receiver : messageReceivers) {
                                    String text = new String(received, StandardCharsets.UTF_8);
                                    receiver.RTTextReceived(text);
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {/* that's fine */}
            }
        }
    }
}
