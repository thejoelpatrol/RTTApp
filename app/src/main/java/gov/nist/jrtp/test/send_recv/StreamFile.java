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

import gov.nist.jrtp.*;
import java.io.*;

/**
 * This class implements the wrapper for reading data from a file and streaming
 * it to the RTP manager.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.3 $, $Date: 2007-06-03 18:33:23 $
 * @since 1.5
 */
public class StreamFile implements Runnable {

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** The application that invoked this stream. */
	protected TestApplication application;
	
	/** The rate in miliseconds at which packets are sent.*/
	private static long RATE = 20;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct a wrapper for streaming file data.
	 */
	public StreamFile(TestApplication application) {

		this.application = application;

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Restart reading on a waiting stream.
	 */
	public void restart() {

		synchronized (this) {

			this.notify();

		}
	}

	/**
	 * Run this object.
	 */
	public void run() {

		int bufferSize = RtpPacket.MAX_PAYLOAD_BUFFER_SIZE;
		byte[] buffer = new byte[bufferSize];

		try {

			FileInputStream fileInputStream = new FileInputStream(
					application.selectedFile);

			// Set up a test RTP packet
			RtpPacket rtpPacket = new RtpPacket();
			rtpPacket.setV(1);
			rtpPacket.setP(1);
			rtpPacket.setX(1);
			rtpPacket.setCC(1);
			rtpPacket.setM(1);
			rtpPacket.setPT(1);
			rtpPacket.setTS(1);
			rtpPacket.setSSRC(1);

			int numBytesRead = 0;
			long totalBytesRead = 0;
			long fileSize = application.selectedFile.length();
			long startTime = System.currentTimeMillis();
			
			// Read file
			while ((numBytesRead = fileInputStream.read(buffer)) > 0) {

				totalBytesRead += numBytesRead;
				application.statusLabel.setText("Sending " + totalBytesRead
						+ " of " + fileSize + " bytes");

				if (application.streaming == false) {

					try {

						System.out.println("[StreamFile] paused");

						synchronized (this) {

							wait();

						}

					} catch (InterruptedException ie) {

						System.out.println("[StreamFile] continuing");

					}
				}

				// Set RTP packet data rather than create new RTP packet
				rtpPacket.setPayload(buffer, numBytesRead);

				try {

					application.rtpSession.sendRtpPacket(rtpPacket);
					
					/* Note that an application is responsible for the timing
					   of packet delays between each outgoing packet.  Here,
					   we set to an arbitrary value = 10ms.  A better method
					   (not shown) is to check elapsed time between the 
					   sending of each packet to reduce jitter.
					*/
					try {
						Thread.sleep(RATE);
						
					} catch (Exception e) {
						
						e.printStackTrace();
					}

				} catch (RtpException re) {

					re.printStackTrace();

				}

			}

			// Clean up
			application.writeScroll(application.textArea, " Done.\n");
			fileInputStream.close();
			application.streaming = false;
			application.newFile = true;
			application.streamButton.setText("Stream");
			application.browseButton.setEnabled(true);

		} catch (FileNotFoundException fnfe) {

			fnfe.printStackTrace();

		} catch (IOException ioe) {

			ioe.printStackTrace();

		}
	}
}