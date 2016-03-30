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
 * This class implements an RTP exception.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.1 $, $Date: 2007-05-10 13:22:37 $
 * @since 1.5
 */
public class RtpException extends Exception {

	/***************************************************************************
	 * Constants
	 **************************************************************************/

	/** Determines if a de-serialized file is compatible with this class. */
	private static final long serialVersionUID = 0;

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** Constant that identifies an out-of-range method parameter. */
	public final static String OUT_OF_RANGE = "Method parameter value is out of range.";

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct an RTP exception.
	 * 
	 * @param exceptionType
	 *            The type of RTP exception.
	 */
	public RtpException(String exceptionType) {

		super(exceptionType);

	}

	/**
	 * Construct an RTP exception.
	 * 
	 * @param error
	 *            The error message for this exception
	 * @param wrappedException
	 *            The exception object
	 */
	public RtpException(String error, Exception wrappedException) {

		super(error, wrappedException);

	}

}
