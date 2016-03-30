/* This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 United States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 * 
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS".  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof including, but
 * not limited to, the correctness, accuracy, reliability or usefulness of
 * the software.
 * 
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement
 * .
 */
package gov.nist.jrtp;

import gov.nist.jrtp.RtpPacket;
import java.io.*;
import java.net.*;

/**
 * This class implements an RTP packet receiver. An RTP packet receiver listens
 * on the designated port for incoming RTP packets. This class is implemented as
 * a Thread rather than a Runnable object so that we may invoke an interrupt to
 * halt execution.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.3 $, $Date: 2007-06-03 18:33:24 $
 * @since 1.5
 */
public class RtpPacketReceiver extends Thread {

	/***************************************************************************
	 * Constants
	 **************************************************************************/

	/**
	 * The time to live for waiting for an RTP packet in milliseconds. 0 means
	 * wait indefinitely.
	 */
	private static int TTL = 0;

	/***************************************************************************
	 * Variables
	 **************************************************************************/
	
	/** The socket for receiving an RTP packet. */
	private DatagramSocket receiveSocket = null;

	/** The calling RTP session. */
	private RtpSession rtpSession = null;

	/** Logs sequence number of last packet received. */
	private int lastRtpPacketSequenceNumber = 0;
	
	/** Check receive rate. */
	private long receiveTime = 0;
	
	/** Last receive time. */
	private long lastReceiveTime = 0;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct an RTP packet receiver.
	 * 
	 * @param rtpSession
	 *            the calling RTP session.
	 * @throws SocketException
	 */
	public RtpPacketReceiver(RtpSession rtpSession) throws SocketException {

		this.rtpSession = rtpSession;
		this.receiveSocket = rtpSession.getRtpRecvSocket();

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Run this object.
	 */
	public void run() {

		try {

			// Set timeout on packet-receive wait
			receiveSocket.setSoTimeout(TTL);

			// Since RTP packets are variable size, we have to be smart about
			// how large to set the incoming datagram packet buffer. If we
			// set to the maximum UDP packet size, we will ensure getting all
			// packet data, but performance will be extremely slow. If we
			// set to a smaller size, speed will increase, but we risk losing
			// data at the end of the packet.
			int bufferSize = RtpPacket.FIXED_HEADER_LENGTH
					+ RtpPacket.MAX_PAYLOAD_BUFFER_SIZE;

			byte[] buffer = new byte[bufferSize];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			int packet_count = 0;
			
			for (;;) {

				// Receive the UDP packet
				receiveSocket.receive(packet);

				lastReceiveTime = receiveTime;
				receiveTime = System.currentTimeMillis();
				
				if (lastReceiveTime > 0) {
					
					long elapsedTime = receiveTime - lastReceiveTime;
					System.out.println("elapsed: " + elapsedTime);
					
				}
				
				
				//System.out.println("Received packet: " + packet_count++);
				
				byte[] packetData = packet.getData();
				// Get packet size. Note that this is NOT the same as
				// packetData.length!
				int packetSize = packet.getLength();

				RtpPacket rtpPacket = new RtpPacket(packetData, packetSize);
				// rtpPacket.set();

				// Only process RTP packets in sequence. Otherwise, discard
				int rtpPacketSN = rtpPacket.getSN();
								
				if (rtpPacketSN > lastRtpPacketSequenceNumber) {

					lastRtpPacketSequenceNumber = rtpPacketSN;

					// Send event to listeners
					RtpPacketEvent rtpEvent = new RtpPacketEvent(rtpSession,
							rtpPacket, "Received RTP packet");

					for (RtpListener listener : rtpSession.listeners)
						listener.handleRtpPacketEvent((RtpPacketEvent) rtpEvent);

				} else {

					// Silently discard

				}

			}

		} catch (SocketException se) {

			RtpTimeoutEvent rtpEvent = new RtpTimeoutEvent(rtpSession, se);

			for (RtpListener listener : rtpSession.listeners)
				listener.handleRtpTimeoutEvent((RtpTimeoutEvent) rtpEvent);

		} catch (IOException se) {

			RtpErrorEvent rtpEvent = new RtpErrorEvent(rtpSession, se);

			for (RtpListener listener : rtpSession.listeners)
				listener.handleRtpErrorEvent((RtpErrorEvent) rtpEvent);

		} finally {

			// This is invoked when an interrupt is called on this thread.
			System.out.println("RtpPacketReceiver shutting down.");


			if (receiveSocket != null) {

				receiveSocket.close();
				receiveSocket = null;

			}

		}

	}

}
