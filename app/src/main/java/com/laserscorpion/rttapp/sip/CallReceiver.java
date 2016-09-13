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

/**
 * A CallReceiver listens for an incoming call, and then <em>must</em> act on the call. It must either
 * accept or decline the call via the global SipClient.
 */
public interface CallReceiver {
    /**
     * Called when a new call is coming in. The receiver <em>must</em> then accept or decline the call
     * via the SipClient.
     */
    void callReceived(String from);
}
