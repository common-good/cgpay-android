package org.rcredits.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Handle database operations.
 * Mostly duplicates SQLiteDatabase object, which cannot be extended directly.
 * Created by William on 7/7/14.
 */
public class Db {
    private static SQLiteDatabase db;

    Db(boolean testing) {
        db = testing ? A.db_test : A.db_real;
    }

    public long insert(String table, ContentValues values) {
        long rowid = db.insert(table, null, values);
        assert rowid > 0;
        return rowid;
    }

    public int update(String table, ContentValues values, long rowid) {
        return db.update(table, values, "rowid=?", new String[] {String.valueOf(rowid)});
    }

    public int delete(String table, long rowid) {
        return db.delete(table, "rowid=?", new String[] {String.valueOf(rowid)});
    }

    /**
     * Return a recordset, positioned on the first record.
     * @param sql: a SELECT query string
     * @param selectionArgs: query parameters
     * @return: the recordset (null if empty)
     */
    public Q q(String sql, String[] selectionArgs) {
        Q q = new Q(db.rawQuery(sql, selectionArgs));
        if (q.moveToFirst()) {
            return q;
        } else {
            q.close();
            return null;
        }
    }

    public void beginTransaction() {db.beginTransaction();}
    public void setTransactionSuccessful() {db.setTransactionSuccessful();}
    public void endTransaction() {db.endTransaction();}
//    public void close() {db.close(); db = null;}

    /**
     * Change a transaction status.
     * @param rowid : which transaction to change
     * @param status : new status
     * @param txid : transaction ID on the server (null if no change)
     */
    public void changeStatus(Long rowid, int status, Long txid) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        if (txid != null) values.put("txid", txid);
        this.update("txs", values, rowid);
    }

    /**
     * Mark a transaction DONE and update the customer record.
     * @param txRowid: transaction rowid
     * @param txid: transaction record ID on the server
     * @param balance: customer's current balance
     * @param rewards: customer's rewards ever
     */
    public void completeTx(Long txRowid, String txid, String balance, String rewards, String lastTx) {
        ContentValues values = new ContentValues();
        values.put("balance", balance);
        values.put("rewards", rewards);
        values.put("lastTx", lastTx);
        Long custRowid = customerRowid(txQid(txRowid)); // find the corresponding customer record, if any

        beginTransaction();
        if (custRowid != null) this.update("customers", values, custRowid);
        changeStatus(txRowid, A.TX_DONE, Long.valueOf(txid));
        setTransactionSuccessful();
        endTransaction();
    }

    public void completeTx(Long txRowid, Json txJson) {
        completeTx(txRowid, txJson.get("txid"), A.nn(txJson.get("balance")).replace("*", ""),
                txJson.get("rewards"), txJson.get("created"));
    }

    public static void saveCustomer(String qid, byte[] image, Json idJson) {
//        if (!rcard.region.equals(A.region)) return; // don't record customers outside the region?

        Q q = A.db.oldCustomer(qid);
        if (q == null) { // new customer!
            ContentValues values = new ContentValues();
            for (String k : DbHelper.CUSTOMERS_FIELDS.split(" ")) values.put(k, idJson.get(k));
            values.put("qid", qid);
            values.put("photo", A.shrink(image));
            A.db.insert("customers", values);
            Q r = A.db.oldCustomer(qid); if (r != null) {A.deb("saveCustomer r.qid=" + r.getString("qid") + " r.name=" + r.getString("name")); r.close();}
        } else q.close();
    }

    /**
     * Find the customer in the database.
     * @param qid: the customer's account id (like NEW.AAA or NEW:AAA)
     * @return: the database record for that customer (null if not found)
     */
    public Q oldCustomer(String qid) {
        A.deb("oldCustomer qid=" + qid);
        return q("SELECT rowid, * FROM customers WHERE qid=?", new String[] {A.nn(qid)});
    }

    public Long customerRowid(String qid) {
        Q q = oldCustomer(qid);
        if (q == null) return null;
        Long rowid = q.rowid();
        q.close();
        A.deb("customerRowid rowid=" + rowid);
        return rowid;
    }

    public String customerName(String qid) {
        Q q = oldCustomer(qid);
        if (q == null) return "";
        String name = q.getString("name");
        String company = q.getString("company");
        q.close();

        return A.customerName(name, company);
    }

    public String txQid(Long rowid) {
        A.deb("txQid rowid=" + rowid);
        if (rowid == null) return null;
        Q q = q("SELECT customer FROM txs WHERE rowid=?", new String[] {String.valueOf(rowid)});
        if (q == null) return null;
        String qid = q.getString("customer");
        q.close();
        A.deb("txQid qid=" + qid);
        return qid;
    }
}
