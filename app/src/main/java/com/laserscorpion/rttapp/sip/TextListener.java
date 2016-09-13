/* © 2016 Joel Cretan
 *
 * This is part of RTTAPP, an Android RFC 4103 real-time text app
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.laserscorpion.rttapp.sip;

import java.util.EventListener;

/**
 * TextListeners are notified with text messages when two kinds of events occur:
 * 1. Real-time text is received
 * 2. Some kind of status information is available regarding registering, sending INVITE, etc
 * The first case is important, since this app is about sending/receiving real-time text.
 * The second case is not very well specified. This can probably be reworked to either become
 * more focused or be removed.
 */
public interface TextListener extends EventListener {

    void controlMessageReceived(String message);

    /**
     * Called when real-time text has been received from the RTP session
     * @param text the incoming characters
     */
    void RTTextReceived(String text);

}
