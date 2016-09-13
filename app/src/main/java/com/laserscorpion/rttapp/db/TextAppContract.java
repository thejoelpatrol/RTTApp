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
package com.laserscorpion.rttapp.db;

import android.provider.BaseColumns;

/**
 * Specifies the structure/schema of the app's database
 */
public class TextAppContract {
    public static final String DATABASE_NAME = "RTT.db";

    private TextAppContract() {}

    /**
     * The column name constants for the conversations table
     */
    public static class Conversations implements BaseColumns {
        public static final String TABLE_NAME = "conversations";
        public static final String COLUMN_NAME_OTHER_PARTY_URI = "other_party_uri";
        public static final String COLUMN_NAME_MY_TEXT = "my_text";
        public static final String COLUMN_NAME_OTHER_PARTY_TEXT = "other_party_text";
        public static final String COLUMN_NAME_CALL_START_TIME = "call_start_time";
        public static final String COLUMN_NAME_CALL_END_TIME = "call_end_time";
    }
}
