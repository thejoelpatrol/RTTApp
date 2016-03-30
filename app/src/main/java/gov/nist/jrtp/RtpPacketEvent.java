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

/**
 * This class implements an RTP event. Note that the event Source for the RTP
 * event is the RTP Packet Receiver that received the packet.
 * 
 * @author mranga@nist.gov
 * @version $Revision: 1.1 $, $Date: 2007-05-10 13:22:37 $
 * @since 1.5
 */
public class RtpPacketEvent extends RtpEvent {

	/***************************************************************************
	 * Constants
	 **************************************************************************/

	/** Determines if a de-serialized file is compatible with this class. */
	private static final long serialVersionUID = 0;

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** The RTP packet. */
	private RtpPacket rtpPacket = null;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct an RTP packet event.
	 * 
	 * @param source
	 *            The RTP session.
	 * @param rtpPacket
	 *            The RTP packet.
	 * @param description
	 *            A description of the RTP packet event.
	 */
	public RtpPacketEvent(RtpSession source, RtpPacket rtpPacket,
			String description) {

		super(source, description);
		this.rtpPacket = rtpPacket;

	}

	/**
	 * Get the RTP packet.
	 * 
	 * @return The RTP packet.
	 */
	public RtpPacket getRtpPacket() {

		return rtpPacket;

	}

}
