package org.rcredits.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * rPOS database management
 * Created by William on 6/30/14.
 */
public class DbHelper extends SQLiteOpenHelper {
    public static final String MEMBERS_FIELDS = "qid name company place balance rewards lastTx photo";
    public static final String TXS_FIELDS = "txid status created agent member amount goods description";
    public static final String LOG_FIELDS = "time what class method line";
    public static final String TABLE_FIELDS[]  = {MEMBERS_FIELDS, TXS_FIELDS, LOG_FIELDS};
    public static final String TABLES[] = {"members", "txs", "log"};
    public static final String TXS_FIELDS_TO_SEND = "created agent member amount goods description"; // to send to server
    public static final String CUSTOMERS_FIELDS_TO_GET = "name company place"; // to get from server
    public static final String TXS_CARDCODE = "txid"; // name of field where cardcode is temporarily stored

    public static final String AGT_CARDCODE = "balance"; // name of field where manager's cardcode is stored (hashed)
    public static final String AGT_COMPANY_QID = "place"; // name of field where manager's company QID is stored
    public static final String AGT_CAN = "rewards"; // name of field where manager's permissions are stored
    public static final String AGT_FLAG = "lastTx"; // name of field that equals TX_AGENT for managers
    public static final String PHOTO_ID_FIELD = "rewards"; // name of field where photo ID is temporarily stored
    public static final String DB_REAL_NAME = "rpos.db";
    public static final String DB_TEST_NAME = "rpos_test.db";

    DbHelper(boolean testing) {
        super(A.context, testing ? DB_TEST_NAME : DB_REAL_NAME, null, Integer.parseInt(A.versionCode));
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
            "rewards REAL," + // rewards to date (as of lastTx) / manager's permissions / photo ID (!rewards.matches(NUMERIC))
            "lastTx INTEGER," + // time of last reconciled transaction / -1 for managers
            "photo BLOB);" // lo-res B&W photo of customer (normally under 4k) / full res photo for manager
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS custQid ON members(qid)");

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS txs (" +
//            "me TEXT," + // company (or device-owner) account code (qid)
            "txid INTEGER DEFAULT 0," + // transaction id (xid) on the server (for offline backup only)
            "status INTEGER," + // see A.TX_... constants
            "created INTEGER," + // transaction creation datetime
            "agent TEXT," + // qid for company and agent (if any) using the device
            "member TEXT," + // customer account code (qid)
            "amount REAL," +
            "goods INTEGER," + // <transaction is for real goods and services>
//            "proof TEXT," + // hash of cardCode, amount, created, and me (as proof of agreement)
            "description TEXT);" // always "undo" or /.*reverses.*/, if this tx undoes a previous one (previous by date)
        );
        A.deb("creating log table");

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS log (" +
            "time INTEGER," + // log entry datetime
            "what TEXT," + // description of what's happening
            "class TEXT," + // current class
            "method TEXT," + // current method
            "line INTEGER);" // current line number
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldv, int newv) {
        A.deb("upgrading db " + db.getPath());
        if (oldv < 212) onCreate(db); // add new tables, if any (so far just log table 2/1/2015)
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
