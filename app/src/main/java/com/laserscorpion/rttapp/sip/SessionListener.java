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
 * SessionListeners are notified when call state changes.
 * This allows some non-SIP-layer class to listen for changes in the session state (e.g.
 * an upper layer that helps the user interact with the call).
 */
public interface SessionListener extends EventListener {

    void SessionEstablished(String userName);
    void SessionClosed();
    void SessionFailed(String reason);
    void SessionDisconnected(String reason);
}
