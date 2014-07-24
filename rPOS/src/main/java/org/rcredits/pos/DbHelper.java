package org.rcredits.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * rPOS database management
 * Created by William on 6/30/14.
 */
public class DbHelper extends SQLiteOpenHelper {
    public static final String TXS_FIELDS = "created op agent customer amount goods description"; // to send to server
    public static final String CUSTOMERS_FIELDS = "name company place"; // to get from server

    private static final int DATABASE_VERSION = 1;

    DbHelper(boolean testing) {
        super(A.context, testing ? "rpos_test.db" : "rpos.db", null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS customers (" + // record of same-region customers
            "qid TEXT," + // customer account code (like NEW.AAA or NEW:AAA)
            "name TEXT," + // member full name
            "company TEXT," + // company name, if any
            "place TEXT," + // customer location
            "balance REAL," + // current balance (as of lastTx)
            "rewards REAL," + // rewards to date (as of lastTx)
            "lastTx INTEGER," + // time of last reconciled transaction
            "photo BLOB);" // lo-res B&W photo of customer (normally under 4k)
        );
        db.execSQL("CREATE INDEX custQid ON customers(qid)");

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS txs (" +
            "txid INTEGER DEFAULT 0," + // transaction id (xid) on the server
            "status INTEGER," + // see A.TX_... constants
            "created INTEGER," +
            "op TEXT," + // charge or undo
            "agent TEXT," +
            "customer TEXT," + // customer account code (for charge) or txid (for undo)
            "amount REAL," +
            "goods INTEGER," +
            "description TEXT);"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //db.execSQL("DROP TABLE IF EXISTS customers");
        //db.execSQL("DROP TABLE IF EXISTS txs");
        //onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
