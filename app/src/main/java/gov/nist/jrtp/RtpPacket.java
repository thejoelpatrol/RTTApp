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

import java.io.Serializable;
import gov.nist.util.ByteUtil;

/**
 * This class implements a RTP packet as defined in <a
 * href="http://www.ietf.org/rfc/rfc3550.txt">IETF RFC 3550</a> with the
 * following exceptions:
 * <P>
 * 1. No CSRC support.<BR>
 * 2. No header extension<BR>
 * <p>
 * Future versions of this class may support CSRC and RTP header extensions.
 * 
 * The RTP header has the following format:
 * <p>
 * 
 * <pre>
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |     sequence number (SN)      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         timestamp (TS)                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * |            contributing source (CSRC) identifiers             |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.3 $, $Date: 2007-06-03 18:33:24 $
 * @since 1.5
 */
public class RtpPacket implements Serializable {

	/***************************************************************************
	 * Constants
	 **************************************************************************/

	/**
	 * 
	 */
	private static final long serialVersionUID = 0;

	/** Constant that identifies the total byte length of fixed fields. */
	public final static int FIXED_HEADER_LENGTH = 12; // V..SSRC only

	/** The maximum buffer (byte array) size for payload data. */
	public static final int MAX_PAYLOAD_BUFFER_SIZE = 512;

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/*
	 * Note that each of the following use data types that are larger than what
	 * is required. The motivation here is to the keep two's complement negative
	 * bit separate from the bits that represent the value.
	 */

	/** Version number (2 bits). */
	private int V = 0;

	/** Padding (1 bit). <i>Note that support for padding is not implemented in 
	 * this version</i>.  */
	private int P = 0;

	/** Header extension (1 bit). */
	private int X = 0;

	/** CSRC count (4 bits). */
	private int CC = 0;

	/** Marker (1 bit). */
	private int M = 0;

	/** Payload type (7 bits). */
	private int PT = 0;

	/** Sequence number (16 bits). */
	protected int SN = (int) (1000 * Math.random());

	/** Time stamp (32 bits). */
	private long TS = 0;

	/** Synchronization source (32 bits). */
	private long SSRC = 0;

	/** Contributing sources (32 bits) -- not supported yet. */
	// private CSRC;
	/** Header extension Defined By Profile (16 bits) -- not supported yet. */
	// private short DP = 0;
	/** Header extension length (16 bits) -- not supported yet. */
	// private short EL = 0;
	
	/** The payload.*/
	private byte[] payload = null;

	/** The length of the payload. */
	private int payloadLength = 0;

	/***************************************************************************
	 * Constructor
	 **************************************************************************/

	/**
	 * Construct an RTP packet.
	 */
	public RtpPacket() {
		
	}

