package earth.commongood.cgpay;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * rPOS database management
 * Created by William on 6/30/14.
 */
public class DbSetup extends SQLiteOpenHelper {
    public static final String MEMBERS_FIELDS = "qid code name company place balance rewards lastTx photo";
    public static final String TXS_FIELDS = "txid status created agent member amount goods proof description counter";
    public static final String LOG_FIELDS = "time what class method line";
    public static final String BAD_FIELDS = "qid code";
    public static final String[] TABLE_FIELDS  = {MEMBERS_FIELDS, TXS_FIELDS, LOG_FIELDS, BAD_FIELDS}; // must be same len as TABLES[]
    public static final String TABLES = "members txs log bad";
    public static final String TXS_FIELDS_TO_SEND = "agent amount member goods description created proof counter"; // to send to server
    public static final String CUSTOMERS_FIELDS_TO_GET = "name company place"; // to get from server
    public static final String TXS_CARDCODE = "txid"; // name of field where cardcode is temporarily stored

    public static final String AGT_COMPANY_QID = "place"; // name of field where manager's company QID is stored
    public static final String AGT_CAN = "rewards"; // name of field where manager's permissions are stored
    public static final String AGT_FLAG = "lastTx"; // name of field that equals TX_AGENT for managers
    public static final String AGT_ABBREV = "place"; // name of field to hold agent's abbreviated QR code
    public static final String DB_REAL_NAME = "rpos.db";
    public static final String DB_TEST_NAME = "rpos_test.db";
    public static final int UPGRADE_BEFORE = 219; // upgrade db for old versions less than this

    DbSetup (boolean testing) {
        super(A.context, testing ? DB_TEST_NAME : DB_REAL_NAME, null, A.versionCode);
        if (A.versionCode < UPGRADE_BEFORE) onUpgrade(getWritableDatabase(), A.versionCode, UPGRADE_BEFORE);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS members (" + // record of customers and managers
            "qid TEXT," + // customer account code (like XXXYYY) or manager account code (like XXXYYY-W)
            "code TEXT," + // hash of cardCode (rCard security code)
            "name TEXT," + // full name (of customer or manager)
            "company TEXT," + // company name, if any (for customer or manager)
            "place TEXT," + // customer location / manager's abbreviated QR code
            "balance REAL," + // current balance (as of lastTx) / company defaults (not used yet for this)
            "rewards REAL," + // rewards to date (as of lastTx) / manager's permissions
            "lastTx INTEGER," + // time of last reconciled transaction / -1 for managers, -2 for BAD
            "photo BLOB);" // lo-res B&W photo of customer (normally under 4k) / full res photo for manager
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS custQid ON members(qid)");

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS txs (" +
            "txid INTEGER DEFAULT 0," + // transaction id (xid) on the server (for offline backup only)
            "status INTEGER," + // see A.TX_... constants
            "created INTEGER," + // transaction creation datetime
            "agent TEXT," + // qid for company and agent (if any) using the device
            "member TEXT," + // customer account code (qid)
            "amount REAL," +
            "goods INTEGER," + // <transaction is for real goods and services>
            "proof TEXT," + // hash of agent + amount + qid + created + cardCode (as proof of agreement)
            "description TEXT," + // what was sold
            "counter INTEGER);" // optional transaction counter for customer
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS log (" +
            "time INTEGER," + // log entry datetime
            "what TEXT," + // description of what's happening
            "class TEXT," + // current class
            "method TEXT," + // current method
            "line INTEGER);" // current line number
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bad (" +
            "qid TEXT," + // phony account qid
            "code TEXT," + // phony card security code (the trailing space is necessary)
            "PRIMARY KEY (qid, code) ON CONFLICT IGNORE);" // ignore any duplicate additions
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldv, int newv) {
        A.log("upgrading db " + db.getPath());
        if (oldv < UPGRADE_BEFORE) onCreate(db); // add new tables, if any (last was bad table 9/2016)
        if (oldv < 217) {
            db.execSQL("ALTER TABLE members ADD COLUMN code TEXT");
            db.execSQL("ALTER TABLE txs ADD COLUMN proof TEXT");
        }
        if (oldv < 218) db.execSQL("ALTER TABLE txs ADD COLUMN counter INTEGER");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
