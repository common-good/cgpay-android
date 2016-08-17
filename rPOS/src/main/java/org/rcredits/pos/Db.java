package org.rcredits.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Blob;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handle database operations.
 * Mostly duplicates SQLiteDatabase object, which cannot be extended directly.
 * Created by William on 7/7/14.
 */
public class Db {
    private SQLiteDatabase db;
    private final static int MIN_K = 100; // keep at least this much room available
    public class NoRoom extends Exception { }
    private static final int TX_DUP_INTERVAL = 10 * 60; // how many seconds before a duplicate tx can be done

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
    public Q q(String sql) {return q(sql, null);}

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
        A.log(String.format("changing status rowid=%s status=%s txid=%s", rowid, status, txid));
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
        A.log("completing tx: " + txRowid + " qid=" + qid + " txid=" + txid + " lastTx=" + lastTx);
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

    /**
     * Complete the transaction at row #txRowid with info from txJson.
     * @param txRowid
     * @param txJson
     */
    public void completeTx(Long txRowid, Json txJson) {
        completeTx(txRowid, txJson.get("txid"), A.nn(txJson.get("balance")).replace("*", ""),
                txJson.get("rewards"), txJson.get("created")); // * in balance means secret
    }

    public void saveCustomer(String qid, byte[] image, Json idJson) throws NoRoom {
//        if (!rcard.region.equals(A.region)) return; // don't record customers outside the region?
        A.log("saving customer " + qid + " idJson=" + idJson.toString());

        Q q = oldCustomer(qid);
        if (q == null) { // new customer!
            ContentValues values = new ContentValues();
            for (String k : DbHelper.CUSTOMERS_FIELDS_TO_GET.split(" ")) values.put(k, idJson.get(k));
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

    /**
     * Store the transaction (in the txs table) before sending it to the server.
     * @param pairs
     * @return rowid of inserted row
     * @throws NoRoom
     */
    public Long storeTx(Pairs pairs) throws NoRoom {
        ContentValues values = new ContentValues();
        for (String k : ("proof " + DbHelper.TXS_FIELDS_TO_SEND).split(" ")) values.put(k, pairs.get(k));
        values.put("status", A.TX_PENDING);
        values.put("agent", A.agent); // gets added to pairs in A.post (not yet)
//        values.put(DbHelper.TXS_CARDCODE, A.rpcPairs.get("code")); // temporarily store card code (from identify op)
//        values.put(DbHelper.TXS_CARDCODE, pairs.get("code")); // temporarily store card code
        for (Map.Entry<String, Object> k : values.valueSet()) A.deb(String.format("Tx value %s: %s", k.getKey(), k.getValue()));
        A.lastTxRow = A.db.insert("txs", values);
        A.deb("Tx A.lastTxRow just set to " + A.lastTxRow);
/*                Q r = A.db.q("SELECT rowid, * FROM txs", new String[] {});
                if (r != null) {A.deb("Tx txid=" + r.getString("txid") + " customer=" + r.getString("member")); r.close();}
                else A.deb("r is null"); */
        return A.lastTxRow;
    }

    /**
     * Say whether there is already another transaction with this member for the same amount.
     * @param pairs: parameters for the proposed transaction
     * @return <a similar completed transaction already exists>
     */
    public boolean similarTx(Pairs pairs) {
        String where = "member=? AND amount=? AND goods=? AND created>? AND status IN (?,?)";
        String[] params = {pairs.get("member"), pairs.get("amount"), pairs.get("goods")
                , (A.now() - TX_DUP_INTERVAL) + "", "" + A.TX_OFFLINE, "" + A.TX_DONE};
        return (A.db.getField("rowid", "txs", where, params) != null);
    }

    /**
     * Return named value pairs for the given transaction.
     * @param txRow: row id for the transaction
     * @return the pairs
     */
    public Pairs txPairs(Long txRow) {
        Q q = q("SELECT rowid, * FROM txs WHERE rowid=?", new String[] {"" + txRow});
        Pairs pairs = new Pairs("op", "charge");
        for (String k : DbHelper.TXS_FIELDS_TO_SEND.split(" ")) pairs = pairs.add(k, q.getString(k));
//        String code;
//        if (!Pattern.matches(A.NUMERIC, code = q.getString(DbHelper.TXS_CARDCODE))) pairs.add("code", code);
        pairs.add("force", q.getString("status")); // handle according to status
        q.close();
        return pairs;
    }

    /**
     * Cancel or delete the transaction.
     * @param rowid:
     * @param agent: current agent qid, if any
     * NOTE: q gets invalidated by the call to apiGetJson().
     *         So be done with it sooner, to avoid "StaleDataException: Access closed cursor"
     * This should not be called on the UI thread.
     */
    public Json cancelTx(long rowid, String agent) {
        A.log("canceling tx: " + rowid + " agent:" + agent);
        Pairs pairs = txPairs(rowid).add("force", "" + A.TX_CANCEL);
        Json json = A.apiGetJson(A.region, pairs);
        if (json != null && json.get("ok").equals("1")) {
            if (json.get("txid").equals("0")) {
                A.log("deleting tx: " + rowid);
                delete("txs", rowid); // does not exist on server, so just delete it
            } else {
                try {
                    reverseTx(rowid, json.get("undo"), json.get("txid"), json.get("created"), agent);
                } catch(NoRoom e) {
                    delete("txs", rowid);
                    A.log("no room to reverse, deleted tx: " + rowid);
                }
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
        A.log("reversing tx: " + rowid1 + " txid1=" + txid1 + " txid2=" + txid2 + " created2=" + created2 + " agent=" + agent);
        Q q = getRow("txs", rowid1);
        ContentValues values2 = new ContentValues();
        for (String k : DbHelper.TXS_FIELDS_TO_SEND.split(" ")) values2.put(k, q.getString(k));
        q.close();
        String amountPlain = A.fmtAmt(-values2.getAsDouble("amount"), false);

        values2.put("txid", txid2); // remember record ID of reversing transaction on server
        values2.put("status", A.TX_DONE); // mark offsetting transaction done
        values2.put("created", created2); // reversal date
        values2.put("amount", amountPlain); // negate
        if (agent != null) values2.put("agent", agent); // otherwise use agent of original tx
        values2.put("proof", ""); // no proof needed (since we have proof of the original) and proof is hard to get
        values2.put("description", "undo");

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
        A.log("saving agent: " + qid + " cardCode=? json=" + json.toString());
        ContentValues values = new ContentValues();
        values.put("name", json.get("name"));
        values.put("company", json.get("company"));
        values.put(DbHelper.AGT_COMPANY_QID, json.get("default"));
        values.put("code", A.hash(cardCode));
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
        String savedCardCode = custField(qid, "code");
        return (savedCardCode == null || !savedCardCode.equals(A.hash(cardCode)));
    }

    /**
     * Return the amount of external storage space remaining (including empty db space), in kilobytes.
     */
    public float kLeft() {
        StatFs stat = new StatFs(A.context.getDatabasePath(DbHelper.DB_REAL_NAME).getPath());
        long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks(); // fix requires API 18
        bytesAvailable += pragma("page_size") * pragma("freelist_count"); // free space in database
        return bytesAvailable / 1024.f;
    }

    /**
     * Say whether there is at least an arbitrary minimum amount of space available.
     * Delete old logs, completed transactions, and member records if necessary, to get more space.
     * @return <enough room is available>
     */
    public boolean enoughRoom() {
        Long rowid;
        while (kLeft() < MIN_K) {
            rowid = rowid("log", "time<? ORDER BY time LIMIT 1", new String[]{"" + A.daysAgo(1)});
            if (rowid != null) {delete("log", rowid); continue;}
            rowid = rowid("txs", "status=? ORDER BY created LIMIT 1", new String[]{"" + A.TX_DONE});
            if (rowid != null) {delete("txs", rowid); continue;}
            rowid = rowid("members", DbHelper.AGT_FLAG + "<>" + A.TX_AGENT + " ORDER BY lastTx LIMIT 1", null);
            if (rowid != null) {delete("members", rowid); continue;}
            return false;
        }
        return true;
    }

    /**
     * Return the result of a numeric database PRAGMA query
     */
    private Long pragma(String query) {
        Cursor q = db.rawQuery("PRAGMA " + query, new String[] {});
        q.moveToFirst();
        return q.getLong(0);
    }

    /**
     * Return a json-encoded dump of the specified table
     * @param table: table name
     * @return: json-encoded associative array, beginning with 0=>array of field names
     */
    public String dump(String table) {
        Json j = new Json("{}"); // the overall result
        JSONArray rec; // each record
        String v; // each value
        int tableIndex = Arrays.asList(DbHelper.TABLES).indexOf(table);
        String[] fields = DbHelper.TABLE_FIELDS[tableIndex].split(" ");
        int fieldCount = fields.length;
        String res = "{"; // the result (build final JSON by hand, to avoid Out of Memory on final j.toString()
        final String MEM_ERR = ",\"MEM\":\"ERR\"}"; // add this instead of closing brace, for memory error
        String line;

        rec = new JSONArray();
        for (String field : fields) rec.put(field);
        //j.put("0", rec.toString());
        res += "\"0\":" + rec.toString();

        Cursor q = db.rawQuery("SELECT rowid, * FROM " + table + " ORDER BY rowid DESC", new String[] {});
        if (q == null || !q.moveToFirst()) return res + "}"; // was j.toString();

        try {
            do {
                rec = new JSONArray();
                for (int i = 0; i < fieldCount; i++) {
                    if (fields[i].equals("photo")) {
                        v = (q.getBlob(i + 1) == null) ? "0" : (q.getBlob(i + 1).length + ""); // just say how long photo is
                    } else v = q.getString(i + 1); // i+1 because rowid is not included in fields variable
                    rec.put(v);
                }
    //            j.put(q.getLong(0) + "", rec.toString().replace("\\", ""));
                line = ",\"" + q.getLong(0) + "\":" + rec.toString();
                assert((res + line + MEM_ERR).length() > 0); // make sure we can add the final brace or an error notice
                res += line;
            } while (q.moveToNext());
        } catch (OutOfMemoryError e) {
            q.close();
            return res + MEM_ERR;
        }
        q.close();
//        return j.toString();
        return res + "}"; // no memory error
    }
}
