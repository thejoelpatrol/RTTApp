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
package gov.nist.util;

import gov.nist.util.ByteUtil;
import junit.framework.TestCase;

/**
 * This class tests P25 block headers.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.2 $, $Date: 2007-06-03 18:33:23 $
 * @since 1.5
 */

public class ByteUtilTest extends TestCase {

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Test max int value for number of bits.
	 */
	public void testGetMaxIntValueForNumBits () {

		// Create block header 1 with empty constructor
		System.out.println("Testing getMaxIntValueForNumBits()");

		for (int i = 0; i < 32; i++) {
			
			int max = ByteUtil.getMaxIntValueForNumBits(i);
			
			// Check assertions for byte, short, and int-1
			if (i == 8)
				assertTrue(max == 255);
			else if (i == 16)
				assertTrue(max == 65535);
			else if (i == 31)
				assertTrue(max == 2147483647);

		}
	}
	
	/**
	 * Test max long value for number of bits.
	 */
	public void testGetMaxLongValueForNumBits () {


		// Create block header 1 with empty constructor
		System.out.println("\nTesting getMaxLongValueForNumBits()\n");

		for (int i = 0; i < 64; i++) {
			
			long max = ByteUtil.getMaxLongValueForNumBits(i);
			
			// Check assertions for byte, short, int, and long-1
			if (i == 8)
				assertTrue(max == 255);
			else if (i == 16)
				assertTrue(max == 65535);
			else if (i == 32)
				assertTrue(max == 4294967295L);
			else if (i == 63)
				assertTrue(max == 9223372036854775807L);

		}
	}

}
