package com.laserscorpion.rttapp;

import android.gov.nist.core.Host;
import android.gov.nist.javax.sdp.fields.AttributeField;
import android.gov.nist.javax.sdp.fields.ConnectionField;
import android.javax.sdp.Attribute;
import android.javax.sdp.Connection;
import android.javax.sdp.Media;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.Origin;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipFactory;
import android.javax.sip.address.Address;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.Message;
import android.os.StrictMode;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Vector;

/**
 * A collection of two static helper methods to read and create SDP data, for the purpose of
 * setting up RTT T140 calls
 */
public class SDPBuilder {
    private static class Sender {
        public String username;
        public String localIP;
        public int port;
    }

    private static final String TAG = "SDPBuilder";
    private static final int SAMPLE_RATE = 1000; // defined by RFC 4103 p.15
    private static SdpFactory factory = SdpFactory.getInstance();
    private static SipFactory sipFactory = SipFactory.getInstance();
    private static HeaderFactory headerFactory;
    private static SecureRandom randomGen = new SecureRandom();
    private static boolean useDummyAudio = true;

    public enum mediaType {T140, T140RED}

    /**
     * Creates a copy of message and adds SDP content and header for RTT, according to the other params.
     *
     * @param message          the message-in-progress that needs the SDP added. This message MUST already contain
     *                         a Contact header that specifies the SIP user and local IP sending this message. It will
     *                         likely throw a NPE if not present.
     * @param preferredT140Map if this is a response to a request that proposed a T140 rtpmap, use that.
     *                         Otherwise, use 0 for the default rtpmap num
     * @param preferredRedMap  if this is a response to a request that proposed a T140red rtpmap, use that.
     *                         Otherwise:
     *                         -Use 0 for the default T140red rtpmap num
     *                         -Use -1 to not use redundancy at all
     * @param port             the local UDP port where the other party should send RTT
     * @return a new copy of the message with the SDP added
     */
    public static Message addSDPContentAndHeader(Message message, int preferredT140Map, int preferredRedMap, int port) {
        if (headerFactory == null) {
            try {
                headerFactory = sipFactory.createHeaderFactory();
            } catch (PeerUnavailableException e) {
                Log.e(TAG, "Probably about to get an NPE ... couldn't get headerFactory", e);
            }
        }
        Message newMessage = (Message) message.clone();
        ContactHeader contact = (ContactHeader) newMessage.getHeader("Contact");
        SipURI from = (SipURI) contact.getAddress().getURI();
        Sender sender = new Sender();
        sender.username = from.getUser();
        sender.localIP = from.getHost();
        sender.port = port;
        try {
            String sdp = createRTTSDPContent(preferredT140Map, preferredRedMap, sender);
            ContentTypeHeader typeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            if (useDummyAudio) {
                String audio = createAudioSDPContent(-1, null); // if you ever want to actually do this, you'll need to get the real rtpmap
                newMessage.setContent(sdp + audio, typeHeader);
            } else
                newMessage.setContent(sdp, typeHeader);
        } catch (Exception e) {
            // TODO will these ever happen?
            Log.e(TAG, "Probably failed to add SDP, oops", e);
        }
        return newMessage;
    }