	/**
	 * Set this RTP packet with the given byte array.
	 * 
	 * @param bytes
	 *            The byte array for populating this RTP packet.
	 * @param length
	 *            The number of bytes to read from the byte array.
	 */
	public RtpPacket(byte[] bytes, int length) {

		/*
		 * Since V..SN are 32 bits, build an int to hold V..SN before
		 * extracting.
		 */
		int V_SN_length = 4; // # bytes
		byte[] V_SN_bytes = new byte[V_SN_length];

		System.arraycopy(bytes, 0, V_SN_bytes, 0, V_SN_length);

		/* Extract V..SN */
		int V_SN = ByteUtil.bytesToInt(V_SN_bytes);
		V = (V_SN >>> 0x1E) & 0x03;
		P = (V_SN >>> 0x1D) & 0x01;
		X = (V_SN >>> 0x1C) & 0x01;
		CC = (V_SN >>> 0x18) & 0x0F;
		M = (V_SN >>> 0x17) & 0x01;
		PT = (V_SN >>> 0x10) & 0x7F;
		SN = (V_SN & 0xFFFF);
		int offset = V_SN_length;

		/* Extract TS */
		int TS_length = 4; // 4 bytes arriving, need to store as long
		byte[] TS_bytes = new byte[TS_length];
		System.arraycopy(bytes, offset, TS_bytes, 0, TS_length);
		byte[] longTS_bytes = new byte[8]; // Copy to long byte array
		System.arraycopy(TS_bytes, 0, longTS_bytes, 4, 4);
		TS = ByteUtil.bytesToLong(longTS_bytes);

		offset += TS_length;

		// Extract SSRC
		int SSRC_length = 4; // 4 bytes arriving, need to store as long
		byte[] SSRC_bytes = new byte[SSRC_length];
		System.arraycopy(bytes, offset, SSRC_bytes, 0, SSRC_length);
		byte[] longSSRC_bytes = new byte[8]; // Copy to long byte array
		System.arraycopy(SSRC_bytes, 0, longSSRC_bytes, 4, 4);
		SSRC = ByteUtil.bytesToLong(longSSRC_bytes);
		offset += SSRC_length;

		// Extract Payload
		int payload_length = (length - offset); // # bytes
		payloadLength = payload_length;

		payload = new byte[payload_length];

		System.arraycopy(bytes, offset, payload, 0, payload_length);

//		System.out.println("[RTPPacket] Unpacking: "
//							+ ByteUtil.writeBytes(bytes));
//
//		System.out.println("[RTPPacket] Unpacked V: " + V);
//		System.out.println("[RTPPacket] Unpacked P: " + P);
//		System.out.println("[RTPPacket] Unpacked X: " + X);
//		System.out.println("[RTPPacket] Unpacked CC: " + CC);
//		System.out.println("[RTPPacket] Unpacked M: " + M);
//		System.out.println("[RTPPacket] Unpacked PT: " + PT);
//		System.out.println("[RTPPacket] Unpacked SN: " + SN);
//		System.out.println("[RTPPacket] Unpacked TS: " + TS);
//		System.out.println("[RTPPacket] Unpacked SSRC: " + SSRC);
//		System.out.println("[RTPPacket] Unpacked payload: "
//					+ ByteUtil.writeBytes(payload));

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Get the data of this RTP packet as a byte array.
	 * 
	 * @return The data of this RTP packet as a byte array.
	 */
	/*
	 * (steveq) Note that we use the same convention for the method name as used
	 * in DatagramPacket.getData(byte[]).
	 */
	public byte[] getData() {

		/* Since V..SN are 32 bits, create a (int) byte array for V..SN. */
		int V_SN = 0;
		V_SN |= V; // Add V
		V_SN <<= 0x01; // Make room for P
		V_SN |= P; // Add P
		V_SN <<= 0x01; // Make room for X
		V_SN |= X; // Add X
		V_SN <<= 0x04; // Make room for CC
		V_SN |= CC; // Add CC
		V_SN <<= 0x01; // Make room for M
		V_SN |= M; // Add M
		V_SN <<= 0x07; // Make room for PT
		V_SN |= PT; // Add PT
		V_SN <<= 0x10; // Make room for SN
		V_SN |= SN; // Add SN
		byte[] V_SN_bytes = ByteUtil.intToBytes(V_SN);

		/*
		 * Create a byte array for TS. Cast from long to int (we won't lose
		 * precision because there are never more than 4 bytes of data).
		 */
		byte[] TS_bytes = ByteUtil.intToBytes((int) TS);

		/*
		 * Create a byte array for SSRC. Cast from long to int (we won't lose
		 * precision because there are never more than 4 bytes of data).
		 */
		byte[] SSRC_bytes = ByteUtil.intToBytes((int) SSRC);

		/* Create byte array for all data. */
		int length = V_SN_bytes.length + TS_bytes.length + SSRC_bytes.length
				+ payloadLength;

		byte[] data = new byte[length];

		int offset = 0;
		System.arraycopy(V_SN_bytes, 0, data, offset, V_SN_bytes.length);

		offset += V_SN_bytes.length;
		System.arraycopy(TS_bytes, 0, data, offset, TS_bytes.length);

		offset += TS_bytes.length;
		System.arraycopy(SSRC_bytes, 0, data, offset, SSRC_bytes.length);

		offset += SSRC_bytes.length;

		System.arraycopy(payload, 0, data, offset, payloadLength);

//		System.out.println("[RTPPacket] Packing V: " + V);
//		System.out.println("[RTPPacket] Packing P: " + P);
//		System.out.println("[RTPPacket] Packing X: " + X);
//		System.out.println("[RTPPacket] Packing CC: " + CC);
//		System.out.println("[RTPPacket] Packing M: " + M);
//		System.out.println("[RTPPacket] Packing PT: " + PT);
//		System.out.println("[RTPPacket] Packing SN: " + SN);
//		System.out.println("[RTPPacket] Packing TS: " + TS);
//		System.out.println("[RTPPacket] Packing SSRC: " + SSRC);
//		System.out.println("[RTPPacket] Packing payload: "
//					+ ByteUtil.writeBytes(payload));
//
//		System.out.println("[RTPPacket] Packed: " + ByteUtil.writeBytes(data));


		return data;

	}

	/*
	 * (steveq) The following uses method names based on solely on block
	 * diagrams for setters and getters. The reasoning here is that those that
	 * will be using this RTP stack will want to methods that are directly
	 * reflective of the field parameter mnemonic (e.g., setV for field V).
	 * Those that require further description are directed to refer to the
	 * javadoc for this class.
	 */
	/**
	 * Set this RTP version.
	 * 
	 * @param i
	 *            This RTP version (2 bits)
	 * @throws IllegalArgumentException
	 */
	public void setV(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(2)))
			V = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the RTP version.
	 */
	public int getV() {

		return V;

	}

