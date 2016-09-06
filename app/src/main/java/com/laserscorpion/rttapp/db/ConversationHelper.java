package com.laserscorpion.rttapp.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Date;

public class ConversationHelper extends SQLiteOpenHelper {
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
