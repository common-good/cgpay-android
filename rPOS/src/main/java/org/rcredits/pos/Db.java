package org.rcredits.pos;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.StatFs;

import java.util.Map;

/**
 * Handle database operations.
 * Mostly duplicates SQLiteDatabase object, which cannot be extended directly.
 * Created by William on 7/7/14.
 */
public class Db {
    private SQLiteDatabase db;
    private final static int MIN_K = 100; // keep at least this much room available
    public class NoRoom extends Exception { }

    Db(boolean testing) {
        db = testing ? A.db_test : A.db_real;
    }

    public long insert(String table, ContentValues values) throws NoRoom {
        if (!enoughRoom()) throw new NoRoom();
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
        Q q = new Q(db.rawQuery(sql, selectionArgs == null ? new String[] {} : selectionArgs));
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

    public Q getRow(String table, String where, String[] params) {
        return q("SELECT rowid, * FROM " + table + " WHERE " + where, params);
    }

    public Q getRow(String table, Long rowid) {return getRow(table, "rowid=?", new String[] {"" + rowid});}

    public String getField(String field, String table, String where, String[] params) {
        Q q = getRow(table, where, params);
        if (q == null) return null;
        String res = q.getString(field);
        q.close();
        return res;
    }

    public String getField(String field, String table, Long rowid) {
        return getField(field, table, "rowid=?", new String[] {"" + rowid});
    }

    public Long rowid(String table, String where, String[] params) {
        String res = getField("rowid", table, where, params);
        return res == null ? null : Long.valueOf(res);
    }

    public String txQid(Long rowid) {return getField("member", "txs", rowid);}
    public String custField(String qid, String field) {return getField(field, "members", custRowid(qid));}
    public Long custRowid(String qid) {return A.n(getField("rowid", "members", "qid=?", new String[] {qid}));}

    public Double sum(String field, String table, String where, String[] params) {
        Q q = q("SELECT SUM(" + field + ") FROM " + table + " WHERE " + where, params);
        if (q == null) return Double.valueOf(0);
        Double sum = q.getDouble(0);
        q.close();
        return sum;
    }

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
     * Mark a transaction DONE (if it exists) and update the customer record.
     * @param txRowid: transaction rowid
     * @param txid: transaction record ID on the server
     * @param balance: customer's current balance
     * @param rewards: customer's rewards ever
     */
    public void completeTx(Long txRowid, String txid, String balance, String rewards, String lastTx) {
        String qid = txQid(txRowid);
        if (qid == null) return; // record is deleted, so no action needed

        ContentValues values = new ContentValues();
        values.put("balance", balance);
        values.put("rewards", rewards);
        values.put("lastTx", lastTx);
        Long custRowid = custRowid(qid); // find the corresponding customer record, if any

        beginTransaction();
        if (custRowid != null) this.update("members", values, custRowid);
        changeStatus(txRowid, A.TX_DONE, Long.valueOf(txid));
        setTransactionSuccessful();
        endTransaction();
    }

    public void completeTx(Long txRowid, Json txJson) {
        completeTx(txRowid, txJson.get("txid"), A.nn(txJson.get("balance")).replace("*", ""),
                txJson.get("rewards"), txJson.get("created"));
    }

    public void saveCustomer(String qid, byte[] image, Json idJson) throws NoRoom {
//        if (!rcard.region.equals(A.region)) return; // don't record customers outside the region?

        Q q = oldCustomer(qid);
        if (q == null) { // new customer!
            ContentValues values = new ContentValues();
            for (String k : DbHelper.CUSTOMERS_FIELDS.split(" ")) values.put(k, idJson.get(k));
            values.put("qid", qid);
            values.put("photo", A.shrink(image));
            insert("members", values);
//            Q r = A.db.oldCustomer(qid); if (r != null) {A.deb("saveCustomer r.qid=" + r.getString("qid") + " r.name=" + r.getString("name")); r.close();}
        } else q.close();
    }

    /**
     * Find the customer in the database.
     * @param qid: the customer's account id (like NEW.AAA or NEW:AAA)
     * @return: the database record for that customer (null if not found)
     */
    public Q oldCustomer(String qid) {
        A.deb("oldCustomer qid=" + qid);
        return q("SELECT rowid, * FROM members WHERE qid=?", new String[] {A.nn(qid)});
    }

    public String customerName(String qid) {
        Q q = oldCustomer(qid);
        if (q == null) return "Member " + qid;
        String name = q.getString("name");
        String company = q.getString("company");
        q.close();

        return A.customerName(name, company);
    }

    public Long storeTx(Pairs pairs) throws NoRoom {
        pairs.add("created", String.valueOf(A.now()));

        ContentValues values = new ContentValues();
        for (String k : DbHelper.TXS_FIELDS.split(" ")) values.put(k, pairs.get(k));
        values.put("status", A.TX_PENDING);
        values.put("agent", A.agent); // gets added to pairs in A.post (not yet)
        values.put(DbHelper.TXS_CARDCODE, A.rpcPairs.get("code")); // temporarily store card code (from identify op)
        for (Map.Entry<String, Object> k : values.valueSet()) A.deb(String.format("Tx value %s: %s", k.getKey(), k.getValue()));
        A.lastTxRow = A.db.insert("txs", values);
        A.deb("Tx A.lastTxRow just set to " + A.lastTxRow);
/*                Q r = A.db.q("SELECT rowid, * FROM txs", new String[] {});
                if (r != null) {A.deb("Tx txid=" + r.getString("txid") + " customer=" + r.getString("member")); r.close();}
                else A.deb("r is null"); */
        return A.lastTxRow;
    }

    public Pairs txPairs(Long txRow) {
        Q q = q("SELECT rowid, * FROM txs WHERE rowid=?", new String[] {"" + txRow});
        Pairs pairs = new Pairs("op", "charge");
        for (String k : DbHelper.TXS_FIELDS.split(" ")) pairs = pairs.add(k, q.getString(k));
        pairs.add("force", q.getString("status")); // handle according to status
        q.close();
        return pairs;
    }

    /**
     * Cancel or delete the transaction.
     * @param rowid:
     * @param ui: <called by user interaction>
     * NOTE: q gets invalidated by the call to apiGetJson().
     *         So be done with it sooner, to avoid "StaleDataException: Access closed cursor"
     * This should not be called on the UI thread.
     */
    public Json cancelTx(long rowid, boolean ui) {
        Pairs pairs = txPairs(rowid).add("force", "" + A.TX_CANCEL);
        Json json = A.apiGetJson(A.region, pairs, ui);
        if (json != null && json.get("ok").equals("1")) {
            if (json.get("txid").equals("0")) {
                delete("txs", rowid); // does not exist on server, so just delete it
            } else {
                try {
                    reverseTx(rowid, json.get("undo"), json.get("txid"), json.get("created"), (ui ? A.agent : null));
                } catch(NoRoom e) {delete("txs", rowid);}
            }
        }
        return json;
    }

    /**
     * Record a reversing transaction from the server.
     * @param rowid1: original (local) transaction record ID
     * @param txid1: original transaction ID on server
     * @param txid2: reversing transaction ID on server
     * @param created2: reversing transaction creation time
     * @param agent: reversing agent, if known
     */
    private void reverseTx(long rowid1, String txid1, String txid2,  String created2, String agent) throws NoRoom {
        Q q = getRow("txs", rowid1);
        ContentValues values2 = new ContentValues();
        for (String k : DbHelper.TXS_FIELDS.split(" ")) values2.put(k, q.getString(k));
        q.close();

        values2.put("txid", txid2); // remember record ID of reversing transaction on server
        values2.put("status", A.TX_DONE); // mark offsetting transaction done
        values2.put("created", created2); // reversal date
        values2.put("amount", A.fmtAmt(-values2.getAsDouble("amount"), false)); // negate
        values2.put("description", "undo");
        if (agent != null) values2.put("agent", agent); // otherwise use agent of original tx

        beginTransaction();
        insert("txs", values2); // record offsetting transaction from server
        changeStatus(rowid1, A.TX_DONE, Long.valueOf(txid1)); // mark original transaction done
        setTransactionSuccessful();
        endTransaction();
    }

    public String showTxs() {
        Q q = q("SELECT rowid, * FROM txs", null);
        if (q == null) return "";

        String show = "";
        do {
            show += String.format("row#%s sta=%s dt=%s agt=%s cust=%s amt=%s g=%s desc=%s", q.rowid(),
                    q.getInt("status"), q.getInt("created"), q.getString("agent"), q.getString("member"),
                    A.fmtAmt(q.getDouble("amount"), true), q.getInt("goods"), q.getString("description"));
        } while (q.moveToNext());
        q.close();
        return show;
    }

    public String showCust() {
        Q q = q("SELECT rowid, * FROM members", null);
        if (q == null) return "";

        String show = "";
        do {
            show += String.format("row#%s qid=%s nm=%s co=%s" +
                    (q.isAgent() ? "agt=%s code=%s can=%s flg=%s" : "place=%s bal=%s rew=%s last=%s"),
                    q.rowid(), q.getString("qid"), q.getString("name"), q.getString("company"), q.getString("place"),
                    q.getString("balance"), q.getString("rewards"), q.getInt("lastTx"));
        } while (q.moveToNext());
        q.close();
        return show;
    }

    /**
     * Update the creation time in db and pairs.
     */
    public void fixTxTime(Long rowid, Pairs pairs) {
        Long created = A.now();
        pairs.add("created", "" + created);
        ContentValues values = new ContentValues();
        values.put("created", created);
        update("txs", values, rowid);
    }

    public void saveAgent(String qid, String cardCode, byte[] image, Json json) throws NoRoom {
        ContentValues values = new ContentValues();
        values.put("name", json.get("name"));
        values.put("company", json.get("company"));
        values.put(DbHelper.AGT_COMPANY_QID, json.get("default"));
        values.put(DbHelper.AGT_CARDCODE, cardCode);
        values.put(DbHelper.AGT_CAN, json.get("can"));
        values.put("photo", image);

        Long rowid = custRowid(qid);

        if (rowid == null) {
            values.put("qid", qid);
            values.put(DbHelper.AGT_FLAG, A.TX_AGENT);
            insert("members", values);
        } else update("members", values, rowid);
    }

    public boolean badAgent(String qid, String cardCode) {
        String savedCardCode = custField(qid, DbHelper.AGT_CARDCODE);
        return (savedCardCode == null || !savedCardCode.equals(cardCode));
    }

    /**
     * Return the amount of external storage space remaining, in kilobytes.
     */
    private float kLeft() {
        StatFs stat = new StatFs(A.context.getDatabasePath(DbHelper.DB_REAL_NAME).getPath());
        long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
        return bytesAvailable / 1024.f;
    }

    private boolean enoughRoom() {
        Long rowid;
        while (kLeft() < MIN_K) {
            rowid = rowid("txs", "status=? ORDER BY created LIMIT 1", new String[]{"" + A.TX_DONE});
            if (rowid == null) {
                rowid = rowid("members", "lastTx>0 ORDER BY lastTx LIMIT 1", null);
                if (rowid == null) return false; else delete("members", rowid);
            } else delete("txs", rowid);
        }
        return true;
    }
}
