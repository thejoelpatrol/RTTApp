package com.laserscorpion.rttapp.db;

import android.provider.BaseColumns;

public class TextAppContract {
    public static final String DATABASE_NAME = "RTT.db";

    private TextAppContract() {}

    public static class Credentials implements BaseColumns {
        public static final String TABLE_NAME = "credentials";
        public static final String COLUMN_NAME_USERNAME = "username";
        public static final String COLUMN_NAME_PASSWORD = "password";
    }

    public static class Conversations implements BaseColumns {
        public static final String TABLE_NAME = "conversations";
        public static final String COLUMN_NAME_OTHER_PARTY_URI = "other_party_uri";
        public static final String COLUMN_NAME_MY_TEXT = "my_text";
        public static final String COLUMN_NAME_OTHER_PARTY_TEXT = "other_party_text";
        public static final String COLUMN_NAME_CALL_START_TIME = "call_start_time";
        public static final String COLUMN_NAME_CALL_END_TIME = "call_end_time";
    }
}
