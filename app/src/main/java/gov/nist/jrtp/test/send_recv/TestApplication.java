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
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;


/**
 * This class implements a test application for testing RTP. This class
 * instantiates the RTP protocol through an RTP manager and allows users to
 * select media files and stream these files to the underlying RTP manager. This
 * class also impelements an RTPListener to trap RTP events.
 * 
 * @author steveq@nist.gov
 * @version $Revision: 1.3 $, $Date: 2007-06-03 18:33:23 $
 * @since 1.5
 */
public class TestApplication extends JFrame implements ActionListener,
		RtpListener {

	/***************************************************************************
	 * Variables
	 **************************************************************************/

	/** Determines if a de-serialized file is compatible with this class. */
	private static final long serialVersionUID = 0;

	/** The network parameters for this test application. */
	protected NetworkParameters networkParameters;

	/**
	 * A vector of NetworkParameters objects that represent communication (i.e.,
	 * RTP) destinations.
	 */
	protected Vector<NetworkParameters> destinations;

	/** A label that displays the status of the application. */
	protected JLabel statusLabel;

	/** Scroll pane for the text area.*/
	private JScrollPane scrollPane = null;
	
	/** A text area for displaying run-time information about the application. */
	protected JTextArea textArea;

	/** A text field that displays the selected file. */
	protected JTextField textField;

	/** A button for initiating a file chooser dialog. */
	protected JButton browseButton;

	/** A file chooser for a selecting file to be streamed. */
	protected JFileChooser fileChooser;

	/** A button for initiating the streaming of a selected file. */
	protected JButton streamButton;

	/** The selected file to be streamed. */
	protected File selectedFile;

	/** Indicates whether a new file was selected. */
	protected boolean newFile = false;

	/** Indicates that the application is currently streaming a file. */
	protected boolean streaming = false;

	/** A wrapper for reading a file and streaming it. */
	protected StreamFile streamFile;

	/** The thread for streaming a file to the receiver. */
	protected Thread streamThread;

	/** The RTP manager for this application. */
	protected RtpManager rtpManager;

	/** A single RTP session. */
	protected RtpSession rtpSession;


	/***************************************************************************
	 * Constructors
	 **************************************************************************/

	/**
	 * Constructs a GUI for testing RTP.
	 * 
	 * @param configFilePath
	 *            The path of the configuration file.
	 * @throws IOException
	 * @throws RtpException
	 */
	public TestApplication(String configFilePath) throws IOException,
			RtpException {

		super();

		destinations = new Vector<NetworkParameters>();

		ConfigurationLoader configLoader = new ConfigurationLoader(this,
				configFilePath);
		configLoader.load();
		configLoader = null;

		System.out.println("Name: " + networkParameters.getName());
		System.out.println("\tAddress: " + networkParameters.getIpAddress());
		System.out.println("\tRTP recv port: "
					+ networkParameters.getRtpRecvPort());

		System.out.println("Destinations: ");

		for (int i = 0; i < destinations.size(); i++) {
			NetworkParameters dest = (NetworkParameters) destinations.get(i);

			System.out.println("\tName: " + dest.getName());
			System.out.println("\t\tAddress: " + dest.getIpAddress());
			System.out.println("\t\tRTP recv port: " + dest.getRtpRecvPort());

		}

		streaming = false;
		newFile = false;

		try {

			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

		} catch (Exception e) {

			e.printStackTrace();

		}

		// Menu

		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem openMenuItem = new JMenuItem("Open");
		openMenuItem.setActionCommand("Open");
		openMenuItem.addActionListener(this);
		openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
				ActionEvent.CTRL_MASK));

		JMenuItem exitMenuItem = new JMenuItem("Exit");
		exitMenuItem.setActionCommand("Exit");
		exitMenuItem.addActionListener(this);
		exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
				ActionEvent.CTRL_MASK));

		fileMenu.add(openMenuItem);
		fileMenu.add(exitMenuItem);

		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// Panels

		textArea = new JTextArea();
		textArea.setBorder(new BevelBorder(BevelBorder.LOWERED));
		textArea.setBackground(Color.black);
		textArea.setForeground(Color.cyan);
		textArea.setAutoscrolls(true);
		scrollPane = new JScrollPane(textArea);

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new GridLayout(3, 1));

		statusLabel = new JLabel("Ready");

		JPanel browsePanel = new JPanel();
		browsePanel.setLayout(new BoxLayout(browsePanel, BoxLayout.X_AXIS));
		textField = new JTextField();
		browseButton = new JButton("Browse");
		browseButton.setActionCommand("Browse");
		browseButton.addActionListener(this);

		browsePanel.add(textField);
		browsePanel.add(browseButton);

		streamButton = new JButton("Send RTP Packets");
		streamButton.setEnabled(true);
		streamButton.setActionCommand("Stream");
		streamButton.addActionListener(this);

		bottomPanel.add(statusLabel);
		bottomPanel.add(browsePanel);
		bottomPanel.add(streamButton);

		fileChooser = new JFileChooser();

		// Frame and window

		setTitle(networkParameters.name);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		getContentPane().add(bottomPanel, BorderLayout.SOUTH);

		setSize(300, 400);
		Point upperLeftPoint = centerComponent(this);
		setLocation(upperLeftPoint);

		this.addWindowListener(new WindowAdapter() {

			public void windowClosing(WindowEvent e) {

				System.exit(0);

			}
		});

		this.setVisible(true);

		writeScroll(textArea, "Instantiating RTP manager... ");

		rtpManager = new RtpManager(networkParameters.getIpAddress());

		// Start an RTP receiver for each sender
		for (int i = 0; i < destinations.size(); i++) {
			NetworkParameters dest = (NetworkParameters) destinations.get(i);

			String remoteIpAddress = dest.getIpAddress();
			int remoteRtpRecvPort = dest.getRtpRecvPort();
			int myRtpRecvPort = networkParameters.getRtpRecvPort();

			// NOTE: There is only a single RTP session defined for
			// this application.
			rtpSession = rtpManager.createRtpSession(myRtpRecvPort,
					remoteIpAddress, remoteRtpRecvPort);
			rtpSession.addRtpListener(this);
			rtpSession.receiveRTPPackets();

		}

		writeScroll(textArea, "Done.\n");
	}

	/***************************************************************************
	 * Methods
	 **************************************************************************/

	/**
	 * Trap Swing events.
	 * 
	 * @param ae
	 *            The trapped event.
	 */
	public void actionPerformed(ActionEvent ae) {

		if (ae.getActionCommand().equals("Exit")) {

			rtpSession.shutDown();
			System.exit(0);

		} else if (ae.getActionCommand().equals("Open")) {

			openFile();

		} else if (ae.getActionCommand().equals("Browse")) {

			openFile();

		} else if (ae.getActionCommand().equals("Stream")) {

			if (!streaming) {

				// Stream
				streaming = true;
				browseButton.setEnabled(false);
				streamButton.setText("Pause"); // Set to pause when streaming

				if (newFile) {

					newFile = false;

					writeScroll(textArea, "Sending " + selectedFile.getName()
							+ "...");

					// Set previous streamFile and streamThread to null
					if (streamFile != null) {

						streamFile = null;

					}

					if (streamThread != null) {

						streamThread = null;

					}

					// int rtpSendPort = networkParameters.getRtpSendPort();
					streamFile = new StreamFile(this);
					streamThread = new Thread(streamFile);
					streamThread.start();

				} else {

					writeScroll(textArea, "Restarting "
							+ selectedFile.getName() + "...");
					streamFile.restart();
				}

			} else {

				// Pause
				streaming = false;
				writeScroll(textArea, "\nPausing " + selectedFile.getName()
						+ "\n");
				streamButton.setText("Stream"); // Set to stream when pausing
				browseButton.setEnabled(true);

				// Since we are paused, this might be a good time to clean up
				System.gc();

			}
		}
	}

	/**
	 * Trap RTP packet events.
	 * 
	 * @param rtpEvent
	 *            The RTP packet event.
	 */
	public void handleRtpPacketEvent(RtpPacketEvent rtpEvent) {

		// Print the remote IP address and port
		RtpSession rtpSession = (RtpSession) rtpEvent.getSource();

		RtpPacket rtpPacket = rtpEvent.getRtpPacket();

		textArea.append("Received RTP packet #" + rtpPacket.getSN() + "\n");
		textArea.setCaretPosition(textArea.getDocument().getLength());

		System.out.println("---------------\n[TestApplication] RTP Data:");
		System.out.println("[TestApplication] Received V: " + rtpPacket.getV());
		System.out.println("[TestApplication] Received P: " + rtpPacket.getP());
		System.out.println("[TestApplication] Received X: " + rtpPacket.getX());
		System.out.println("[TestApplication] Received CC: " + rtpPacket.getCC());
		System.out.println("[TestApplication] Received M: " + rtpPacket.getM());
		System.out.println("[TestApplication] Received PT: " + rtpPacket.getPT());
		System.out.println("[TestApplication] Received SN: " + rtpPacket.getSN());
		System.out.println("[TestApplication] Received TS: " + rtpPacket.getTS());
		System.out.println("[TestApplication] Received SSRC: " + rtpPacket.getSSRC());
		System.out.println("[TestApplication] Received Payload size: " + rtpPacket.getPayloadLength());

	}

	/**
	 * Trap RTP status events.
	 * 
	 * @param rtpEvent
	 *            The RTP status event.
	 */
	public void handleRtpStatusEvent(RtpStatusEvent rtpEvent) {

		if (rtpEvent.getStatus() == RtpStatus.RECEIVER_STOPPED) {

			System.out.println("RECEIVED RTP STATUS EVENT: RECEIVER_STOPPED");

		} else if (rtpEvent.getStatus() == RtpStatus.RECEIVER_STARTED) {

			System.out.println("RECEIVED RTP STATUS EVENT: RECEIVER_STARTED");

		}

	}

	/**
	 * Trap RTP timeout events.
	 * 
	 * @param rtpEvent
	 *            The RTP timeout event.
	 */
	public void handleRtpTimeoutEvent(RtpTimeoutEvent rtpEvent) {
		// TODO Auto-generated method stub

	}

	/**
	 * Trap RTP error events.
	 * 
	 * @param rtpEvent
	 *            The RTP error event.
	 */
	public void handleRtpErrorEvent(RtpErrorEvent rtpEvent) {
		// TODO Auto-generated method stub

	}

	/**
	 * Open a file for reading.
	 */
	public void openFile() {

		int returnValue = fileChooser.showOpenDialog(this);

		if (returnValue == JFileChooser.APPROVE_OPTION) {

			// Set the file to be sent
			selectedFile = fileChooser.getSelectedFile();
			textField.setText(selectedFile.getPath());

			streaming = false;
			newFile = true;

		}
	}

	/**
	 * Scroll a pane to the current caret position.
	 * 
	 * @param textArea
	 *            Tthe text area component to be written to.
	 * @param message
	 *            The message to written to the text area component.
	 */
	public void writeScroll(JTextArea textArea, String message) {

		textArea.append(message);
		int paneHeight = (int) textArea.getBounds().getHeight();
		textArea.scrollRectToVisible(new Rectangle(new Point(0, paneHeight)));

	}

	/**
	 * Return the top-left coordinate of a centered Component.
	 * 
	 * @param c
	 *            The component to be centered.
	 * @return The center point of the component.
	 */
	private Point centerComponent(Component c) {

		Rectangle rc = new Rectangle();
		rc = c.getBounds(rc);

		Rectangle rs = new Rectangle(Toolkit.getDefaultToolkit()
				.getScreenSize());

		return new Point((int) ((rs.getWidth() / 2) - (rc.getWidth() / 2)),
				(int) ((rs.getHeight() / 2) - (rc.getHeight() / 2)));

	}

	/**
	 * Main.
	 */
	public static void main(String[] args) throws Exception {

		if (args.length != 1) {
			System.out.println("Usage: java Device config_file_path");
			return;
		}

		TestApplication application = new TestApplication(args[0]);
	}

}