	/**
	 * Set the padding bit.
	 * 
	 * @param i
	 *            The padding (1 bit).
	 * @throws IllegalArgumentException
	 */
	public void setP(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(1)))
			P = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the padding bit.
	 * 
	 * @return The padding.
	 */
	public int getP() {

		return P;

	}

	/**
	 * Set the extension.
	 * 
	 * @param i
	 *            The extension (1 bit)
	 * @throws IllegalArgumentException
	 */
	public void setX(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(1)))
			X = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the extension.
	 * 
	 * @return the extension.
	 */
	public int getX() {

		return X;

	}

	/**
	 * Set the CSRC count.
	 * 
	 * @param i
	 *            The CSRC count (4 bits)
	 * @throws IllegalArgumentException
	 */
	public void setCC(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(4)))
			CC = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the CSRC count.
	 * 
	 * @return the CSRC count.
	 */
	public int getCC() {

		return CC;

	}

	/**
	 * Set the marker.
	 * 
	 * @param i
	 *            The marker (1 bit)
	 * @throws IllegalArgumentException
	 */
	public void setM(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(1)))
			M = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the marker.
	 * 
	 * @return the marker.
	 */
	public int getM() {

		return M;

	}

	/**
	 * Set the payload type.
	 * 
	 * @param i
	 *            The payload type (7 bits)
	 * @throws IllegalArgumentException
	 */
	public void setPT(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(7)))
			PT = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the payload type.
	 * 
	 * @return The payload type.
	 */
	public int getPT() {

		return PT;

	}

	/**
	 * Set the sequence number.
	 * 
	 * @param i
	 *            The sequence number (16 bits)
	 * @throws IllegalArgumentException
	 */
	protected void setSN(int i) throws IllegalArgumentException {

		if ((0 <= i) && (i <= ByteUtil.getMaxIntValueForNumBits(16)))
			SN = i;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the sequence number.
	 * 
	 * @return the sequence number.
	 */
	public int getSN() {

		return SN;

	}

	/**
	 * Set the time stamp.
	 * 
	 * @param timeStamp
	 *            The time stamp (32 bits).
	 * @throws IllegalArgumentException
	 */
	public void setTS(long timeStamp) throws IllegalArgumentException {

		if ((0 <= timeStamp) && (timeStamp <= ByteUtil.getMaxLongValueForNumBits(32)))
			TS = timeStamp;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE);

	}

	/**
	 * Get the time stamp.
	 * 
	 * @return the time stamp.
	 */
	public long getTS() {

		return TS;

	}

	/**
	 * Set the synchronization source identifier.
	 * 
	 * @param ssrc
	 *            the synchronization source identifier (32 bits)
	 * @throws IllegalArgumentException
	 */
	public void setSSRC(long ssrc) throws IllegalArgumentException {

		if ((0 <= ssrc) && (ssrc <= ByteUtil.getMaxLongValueForNumBits(32)))
			SSRC = ssrc;
		else
			throw new IllegalArgumentException(RtpException.OUT_OF_RANGE + ssrc);

	}

	/**
	 * Get the synchronization source identifier.
	 * 
	 * @return the synchronization source identifier.
	 */
	public long getSSRC() {

		return SSRC;

	}

	// public int getCSRC() {}
	// public void setCSRC() {}

	/***************************************************************************
	 * RTP Header Extensions
	 **************************************************************************/

	// public void getDP(){}
	// public void setDP(){}
	// public void getEL(){}
	// public void setEL(){}
	/***************************************************************************
	 * Other Methods
	 **************************************************************************/

	/**
	 * Get the payload of this RTP packet.
	 * 
	 * @return the payload of this RTP packet.
	 */
	public byte[] getPayload() {

		return payload;

	}

	/**
	 * Set the payload of this RTP packet.
	 * 
	 * @param bytes
	 *            the byte buffer containing the payload
	 * @param length
	 *            the number of buffer bytes containing the payload.
	 */
	public void setPayload(byte[] bytes, int length)
			throws IllegalArgumentException {

		if (length > MAX_PAYLOAD_BUFFER_SIZE)
			throw new IllegalArgumentException(
					"Payload is too large Max Size is limited to "
							+ MAX_PAYLOAD_BUFFER_SIZE);

		payloadLength = length;
		payload = bytes;

	}

	/**
	 * Get the payload length.
	 * 
	 * @return they payload length.
	 */
	public int getPayloadLength() {

		return payloadLength;

	}

	/**
	 * Get the XML formatted string representation.
	 * 
	 * @return the XML formatted string representation.
	 */
	public String toString() {

		StringBuffer sb = new StringBuffer();
		sb.append("<rtp-header").append("\nversion = \"" + V + "\"").append(
				"\nheaderExtension \"" + X + "\"").append(
				"\nmarker = \"" + M + "\"").append(
				"\npayloadType =\"" + PT + "\"").append(
				"\nSequenceNumber =\"" + SN + "\"").append(
				"\nimeStamp = \"" + TS + "\"").append(
				"\nSSRC= \"" + SSRC + "\"").append("\n/>");
		return sb.toString();

	}

}
