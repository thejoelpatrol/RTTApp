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
 * of the terms of this agreement.
 */
package gov.nist.jrtp;

import gov.nist.util.ByteUtil;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class implements an RTP session. An RTP session is the application API
 * to both an RTP transmitter and an RTP receiver. This version of RTP is based
 * on <a href="http://www.ietf.org/rfc/rfc3550.txt">IETF RFC 3550</a>, but does
 * not include RTCP. Thus, this version of RTP is geared toward those
 * applications that need to transmit RTP packets, but use mechanisms other than
 * RTCP for managing those packets.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.3 $, $Date: 2007-06-03 18:33:24 $
 * @since 1.5
 */
public class RtpSession {

	/***************************************************************************
	 * Constants
	 **************************************************************************/

	/** Determines if a de-serialized file is compatible with this class. */
	private static final long serialVersionUID = 0;

	/**
	 * The relative directory path for storing RTP log files.
	 */
	private final String logDirectory = "../nistrtp_logs";

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** My IP address. */
	private InetAddress myIpAddress = null;

	/** The remote IP address. */
	private String remoteIpAddress = "";

	/** The remote RTP receive port. */
	private int remoteRtpRecvPort = -1;

	/** My RTP receive port. */
	protected int myRtpRecvPort = -1;

	/** The RTP packet receiver. */
	private RtpPacketReceiver rtpPacketReceiver = null;

	/** The RTP receive socket. */
	private DatagramSocket myRtpSendSocket = null;

	/** The RTP send socket. */
	private DatagramSocket myRtpRecvSocket = null;

	/** The remote Inet address. */
	private InetAddress remoteInetAddress = null;

	/** List of RTP listeners. */
	protected ArrayList<RtpListener> listeners = null;

	/**
	 * Received RTP packets. This is particularly useful for examining RTP
	 * packets at a later time. Contents of these RTP packets will be written to
	 * an RTP log file.
	 */
	protected ArrayList<byte[]> loggedRtpPackets = new ArrayList<byte[]>();
	
	/** The rtp sequence number for this session */
	//private int rtpSequenceNumber = (int) (1000 * Math.random());


	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct an RTP session.
	 * 
	 * @param myIpAddress
	 *            The IP address of this host.
	 * @param myRtpRecvPort
	 *            The RTP receive port.
	 * @param remoteIpAddress
	 *            The remote IP address.
	 * @param remoteRtpRecvPort
	 *            The remote RTP receive port.
	 * @throws SocketException
	 * @throws IOException
	 */
	public RtpSession(InetAddress myIpAddress, int myRtpRecvPort,
			String remoteIpAddress, int remoteRtpRecvPort)
			throws SocketException, IOException {

		this.myIpAddress = myIpAddress;
		this.myRtpRecvPort = myRtpRecvPort;
		this.remoteIpAddress = remoteIpAddress;
		this.remoteRtpRecvPort = remoteRtpRecvPort;

		myRtpRecvSocket = new DatagramSocket(myRtpRecvPort);
		myRtpSendSocket = myRtpRecvSocket; // Auto binds to an open port

		remoteInetAddress = InetAddress.getByName(remoteIpAddress);

		listeners = new ArrayList<RtpListener>();

	}

