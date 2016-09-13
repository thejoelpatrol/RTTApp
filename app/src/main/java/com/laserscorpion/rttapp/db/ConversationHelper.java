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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.Date;

/**
 * Saves (and retrives, in the future) conversation records from the database
 */
public class ConversationHelper extends SQLiteOpenHelper {
    public static final String TAG = "ConversationHelper";
    public static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TextAppContract.Conversations.TABLE_NAME + " (" +
                    TextAppContract.Conversations._ID + " INTEGER PRIMARY KEY," +
                    TextAppContract.Conversations.COLUMN_NAME_OTHER_PARTY_URI + " TEXT," +
                    TextAppContract.Conversations.COLUMN_NAME_MY_TEXT + " TEXT," +
                    TextAppContract.Conversations.COLUMN_NAME_OTHER_PARTY_TEXT + " TEXT," +
                    TextAppContract.Conversations.COLUMN_NAME_CALL_START_TIME + " TEXT," +
                    TextAppContract.Conversations.COLUMN_NAME_CALL_END_TIME + " TEXT )";


    public ConversationHelper(Context context) {
        super(context, TextAppContract.DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    @Override
    public void onOpen(SQLiteDatabase sqLiteDatabase) {

    }

    /**
     * Save a conversation in the database
     * @param URI the name of the other party in the call
     * @param myText the text sent by the user
     * @param otherPartyText the text received from the other party
     * @param callStartTime the time the call began
     * @param callEndTime e.g. now (new Date())
     */
    public void save(String URI, String myText, String otherPartyText, Date callStartTime, Date callEndTime) {
        SQLiteDatabase sqLiteDatabase = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TextAppContract.Conversations.COLUMN_NAME_OTHER_PARTY_URI, URI);
        values.put(TextAppContract.Conversations.COLUMN_NAME_MY_TEXT, myText);
        values.put(TextAppContract.Conversations.COLUMN_NAME_OTHER_PARTY_TEXT, otherPartyText);
        values.put(TextAppContract.Conversations.COLUMN_NAME_CALL_START_TIME, callStartTime.toString());
        values.put(TextAppContract.Conversations.COLUMN_NAME_CALL_END_TIME, callEndTime.toString());
        sqLiteDatabase.insert(TextAppContract.Conversations.TABLE_NAME, null, values);
    }
}
