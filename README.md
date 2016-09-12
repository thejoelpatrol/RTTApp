# Purpose
<p>
	RFC 4103 is an internet standard for a replacement to legacy analog TTY communications for deaf and hard-of-hearing people. This is one of the first open-source Android implementations of the standard. It is written in pure Java and uses standard Android APIs wherever possible. It is usable for some needs, though a widespread release would depend on several improvements.
</p>
<p>
	More information is available in <a href="http://www.laserscorpion.com/RTTApp/">this report</a>. Anyone wishing to extend this project should review that document, as well as the <a href="https://thejoelpatrol.github.io/RTTApp/">code's javadoc</a>.
</p>
# Basic introduction
<p>
	The app contains about 3000 Java SLOC across 25 classes not counting libraries, and various XML UI layout files. It is targeted at Android 4.4 (KitKat) - 6.0 (Marshmallow). It includes all the files necessary to build it with Android Studio 2.1.3 (IntelliJ) and a Java 8 JDK. In order to use some Java 8 features, the build files specify use of the experimental (but likely standard in the future) <a href="https://source.android.com/source/jack.html">Jack toolchain</a>. 
</p>
<p>
	The app is mostly based around the following layer scheme:
	<pre>
		--------------
		|  UI layer  |
		--------------
		      |
		--------------
		| SIP layer  |  (SipClient)
		--------------
		      |
		--------------
		| Call layer |  (RTTCall)
		--------------
		      |
		--------------
		| RTP layer  |  (JRTP, Omnitor t140)
		--------------
	</pre>
	As it is my first Android app, first SIP app, and one of the larger project I've worked on alone so far, there is surely room for improvement, in the layering and other aspects. The intention was to separate the responsibilities of each layer, but the app largely evolved bit by bit rather than being designed from the start, so this may not have completely succeeded. 
</p>
<p>
	Using the JAIN SIP framework, some class must implement the <code>SipListener</code> interface, to receive SIP requests like INVITE and BYE and their responses. RTTApp's <code>SipClient</code> is that <code>SipListener</code>, and therefore the main nexus of the app. As the core of the app, it is a singleton. It makes up nearly 1/3 of the total SLOC, and the other classes in the app's SIP layer are helpers for it. <code>SipListener</code> is an important interface in any JAIN SIP app, so <code>SipClient</code> probably needs to be larger than some classes, but its current size relative to the app may not be ideal. Its public API is somewhat complicated and may be difficult to understand for someone new to the project. It probably serves more than one function. 
</p>
<p>
	The call layer is made up of the RTTCall class, which maintains the state of a particular call and handles sending and receiving text, via the RTP layer below. It stores the messages and dialog used in establishing the call, which the SIP layer may need later for sending further messages. When the RTTCall detects incoming text, it passes it up to listeners on the upper layers. 
</p>
<p>
	Further information on how the classes and layers interact is available in the <a href="https://thejoelpatrol.github.io/RTTApp/">program documentation</a>.
</p>