	/**
	 * Construct an RTP session. This constructor is typically used if the
	 * remoteRtpRecvPort is not known at the time of instantiation. Here, only
	 * myRtpRecvPort is bound and remoteRtpRecvPort is bound later when that
	 * port becomes known. <i>Care should be taken when using this constructor
	 * since it is left to the application to set the remote RTP IP address and
	 * remote RTP receive port. If an application attempts to send an RTP packet
	 * without the remote IP address and RTP receive port defined, the
	 * sendRtpPacket() method will throw an exception.</i>
	 * 
	 * @param myIpAddress
	 *            The IP address of this host.
	 * @param myRtpRecvPort
	 *            The RTP receive port.
	 * @throws SocketException
	 */
	public RtpSession(InetAddress myIpAddress, int myRtpRecvPort)
			throws SocketException {

		this.myIpAddress = myIpAddress;
		this.myRtpRecvPort = myRtpRecvPort;

		if (myRtpRecvPort != 0) {
			myRtpRecvSocket = new DatagramSocket(myRtpRecvPort);
			myRtpSendSocket = myRtpRecvSocket; // Auto binds to an open port
		} else {
			// A 0 port argument can occur when there is no RTP resources
			// available.
			myRtpSendSocket = new DatagramSocket(0);
			myRtpRecvSocket = null;
		}

		listeners = new ArrayList<RtpListener>();

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Add an RTP listener.
	 * 
	 * @param listener
	 *            The RTP listener to be added.
	 */
	public void addRtpListener(RtpListener listener) {

		listeners.add(listener);

	}

	/**
	 * Remove an RTP listener.
	 * 
	 * @param listener
	 *            The RTP listener to be removed.
	 */
	public void removeRtpListener(RtpListener listener) {

		listeners.remove(listener);

	}
	
//	public RtpPacket createRtpPacket() {
//
//
//		
//		RtpPacket rtpPacket = new RtpPacket();
//		rtpPacket.setSN(rtpSequenceNumber);
//		return rtpPacket;
//		
//	}

	/**
	 * Start receiving thread for RTP packets. Note that only one RTP packet
	 * receiver can be running at a time.
	 * 
	 * @throws IOException
	 * @throws RtpException
	 */
	public void receiveRTPPackets() throws SocketException, RtpException {
		if ( this.myRtpRecvSocket == null 	) throw new RtpException("No socket -- cannot recieve packets! ");
		if ( this.myRtpRecvSocket.isClosed()) throw new SocketException("Socket is closed.");
		if ((rtpPacketReceiver == null)
				|| (rtpPacketReceiver.getState() == Thread.State.TERMINATED)) {
			rtpPacketReceiver = new RtpPacketReceiver(this);
			rtpPacketReceiver.start();

		}

	}
	
	/**
	 * Release the port and stop the reciever.
	 * 
	 */
	public void stopRtpPacketReceiver() {
		this.rtpPacketReceiver.interrupt();
		this.myRtpRecvPort = 0;
		// Note that the interrupt call will close the socket
		// if the remote rtp recv port is still open, we
		// allocate a socket for it. This is because the sending
		// and receiving socket are the same.
		try {
			if ( this.remoteRtpRecvPort > 0 )
				this.myRtpSendSocket = new DatagramSocket();
		} catch (SocketException ex) {
			
		}
	}

	/**
	 * Send an RTP packet.  Note that the calling application is responsible
	 * for inserting packet delay when using this method to send multiple 
	 * packets.  <b> Failure to do so may lead to lost packets.</b>  <i>
	 * See </i><tt>gov.nist.jrtp.test.send_rec.StreamFile.java</tt> <i> 
	 * for an example of how to add delay when using this method</i>.
	 * 
	 * @param rtpPacket
	 *            The RTP packet to send.
	 * @throws IOException
	 * @throws UnknownHostException
	 * @throws RtpException
	 */
	public synchronized void sendRtpPacket(RtpPacket rtpPacket)
			throws RtpException, UnknownHostException, IOException {

		// Ensure that outgoingDatagramPacket has been initialized
		// with a remote IP address and remote RTP receive port
		if (remoteInetAddress == null) {

			if (remoteIpAddress == "") {

				throw new RtpException("Failed sending RTP packet. "
						+ "Remote IP address is undefined.");

			} else {

				remoteInetAddress = InetAddress.getByName(remoteIpAddress);

			}

		}

		if (remoteRtpRecvPort < 0) {

			throw new RtpException("ERROR: Cannot send RTP packet. "
					+ "Remote RTP receive port is undefined.");

		}
		
		System.out.println("---------------\n[RtpSession] RTP Data:");
		System.out.println("[RtpSession] Sending V: " + rtpPacket.getV());
		System.out.println("[RtpSession] Sending P: " + rtpPacket.getP());
		System.out.println("[RtpSession] Sending X: " + rtpPacket.getX());
		System.out.println("[RtpSession] Sending CC: " + rtpPacket.getCC());
		System.out.println("[RtpSession] Sending M: " + rtpPacket.getM());
		System.out.println("[RtpSession] Sending PT: " + rtpPacket.getPT());
		System.out.println("[RtpSession] Sending SN: " + rtpPacket.getSN());
		System.out.println("[RtpSession] Sending TS: " + rtpPacket.getTS());
		System.out.println("[RtpSession] Sending SSRC: " + rtpPacket.getSSRC());
		System.out.println("[RtpSession] Sending Payload size: " + rtpPacket.getPayloadLength());
		

		DatagramPacket outgoingDatagramPacket = new DatagramPacket(new byte[1],
				1, remoteInetAddress, remoteRtpRecvPort);
		
		// Convert RTP packet to byte array
		byte[] rtpPacketBytes = rtpPacket.getData();

		// Set RTP packet as UDP payload and send
		outgoingDatagramPacket.setData(rtpPacketBytes);
		
		if (myRtpSendSocket != null)
			myRtpSendSocket.send(outgoingDatagramPacket);
		
		// Increment sequence number (use mod 65535 to repeat when > 65535)
		rtpPacket.SN = ++rtpPacket.SN % ByteUtil.getMaxIntValueForNumBits(16);

	}

	/**
	 * Similar to normal shutdown except that we log all received RTP raw data
	 * to a timestamped file for future analyses and debugging.
	 * 
	 * @param sessionID1
	 *            An application-generated ID for distinguishing this RTP
	 *            sesion.
	 * @param sessionID2
	 *            A second application-generated ID for furthering
	 *            distinguishing this RTP session.
	 */
	public void shutDown(String sessionID1, String sessionID2) {

		// Do normal shutdown routine first
		shutDown();

	}

	/**
	 * Shut this RTP session down.
	 */
	public void shutDown() {

		System.out.println("[RtpSession " + getMyIpAddress() + ":"
					+ getMyRtpRecvPort() + "] shutting down");

		if (rtpPacketReceiver != null) // may be null because recieve port has
										// not yet been associagted
			rtpPacketReceiver.interrupt(); // Shut down RTP packet receiver

		if (myRtpRecvSocket != null) {
			myRtpRecvSocket.close();
			myRtpRecvSocket = null;
		}

		if (myRtpSendSocket != null) {
			myRtpSendSocket.close();
			myRtpSendSocket = null;
		}

	}

	/**
	 * Get my IP address.
	 * 
	 * @return My IP address
	 */
	public InetAddress getMyIpAddress() {

		return myIpAddress;

	}

	/**
	 * Get the remote IP address.
	 * 
	 * @return The remote IP address.
	 */
	public String getRemoteIpAddress() {

		return remoteIpAddress;

	}

	/**
	 * Set the remote IP address.
	 * 
	 * @param remoteIpAddress
	 *            The remote IP address.
	 */
	public void setRemoteIpAddress(String remoteIpAddress) {

		this.remoteIpAddress = remoteIpAddress;

	}

	/**
	 * Set the remote RTP receive port.
	 * 
	 * @param remoteRtpRecvPort
	 *            The remote RTP receive port.
	 */
	public void setRemoteRtpRecvPort(int remoteRtpRecvPort) {

		if (remoteRtpRecvPort % 2 != 0) {

			throw new IllegalArgumentException("RtpRecvPort must be even.");

		}

		this.remoteRtpRecvPort = remoteRtpRecvPort;
		if ( remoteRtpRecvPort == 0 && this.myRtpSendSocket != this.myRtpRecvSocket ) {
			this.myRtpSendSocket.close();
			this.myRtpSendSocket = null;
		}

	}

	/**
	 * Set my RTP recv port. This method is called when setting up half duplex
	 * sessions when RTP resources later become available.
	 */
	public void resetMyRtpRecvPort(int myRtpRecvPort) throws RtpException {
		if (myRtpRecvPort == 0 || myRtpRecvPort % 2 != 0) {

			throw new IllegalArgumentException("RtpRecvPort must be even.");

		}

		try {

			if (this.rtpPacketReceiver != null)
				this.rtpPacketReceiver.interrupt();
			this.rtpPacketReceiver = null;
			this.myRtpRecvPort = myRtpRecvPort;
			this.myRtpRecvSocket = new DatagramSocket(myRtpRecvPort);
			this.myRtpSendSocket = myRtpRecvSocket;
		} catch (SocketException ex) {
			throw new RtpException("failed to assign recv port", ex);
		}

	}

	/**
	 * Get the remote RTP receive port.
	 * 
	 * @return The remote RTP receive port.
	 */
	public int getRemoteRtpRecvPort() {

		return remoteRtpRecvPort;
	}

	/**
	 * Get my RTP receive port.
	 * 
	 * @return My RTP receive port.
	 */
	public int getMyRtpRecvPort() {

		return myRtpRecvPort;

	}

	/**
	 * Get my RTP receive socket.
	 * 
	 * @return My RTP receive port.
	 */
	public DatagramSocket getRtpRecvSocket() {

		return myRtpRecvSocket;

	}

	/**
	 * Get the XML formatted string representation.
	 * 
	 * @return the XML formatted string representation.
	 */
	public String toString() {

		return new StringBuffer().append("<rtp-session\n").append(
				" senderIpAddress = \"" + remoteIpAddress + "\"\n").append(
				" remoteRtpRecvPort = \"" + remoteRtpRecvPort + "\"\n").
		// append(" remoteRtcpRecvPort = " + remoteRtcpRecvPort).
				append(
						" myAddress = \"" + this.myIpAddress.getHostAddress()
								+ "\"\n").append(
						" myRtpRecvPort = \"" + myRtpRecvPort + "\"\n").
				// append(" myRtcpRecvPort = " + myRtcpRecvPort ).
				append("\n/>").toString();

	}

}
