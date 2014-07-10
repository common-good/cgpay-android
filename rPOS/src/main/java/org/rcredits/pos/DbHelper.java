package org.rcredits.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * rPOS database management
 * Created by William on 6/30/14.
 */
public abstract class DbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 2;

    DbHelper(Context context) {
        super(context, "rpos", null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers (" +
                "qid TEXT," + // customer account code (like NEW.AAA or NEW:AAA)
                "name TEXT," + // member full name
                "company TEXT," + // company name, if any
                "place TEXT," + // customer location
                "firstTx NUMERIC," + // datetime of customer's first tx on this device
                "lastTx NUMERIC," + // datetime of customer's most recent tx on this device
                "amountEver NUMERIC," + // total amount charged (minus payments)
                "txsEver INTEGER," + // number of transactions with this customer
                "picture" + // lo-res B&W photo of customer (normally under 4k)
                ");" +

                "CREATE TABLE txs (" +
                "created NUMERIC," +
                "op TEXT," + // charge or undo
                "agent TEXT," +
                "customer TEXT," + // customer account code (for charge) or txid (for undo)
                "amount NUMERIC," +
                "goods INTEGER," +
                "description TEXT," +
                "status Integer" + // 0=transaction has not been uploaded yet 1=uploaded -1=cancel
                ");"
        );
    }
}
