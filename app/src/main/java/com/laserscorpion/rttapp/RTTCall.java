package com.laserscorpion.rttapp;

import android.javax.sip.Dialog;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.message.Request;
import android.util.Log;

import org.w3c.dom.Text;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
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
 * Created by joel on 2/12/16.
 */
public class RTTCall {
    private static final String TAG = "RTTCall";
    private static final int RFC4103_BUFFER_TIME = 300;
    private static final int REDUNDANT_TEXT_GENERATIONS = 3;
    private SipClient sipClient;
    private Dialog dialog;
    private Request creationRequest;

    private RequestEvent incomingRequest;
    private ServerTransaction inviteTransaction;


    private Semaphore creationLock;
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

    /**
     * Use this constructor for an incoming call - the requestEvent is the INVITE,
     * the transaction is the ServerTransaction used to respond to the INVITE,
     * for both 180 Ringing and the final response. If planning to send 180 Ringing,
     * it must be sent already, so the ServerTransaction can be used here.
     * @param requestEvent the incoming INVITE event
     * @param transaction used to send 180 Ringing, and the final response. null if no 180 has been
     *                    sent yet and therefore no transaction is used yet. In that case, only
     *                    one response can be sent,
     */
    public RTTCall(RequestEvent requestEvent, ServerTransaction transaction, List<TextListener> messageReceivers) {
        this(requestEvent.getRequest(), requestEvent.getDialog(), messageReceivers);
        incomingRequest = requestEvent;
        inviteTransaction = transaction;
    }

    /**
     * Use this constructor for an outgoing call. Ideally you will pass in the dialog that is
     * created for the call, but this may not be available yet,
     * so you will need to call addDialog() in that case.
     * @param creationRequest the INVITE Request sent to the other party to initiate the call
     * @param dialog
     */
    public RTTCall(Request creationRequest, Dialog dialog, List<TextListener> messageReceivers) {
        this.creationRequest = creationRequest;
        this.dialog = dialog;
        sipClient = SipClient.getInstance();
        destructionLock = new Semaphore(1);
        recvBuf = new FifoBuffer();
        printThread = new TextPrintThread(messageReceivers, recvBuf);
        printThread.start();
        try {
            manager = new RtpManager(sipClient.getLocalIP());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @return the transaction used to respond to the original INVITE, or null if none
     */
    public ServerTransaction getInviteTransaction() {
        return inviteTransaction;
    }

    /**
     * Used to change the Dialog associated with the call, especially if one was not
     * available at creation of the RTTCall
     * @param dialog the new Dialog to associate with the call
     */
    public synchronized void addDialog(Dialog dialog) {
        this.dialog = dialog;
    }


    /**
     * Precondition: call was created by an incoming RequestEvent. If call
     * was created by an outgoing request, will return null.
     * @return the incoming event that created the call, or null
     */
    public RequestEvent getCreationEvent() {
        return incomingRequest;
    }
    public Request getCreationRequest() {
        return creationRequest;
    }
    public Dialog getDialog() {
        return dialog;
    }

    public boolean equals(RTTCall otherCall) {
        Request newRequest = otherCall.getCreationRequest();
        return newRequest.equals(creationRequest);
    }

    public synchronized void setCalling() {
        calling = true;
    }
    public synchronized void setRinging() {
        ringing = true;
    }

    /**
     * Connect an incoming call that was previously ringing
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @param t140MapNum the RTP payload map number corresponding to t140 in the agreed session description
     * @param t140RedMapNum must be <= 0 if not using redundancy! This is the RTP payload map number corresponding to
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
     *
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @param t140MapNum the RTP payload map number corresponding to t140 in the agreed session description
     * @param t140RedMapNum must be <= 0 if not using redundancy! This is the RTP payload map number corresponding to
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
        outgoingBuf = new SyncBuffer(redGenerations, RFC4103_BUFFER_TIME);
        outgoingBuf.start();
        try {
            session = manager.createRtpSession(localRTPPort, remoteIP, remotePort);
            session.addRtpListener(recvThread);
            session.receiveRTPPackets();
            int payloadType = useRed ? t140RedMapNum : t140MapNum;
            //sendThread = new SendThread(session, payloadType);
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

    public void sendText(String text) {
        if (!connected)
            throw new IllegalStateException("call is not connected, no one to send text to");
        byte[] t140Text = text.getBytes(StandardCharsets.UTF_8);
        outgoingBuf.setData(t140Text);
    }


    /**
     * End a call at any stage. Invoking multiple times has no effect; the
     * first invocation ends the session.
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


    /**
     * This thread receives incoming RTP packets from JRTP's session,
     * repackages them into Omnitor's expected RTPPacket format, and
     * hands them over to a modified version of Omnitor's RtpTextReceiver,
     * which removes duplicates and extracts the text and puts it in the
     * FIFO buffer for PrintThread to read.
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
        List<TextListener> messageReceivers;
        FifoBuffer buffer;
        boolean stop = false;

        public TextPrintThread(List<TextListener> messageReceivers, FifoBuffer buffer) {
            this.messageReceivers = messageReceivers;
            this.buffer = buffer;
        }

        /* synchronizing on the boolean stop is probably not necessary */
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
                        for (TextListener receiver : messageReceivers) {
                            String text = new String(received, StandardCharsets.UTF_8);
                            receiver.RTTextReceived(text);
                        }
                    }
                } catch (InterruptedException e) {/* that's fine */}
            }
        }
    }
}
