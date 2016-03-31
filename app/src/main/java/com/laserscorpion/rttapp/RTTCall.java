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
import java.util.List;
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
import se.omnitor.protocol.rtp.packets.RTPPacket;
import se.omnitor.util.*;


/**
 * Created by joel on 2/12/16.
 */
public class RTTCall {
    private static final String TAG = "RTTCall";
    private SipClient sipClient;
    private Dialog dialog;
    private Request creationRequest;

    private RequestEvent incomingRequest;
    private ServerTransaction inviteTransaction;


    private Semaphore creationLock;
    private Semaphore destructionLock;
    private boolean ringing;
    private boolean connected;
    private boolean calling;


    private int localPort;
    private int remotePort;
    private String remoteIP;

    private RtpManager manager;
    private List<TextListener> messageReceivers;
    private RtpSession session;
    private ReceiveThread recvThread;
    private TextPrintThread printThread;
    private int t140PayloadNum;
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
        this.messageReceivers = messageReceivers;
        FifoBuffer recvBuf = new FifoBuffer();
        recvThread = new ReceiveThread(recvBuf);
        recvThread.start();
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
     * @throws IllegalStateException if no call is currently ringing, or if setT140MapNum() has not yet been called
     */
    public void accept(String remoteIP, int remotePort, int localRTPPort, int t140MapNum) throws IllegalStateException {
        if (!ringing)
            throw new IllegalStateException("call is not ringing - cannot accept");
        connectCall(remoteIP, remotePort, localRTPPort, t140MapNum);
        /*connected = true;
        ringing = false;
        setUpStream();*/
    }
    /**
     *
     * @param remoteIP the IP of the remote party for the RTP stream
     * @param remotePort the port of the remote party for the RTP stream
     * @param localRTPPort the local port to be used for the RTP stream
     * @param t140MapNum the RTP payload map number corresponding to t140 in the agreed session description
     * @throws IllegalStateException if no call is currently outgoing
     */
    public void callAccepted(String remoteIP, int remotePort, int localRTPPort, int t140MapNum) {
        if (!calling)
            throw new IllegalStateException("not calling anyone - what was accepted?");
        connectCall(remoteIP, remotePort, localRTPPort, t140MapNum);
    }

    private synchronized void connectCall(String remoteIP, int remotePort, int localRTPPort, int t140MapNum) {
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.localPort = localRTPPort;
        this.t140PayloadNum = t140MapNum;
        try {
            session = manager.createRtpSession(localRTPPort, remoteIP, remotePort);
            session.addRtpListener(recvThread);
            session.receiveRTPPackets();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RtpException e) {
            e.printStackTrace();
        }
        connected = true;
        ringing = false;
        calling = false;
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
            printThread.stopPrinting();
            session.stopRtpPacketReceiver();
            session.shutDown();
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


    private class ReceiveThread extends Thread implements RtpListener {
        //private FifoBuffer buffer;
        private RtpTextReceiver textReceiver;

        public ReceiveThread(FifoBuffer buffer) {
            //this.buffer = buffer;
            textReceiver = new RtpTextReceiver(localPort, false, t140PayloadNum, 0, buffer);
        }

        @Override
        public void run() {
            while (true) {
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
            //Log.d(TAG, "received some text");
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

    private class TextPrintThread extends Thread {
        List<TextListener> messageReceivers;
        FifoBuffer buffer;
        boolean stop;

        public TextPrintThread(List<TextListener> messageReceivers, FifoBuffer buffer) {
            this.messageReceivers = messageReceivers;
            this.buffer = buffer;
            stop = false;
        }

        /* synchronizing on the boolean stop is probably not necessary */
        public void stopPrinting() {
            stop = true;
            synchronized (buffer) {
                buffer.notifyAll();
            }
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
