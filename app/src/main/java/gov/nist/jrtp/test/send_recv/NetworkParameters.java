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
package gov.nist.jrtp.test.send_recv;

/**
 * This class stores the configuration of an application.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.1 $, $Date: 2007-05-10 13:22:36 $
 * @since 1.5
 */
public class NetworkParameters {

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** The name of the application. */
	protected String name = "";

	/** The IP address of the application. */
	protected String ipAddress = "";

	/** The RTP receive port of the application. */
	protected int rtpRecvPort = -1;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct network parameters for an application.
	 */
	public NetworkParameters() {

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Set the name of the application.
	 * 
	 * @param name
	 *            the name of the ISSI application.
	 */
	public void setName(String name) {

		this.name = name;

	}

	/**
	 * Get the name of the application.
	 * 
	 * @return the name of the application.
	 */
	public String getName() {

		return name;

	}

	/**
	 * Set the IP address of the application.
	 * 
	 * @param ipAddress
	 *            the IP address of the application.
	 */
	public void setIpAddress(String ipAddress) {

		this.ipAddress = ipAddress;

	}

	/**
	 * Get the IP address of the application.
	 * 
	 * @return The IP address of the application.
	 */
	public String getIpAddress() {

		return ipAddress;

	}

	/**
	 * Set the RTP send port of the application.
	 * 
	 * @param rtpRecvPort The RTP receive port.
	 */
	public void setRtpRecvPort(int rtpRecvPort) {

		this.rtpRecvPort = rtpRecvPort;

	}

	/**
	 * Get the RTP send port of the application.
	 * 
	 * @return The RTP send port of the application.
	 */
	public int getRtpRecvPort() {

		return rtpRecvPort;

	}

	/**
	 * Ensure that all parameters are set.
	 * 
	 * @return true if all parameters are set, otherwise return false.
	 */
	public boolean isSet() {

		if ((name != null) && (ipAddress != null) && (rtpRecvPort > 0)) {

			return true;

		} else {

			return false;

		}
	}
}