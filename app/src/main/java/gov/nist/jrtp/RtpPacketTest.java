/* This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 United States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 * 
 * The NIST RTP stack is provided by NIST as a service and is expressly
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

import gov.nist.util.ByteUtil;
import junit.framework.TestCase;

/**
 * This class tests P25 control octets.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.1 $, $Date: 2007-06-03 18:33:24 $
 * @since 1.5
 */
public class RtpPacketTest extends TestCase {

	/***************************************************************************
	 * Constructor
	 **************************************************************************/

	/**
	 * Contruct an RTP packet JUnit test.
	 */
	public RtpPacketTest() {

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Set up this JUnit test.
	 */
	public void setUp() {

	}
	
	/**
	 * Test packing and unpacking an RTP packet with maximum values.
	 */
	public void testPackUnpackMinValues() {

		// Test pack
		RtpPacket rtpPacket = new RtpPacket();
		rtpPacket.setV(2);
		rtpPacket.setP(1);
		rtpPacket.setX(1);
		rtpPacket.setCC(2);
		rtpPacket.setM(0);
		rtpPacket.setPT(2);
		int SN = rtpPacket.getSN();
		rtpPacket.setTS(2);
		rtpPacket.setSSRC(2);
		// byte[] testPayload = "This is some payload".getBytes();
		byte[] testPayload = new byte[11];
		rtpPacket.setPayload(testPayload, testPayload.length);

		byte[] encodedPacket = rtpPacket.getData();

		// Test unpack
		RtpPacket newRtpPacket = new RtpPacket(encodedPacket,
				encodedPacket.length);
		// newRtpPacket.unpack(encodedPacket, encodedPacket.length);

		assertTrue(newRtpPacket.getV() == 2);
		assertTrue(newRtpPacket.getP() == 1);
		assertTrue(newRtpPacket.getX() == 1);
		assertTrue(newRtpPacket.getCC() == 2);
		assertTrue(newRtpPacket.getM() == 0);
		assertTrue(newRtpPacket.getPT() == 2);
		assertTrue(newRtpPacket.getSN() == SN);
		assertTrue(newRtpPacket.getTS() == 2);
		assertTrue(newRtpPacket.getSSRC() == 2);

	}

	/**
	 * Test packing and unpacking an RTP packet with maximum values.
	 */
	public void testPackUnpackMaxValues() {

		// Test pack
		RtpPacket rtpPacket = new RtpPacket();
		rtpPacket.setV(ByteUtil.getMaxIntValueForNumBits(2));
		rtpPacket.setP(ByteUtil.getMaxIntValueForNumBits(1));
		rtpPacket.setX(ByteUtil.getMaxIntValueForNumBits(1));
		rtpPacket.setCC(ByteUtil.getMaxIntValueForNumBits(4));
		rtpPacket.setM(ByteUtil.getMaxIntValueForNumBits(1));
		rtpPacket.setPT(ByteUtil.getMaxIntValueForNumBits(7));
		int SN = rtpPacket.getSN();
		//rtpPacket.setSN(ByteUtil.getMaxIntValueForNumBits(16));
		rtpPacket.setTS(ByteUtil.getMaxLongValueForNumBits(32));
		rtpPacket.setSSRC(ByteUtil.getMaxLongValueForNumBits(32));
		// byte[] testPayload = "This is some payload".getBytes();
		byte[] testPayload = new byte[11];
		rtpPacket.setPayload(testPayload, testPayload.length);

		byte[] encodedPacket = rtpPacket.getData();

		// Test unpack
		RtpPacket newRtpPacket = new RtpPacket(encodedPacket,
				encodedPacket.length);
		// newRtpPacket.unpack(encodedPacket, encodedPacket.length);

		assertTrue(newRtpPacket.getV() == ByteUtil.getMaxIntValueForNumBits(2));
		assertTrue(newRtpPacket.getP() == ByteUtil.getMaxIntValueForNumBits(1));
		assertTrue(newRtpPacket.getX() == ByteUtil.getMaxIntValueForNumBits(1));
		assertTrue(newRtpPacket.getCC() == ByteUtil.getMaxIntValueForNumBits(4));
		assertTrue(newRtpPacket.getM() == ByteUtil.getMaxIntValueForNumBits(1));
		assertTrue(newRtpPacket.getPT() == ByteUtil.getMaxIntValueForNumBits(7));
		assertTrue(newRtpPacket.getSN() == SN);
		assertTrue(newRtpPacket.getTS() == ByteUtil.getMaxLongValueForNumBits(32));
		assertTrue(newRtpPacket.getSSRC() == ByteUtil.getMaxLongValueForNumBits(32));

	}

	/**
	 * Test packing and unpacking an RTP packet with out of range values.
	 */
	public void testPackUnpackOutOfRangeValues() {

		// Test pack
		RtpPacket rtpPacket = new RtpPacket();

		try {
			rtpPacket.setV(ByteUtil.getMaxIntValueForNumBits(2) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set V = " + 
				(ByteUtil.getMaxIntValueForNumBits(2) + 1) + ": " + iae);
		}

		try {
			rtpPacket.setP(ByteUtil.getMaxIntValueForNumBits(1) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set P = " + (ByteUtil.getMaxIntValueForNumBits(1) + 1) + ": "
						+ iae);
		}

		try {
			rtpPacket.setX(ByteUtil.getMaxIntValueForNumBits(1)+ 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set X = " + (ByteUtil.getMaxIntValueForNumBits(1) + 1) + ": "
						+ iae);
		}

		try {
			rtpPacket.setCC(ByteUtil.getMaxIntValueForNumBits(4) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set CC = " + (ByteUtil.getMaxIntValueForNumBits(4) + 1)
						+ ": " + iae);
		}

		try {
			rtpPacket.setM(ByteUtil.getMaxIntValueForNumBits(1) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set M = " + (ByteUtil.getMaxIntValueForNumBits(1) + 1) + ": "
						+ iae);
		}

		try {
			rtpPacket.setPT(ByteUtil.getMaxIntValueForNumBits(7) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set PT = " + (ByteUtil.getMaxIntValueForNumBits(7) + 1)
						+ ": " + iae);
		}

//		try {
//			rtpPacket.setSN(ByteUtil.getMaxIntValueForNumBits(16) + 1);
//		} catch (IllegalArgumentException iae) {
//			if (logger.isEnabledFor(org.apache.log4j.Level.FATAL))
//				logger.fatal("Set SN = " + (ByteUtil.getMaxIntValueForNumBits(16) + 1)
//						+ ": " + iae);
//		}

		try {
			rtpPacket.setTS(ByteUtil.getMaxLongValueForNumBits(32) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set TS = " + (ByteUtil.getMaxLongValueForNumBits(32) + 1)
						+ ": " + iae);
		}

		try {
			rtpPacket.setSSRC(ByteUtil.getMaxLongValueForNumBits(32) + 1);
		} catch (IllegalArgumentException iae) {
			System.out.println("Set SSRC = " + (ByteUtil.getMaxLongValueForNumBits(32) + 1)
						+ ": " + iae);
		}

		System.out.println("\nEnd of test.");
	}

}
