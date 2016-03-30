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

import java.io.*;
import java.net.*;

/**
 * This class implements an RTP manager. An RTP manager is a single point of
 * access for an RTP sender/receiver and RTCP sender/receiver. An RTP manager
 * logically implements RTP sessions between RTP senders and receivers. An RTP
 * manager also traps RTP events of interest to listening applications.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.2 $, $Date: 2007-06-03 18:33:24 $
 * @since 1.5
 */
public class RtpManager {

	/***************************************************************************
	 * Variables
	 **************************************************************************/
	
	/** The IP address of this host. */
	private InetAddress myIpAddress = null;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct an RTP manager with the default localhost IP address.
	 * 
	 * @throws UnknownHostException
	 */
	public RtpManager() throws UnknownHostException {

		this.myIpAddress = InetAddress.getLocalHost();

	}

	/**
	 * Construct a RTP manager with the given IP address. This constructor is
	 * useful when a machine has multiple IP interfaces (i.e., NICs).
	 * 
	 * @param ipAddress
	 *            the user-defined IP address for this host
	 * @throws UnknownHostException
	 */
	public RtpManager(String ipAddress) throws UnknownHostException {

		this.myIpAddress = InetAddress.getByName(ipAddress);

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Create an RTP session.
	 */
	public RtpSession createRtpSession(int myRtpRecvPort, 
			String remoteIpAddress, int remoteRtpRecvPort)
			throws SocketException, IOException {

		return new RtpSession(this.myIpAddress, myRtpRecvPort, remoteIpAddress,
				remoteRtpRecvPort);

	}

	/**
	 * Create an RTP session that binds only the RTP receive port and waits
	 * indefinitely to receive an RTP packet. <I>When using this method, care
	 * must be taken to manually set the remote IP address and remote RTP
	 * receive port on the RtpSession object before calling
	 * RtpSession.sendRtpPacket().</I>
	 */
	public RtpSession createRtpSession(int myRtpRecvPort)
			throws SocketException {

		return new RtpSession(this.myIpAddress, myRtpRecvPort);

	}

	/**
	 * Get my IP address.
	 * 
	 * @return this host's IP address
	 */
	public InetAddress getMyIpAddress() {

		return this.myIpAddress;

	}

}
