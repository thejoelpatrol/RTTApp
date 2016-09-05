package com.laserscorpion.rttapp.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Date;

/**
 * SIP login credentials are stored in the SQLite database to keep them private.
 * From my reading of https://developer.android.com/guide/topics/data/data-storage.html,
 * it looks to me like SharedPreferences are basically on the honor system, i.e. specifying your
 * preferences with a fully qualified package name actually does nothing to limit access to your
 * app (it just stores "key-value pairs"). It seems that any other app that wants to read the
 * preference saved at com.laserscorpion.rttapp.pref_password is allowed to? This seems strange,
 * but that's my reading of it, so instead we'll use a file that is definitely stored within this
 * app's private /data/data/com.laserscorpion.rttapp directory.
 *
 * Update: apparently the default for SharedPreferences is for them to be private, enforced by file
 * access modes. I guess that's the same as a database, so this is not necessary after all.
 * Strangely, until API 24, it was in fact possible to have world-readable SharedPreferences, though,
 * which doesn't seem like it has any good use.
 */
public class CredentialsHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TextAppContract.Credentials.TABLE_NAME + " (" +
                    TextAppContract.Credentials._ID + " INTEGER PRIMARY KEY," +
                    TextAppContract.Credentials.COLUMN_NAME_USERNAME + "TEXT," +
                    TextAppContract.Credentials.COLUMN_NAME_PASSWORD + "TEXT )";
    private static final String[] projection = { TextAppContract.Credentials.COLUMN_NAME_USERNAME,
                                                TextAppContract.Credentials.COLUMN_NAME_PASSWORD };


    public CredentialsHelper(Context context) {
        super(context, TextAppContract.DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(SQL_CREATE_TABLE);
        ContentValues values = new ContentValues();
        values.put(TextAppContract.Credentials.COLUMN_NAME_USERNAME, ""); // insert an empty row to update later
        values.put(TextAppContract.Credentials.COLUMN_NAME_PASSWORD, "");
        sqLiteDatabase.insert(TextAppContract.Credentials.TABLE_NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    @Override
    public void onOpen(SQLiteDatabase sqLiteDatabase) {

    }

    public void save(String username, String password) {
        SQLiteDatabase sqLiteDatabase = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TextAppContract.Credentials.COLUMN_NAME_USERNAME, username);
        values.put(TextAppContract.Credentials.COLUMN_NAME_PASSWORD, password);
        // no need for WHERE clause ... this table is meant to only have one row
        // this is kind of a hack, i guess, since tables normally have many rows
        sqLiteDatabase.update(TextAppContract.Conversations.TABLE_NAME, values, null, null);
    }

    public Credentials getCredentials() {
        SQLiteDatabase sqLiteDatabase = getReadableDatabase();
        Credentials creds = new Credentials();
        Cursor c = sqLiteDatabase.query(TextAppContract.Credentials.TABLE_NAME, projection, null, null, null, null, null);
        c.moveToFirst();
        int i = c.getColumnIndex(TextAppContract.Credentials.COLUMN_NAME_USERNAME);
        creds.username = c.getString(i);
        int j = c.getColumnIndex(TextAppContract.Credentials.COLUMN_NAME_PASSWORD);
        creds.password = c.getString(j);
        return creds;
    }

    public class Credentials {
        public String username;
        public String password;
    }
}
