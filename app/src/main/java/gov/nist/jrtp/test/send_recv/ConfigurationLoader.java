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


import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

/**
 * This class implements a SAX XML configuration file loader for an application.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.2 $, $Date: 2007-06-03 18:33:23 $
 * @since 1.5
 */
public class ConfigurationLoader extends DefaultHandler {

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** The calling application. */
	protected TestApplication application;

	/** The configuration file for the calling application. */
	protected String filePath;

	/** The current XML tag element. */
	protected String currentElement;

	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Construct a configuration loader for the calling application.
	 * 
	 * @param application
	 *            The calling application.
	 * @param filePath
	 *            The configuration path for the calling application.
	 */
	public ConfigurationLoader(TestApplication application, String filePath) {

		this.application = application;
		this.filePath = filePath;

	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * This method loads the configuation file for the calling application.
	 */
	public void load() {

		DefaultHandler handler = this;
		SAXParserFactory factory = SAXParserFactory.newInstance();

		try {

			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(new File(filePath), handler);

		} catch (Throwable t) {

			t.printStackTrace();
		}
	}

	/**
	 * Parse the start of the XML document.
	 * 
	 * @throws SAXException
	 */
	public void startDocument() throws SAXException {
	}

	/**
	 * Parse the end of the XML document.
	 * 
	 * @throws SAXException
	 */
	public void endDocument() throws SAXException {
	}

	/**
	 * Parse the body of an XML document.
	 * 
	 * @param namespaceUri
	 *            The namespace for URI
	 * @param localName
	 *            The local name or XML element name
	 * @param qualifiedName
	 *            The qualified name or XML element name
	 * @param attributes
	 *            The attributes of an XML element.
	 * @throws SAXException
	 */
	public void startElement(String namespaceUri, String localName,
			String qualifiedName, Attributes attributes) throws SAXException {

		String applicationName = localName;

		if ("".equals(applicationName)) {

			applicationName = qualifiedName;

		}

		currentElement = applicationName.trim();

		if (currentElement.equals("me") || currentElement.equals("dest")) {

			NetworkParameters networkParameters = new NetworkParameters();

			if (attributes != null) {

				for (int i = 0; i < attributes.getLength(); i++) {

					String attributeName = attributes.getLocalName(i);

					if ("".equals(attributeName)) {

						attributeName = attributes.getQName(i);
					}

					System.out.println("[ConfigurationLoader] attributeName: "
								+ attributeName.trim());

					String value = attributes.getValue(i);

					System.out.println("[ConfigurationLoader] value: "
								+ value.trim());

					if (attributeName.equals("name")) {

						networkParameters.setName(value.trim());

					} else if (attributeName.equals("ip_address")) {

						networkParameters.setIpAddress(value);

					} else if (attributeName.equals("rtp_recv_port")) {

						networkParameters.setRtpRecvPort(new Integer(value)
								.intValue());

					}
				}

				if (currentElement.equals("me")) {

					application.networkParameters = networkParameters;

				} else if (currentElement.equals("dest")) {

					application.destinations.add(networkParameters);

				} else if (!networkParameters.isSet()) {

					System.out.println("[ConfigurationLoader] Configuration not"
								+ "completely set.");

				}
			}
		}
	}

	/**
	 * Process subelements of an XML element.
	 * 
	 * @param namespaceUri
	 *            The namespace for URI.
	 * @param qualifiedName
	 *            The qualified name or XML element name.
	 * @throws SAXException
	 */
	public void endElement(String namespaceUri, String sName,
			String qualifiedName) throws SAXException {
	}

	/**
	 * Process characters of an XML element.
	 * 
	 * @param buf
	 *            The character array.
	 * @param offset
	 *            The array offset.
	 * @param len
	 *            The length of buffer space to process.
	 * @throws SAXException
	 */
	public void characters(char buf[], int offset, int len) throws SAXException {
	}
}
