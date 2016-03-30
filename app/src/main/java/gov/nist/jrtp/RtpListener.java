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
 * This class defines an RTP listener interface.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.1 $, $Date: 2007-05-10 13:22:37 $
 * @since 1.5
 */
public interface RtpListener {

	/**
	 * Handle the received RTP packet.
	 * 
	 * @param rtpEvent The received RTP packet event.
	 */
	public void handleRtpPacketEvent(RtpPacketEvent rtpEvent);

	/**
	 * Handle an RTP Status event.
	 * 
	 * @param rtpEvent The received RTP status event.
	 */
	public void handleRtpStatusEvent(RtpStatusEvent rtpEvent);

	/**
	 * Handle an RTP timeout event.
	 * 
	 * @param rtpEvent The received RTP timeout event.
	 */
	public void handleRtpTimeoutEvent(RtpTimeoutEvent rtpEvent);

	/**
	 * Handle an RTP error event.
	 * 
	 * @param rtpEvent The received RTP error event.
	 */
	public void handleRtpErrorEvent(RtpErrorEvent rtpEvent);

}
