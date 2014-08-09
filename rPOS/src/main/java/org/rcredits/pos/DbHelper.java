package org.rcredits.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * rPOS database management
 * Created by William on 6/30/14.
 */
public class DbHelper extends SQLiteOpenHelper {
    public static final String TXS_FIELDS = "created agent member amount goods description"; // to send to server
    public static final String CUSTOMERS_FIELDS = "name company place"; // to get from server
    public static final String TXS_CARDCODE = "txid"; // name of field where cardcode is temporarily stored

    public static final String AGT_CARDCODE = "balance"; // name of field where manager's cardcode is stored
    public static final String AGT_COMPANY_QID = "place"; // name of field where manager's cardcode is stored
    public static final String AGT_CAN = "rewards"; // name of field where manager's permissions are stored
    public static final String AGT_FLAG = "lastTx"; // name of field that equals TX_AGENT for managers
    public static final String DB_REAL_NAME = "rpos.db";
    public static final String DB_TEST_NAME = "rpos_test.db";

    private static final int DATABASE_VERSION = 1;

    DbHelper(boolean testing) {
        super(A.context, testing ? DB_TEST_NAME : DB_REAL_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS members (" + // record of (same-region?) customers (and managers)
            "qid TEXT," + // customer or manager account code (like NEW.AAA or NEW:AAA)
            "name TEXT," + // full name (of customer or manager)
            "company TEXT," + // company name, if any (for customer or manager)
            "place TEXT," + // customer location / manager's company account code
            "balance REAL," + // current balance (as of lastTx) / manager's rCard security code
            "rewards REAL," + // rewards to date (as of lastTx) / manager's permissions
            "lastTx INTEGER," + // time of last reconciled transaction / -1 for managers
            "photo BLOB);" // lo-res B&W photo of customer (normally under 4k) / full res photo for manager
        );
        db.execSQL("CREATE INDEX custQid ON members(qid)");

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS txs (" +
            "txid INTEGER DEFAULT 0," + // transaction id (xid) on the server (for offline backup only)
            "status INTEGER," + // see A.TX_... constants
            "created INTEGER," + // transaction creation datetime
            "agent TEXT," + // qid for company and agent (if any) using the device
            "member TEXT," + // customer account code (qid)
            "amount REAL," +
            "goods INTEGER," + // <transaction is for real goods and services>
            "description TEXT);" // always "undo" or /.*reverses.*/, if this tx undoes a previous one (previous by date)
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //db.execSQL("DROP TABLE IF EXISTS members");
        //db.execSQL("DROP TABLE IF EXISTS txs");
        //onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