    /**
     * @param t140MapNum the preferred map number for the red media type, or 0 for no preference
     * @param redMapNum  the preferred map number for the red media type, or 0 for no preference, or -1 for no redundancy
     * @return
     */
    private static String createRTTSDPContent(int t140MapNum, int redMapNum, Sender sender) {
        int sessionID = Math.abs(randomGen.nextInt());
        if (t140MapNum == 0)
            t140MapNum = 100;
        if (redMapNum == 0)
            redMapNum = 101;
        try {
            int codecs[] = {t140MapNum};
            ;
            if (redMapNum > 0) {
                codecs = new int[2];
                codecs[0] = t140MapNum;
                codecs[1] = redMapNum;
            }
            MediaDescription textMedia = factory.createMediaDescription("text", sender.port, 1, "RTP/AVP", codecs);
            textMedia.setAttribute("rtpmap", t140MapNum + " t140/" + SAMPLE_RATE);
            if (redMapNum > 0) {
                AttributeField redAttr = new AttributeField();
                redAttr.setName("rtpmap");
                redAttr.setValue(redMapNum + " red/" + SAMPLE_RATE);
                textMedia.addAttribute(redAttr);
                textMedia.setAttribute("fmtp", redMapNum + " " + t140MapNum + "/" + t140MapNum + "/" + t140MapNum + "/" + t140MapNum); // 4 levels of red
            }
            AttributeField sendrecv = new AttributeField();
            sendrecv.setName("sendrecv");
            sendrecv.setValueAllowNull(null);
            textMedia.addAttribute(sendrecv);


            /* creating the session description requires checking the IP address (poorly)
               this is a "network" operation so isn't normally allowed by Android on the main thread
               what's annoying is that we have to replace the garbage that createSessionDescription()
               puts in session anyway */
            StrictMode.ThreadPolicy tp0 = StrictMode.getThreadPolicy();
            StrictMode.ThreadPolicy tp1 = StrictMode.ThreadPolicy.LAX;
            StrictMode.setThreadPolicy(tp1);
            SessionDescription session = factory.createSessionDescription();
            Origin origin = factory.createOrigin(sender.username, sessionID, 1, "IN", "IP4", sender.localIP);
            session.setOrigin(origin);
            StrictMode.setThreadPolicy(tp0);

            Connection connection = factory.createConnection(sender.localIP);
            session.setConnection(connection);
            Vector mediaDescs = session.getMediaDescriptions(true);
            mediaDescs.add(textMedia);
            session.setMediaDescriptions(mediaDescs);
            session.setSessionName(factory.createSessionName("RTT_SDP_v0.1"));
            return session.toString();
        } catch (SdpException e) {
            Log.e(TAG, "could not create SDP: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    /**
     * we're not actually going to send or receive audio. we are setting up a dummy audio stream
     * so as to work with Asterisk, which assumes there must always be at least one audio stream
     * on any type of call.
     *
     * @param preferredMapNum if we have received a proposed session, use the other party's suggested
     *                        audio map number. If not, i.e. we are proposing a session, use -1
     * @param preferredFormat if we have received a proposed session, use the other party's suggested
     *                        audio format. If not, i.e. we are proposing a session, use null
     * @return the audio description text to add to the session description
     * @throws SdpException
     */
    private static String createAudioSDPContent(int preferredMapNum, String preferredFormat) throws SdpException {
        int dummyPort = 45678;
        if (preferredMapNum < 0)
            preferredMapNum = 0;
        if (preferredFormat == null)
            preferredFormat = "PCMU/8000"; // this should be uncontroversial, everyone probably supports this, not that we'll use it
        int codecs[] = {preferredMapNum};
        MediaDescription audioMedia = factory.createMediaDescription("audio", dummyPort, 1, "RTP/AVP", codecs);
        audioMedia.setAttribute("rtpmap", preferredMapNum + " " + preferredFormat);
        return audioMedia.toString();
    }


    /* I'll be mad if it turns out there is a way to do this automatically with the
        SDP library, but as far as I can tell, all it does is structure the string into
        discrete lines of different types
        returns: the t140 payload map number from the incoming SDP, or -1 if none
     */
    public static int getT140MapNum(Message incomingRequestResponse, mediaType mediaType) {
        String body = new String(incomingRequestResponse.getRawContent(), StandardCharsets.UTF_8);
        String mediaName = (mediaType == mediaType.T140) ? "t140" : "red";
        String pattern = "[0-9]+ " + mediaName + "/" + SAMPLE_RATE;
        try {
            SessionDescription suggestedSession = factory.createSessionDescription(body);
            Vector<MediaDescription> mediaDescriptions = suggestedSession.getMediaDescriptions(true);
            for (MediaDescription mediaDescription : mediaDescriptions) {
                Media media = mediaDescription.getMedia();
                if (media.getMediaType().equals("text")) {
                    Vector<Attribute> attributes = mediaDescription.getAttributes(true);
                    for (Attribute attr : attributes) {
                        String attrName = attr.getName();
                        if (attrName.equals("rtpmap")) {
                            String attrValue = attr.getValue().toLowerCase();
                            if (attrValue.matches(pattern)) {
                                String mapNum = attrValue.split(" ")[0];
                                return Integer.decode(mapNum);
                            }
                        }
                    }
                }
            }
            return -1;
        } catch (Exception e) {
            Log.e(TAG, "call media may fail, couldn't get T140 rtpmap from SDP");
            e.printStackTrace();
            return -1;
        }
    }

    public static int getT140PortNum(Message otherPartySDP) {
        String body = new String(otherPartySDP.getRawContent(), StandardCharsets.UTF_8);
        try {
            SessionDescription suggestedSession = factory.createSessionDescription(body);
            Vector<MediaDescription> mediaDescriptions = suggestedSession.getMediaDescriptions(true);
            for (MediaDescription mediaDescription : mediaDescriptions) {
                Media media = mediaDescription.getMedia();
                if (media.getMediaType().equals("text")) {
                    // should also check attributes for t140 here
                    return media.getMediaPort();
                }
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "call media may fail, couldn't get T140 port from SDP");
            e.printStackTrace();
            return 0;
        }
    }

    public static String getRemoteIP(Message otherPartySDP) {
        String body = new String(otherPartySDP.getRawContent(), StandardCharsets.UTF_8);
        try {
            SessionDescription suggestedSession = factory.createSessionDescription(body);
            ConnectionField connection = (ConnectionField) suggestedSession.getConnection();
            Host host = connection.getConnectionAddress().getAddress();
            return host.getAddress();
        } catch (SdpParseException e) {
            Log.e(TAG, "app may crash/call may fail, couldn't get IP from SDP");
            e.printStackTrace();
            return null;
        }
    }
}