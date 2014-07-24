package org.rcredits.pos;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.os.SystemClock;

import org.apache.http.NameValuePair;

import java.util.List;

/**
 * Do stuff periodically in the background:
 *  - Reconcile offline transactions with server
 *  - Synchronize the system clock with the server's
 *  - Check for available updates (included in clock sync)
 * cancel by calling A.periodic.cancel(true); A.periodic = null;
 */
public class Periodic extends AsyncTask<String, Void, Integer> {
    private Db db;

    @Override
    protected Integer doInBackground(String... zot) {
        db = A.db; // use a private db pointer, in case user switches mode

        Q q;
        String sql = "SELECT rowid, * FROM txs WHERE status<>? AND created<?";

        while (!isCancelled()) {
            sleep(A.PERIOD);
            if (isCancelled()) break;
            if (!A.setTime(A.getTime())) continue; // sync clock and check for updates
            A.deb("after setTime");
            if (isCancelled()) break;

            String[] params = new String[] {String.valueOf(A.TX_DONE), String.valueOf(A.now() - A.PERIOD)};
            q = db.q(sql, params);
            if (q == null) continue;
            A.deb("q!=null");
            do { // for each non-current transaction in limbo [NOTE: do not use "continue" in do...while]
                sleep(1); // breathe between db operations, to make sure UI runs fast
                reconcile(q);
                if (q.invalid()) q = db.q(sql, params); // refresh, if invalidated
            } while (q != null && q.moveToNext() && !isCancelled());

            q.close();
        }

        return 0;
    }

    @Override
    protected void onPostExecute(Integer arg) {
        //if (db != null) db.close();
    }

    /**
     * Reconcile the given transaction with the server.
     * @param q: pointer to transaction record
     * NOTE: q gets invalidated by the call to apiGetJson().
     *         So be done with it sooner, to avoid "StaleDataException: Access closed cursor"
     */
    private void reconcile(Q q) {
        List<NameValuePair> pairs = null; // NOTE: first call of auPair (with pairs=null) is not by reference
        for (String k : DbHelper.TXS_FIELDS.split(" ")) pairs = A.auPair(pairs, k, q.getString(k));
        int status = q.getInt("status");
        A.deb("status=" + status + " txid=" + q.getString("txid") + " amount=" + q.getString("amount"));

        if (status == A.TX_CANCEL || status == A.TX_PENDING) {
            cancelTx(q, pairs);
        } else if (status == A.TX_OFFLINE) { // tell server about a completed transaction
            long rowid = q.rowid();
            String code = q.getString("txid"); // card code is stored temporarily in "txid"
            String qid = q.getString("customer");
            A.auPair(pairs, "force", "1");

            Json json = A.apiGetJson(A.region, pairs, false);
            A.deb("json is null?:" + (json == null ? "yes" : "no"));
            if (json != null && json.get("ok").equals("1")) {
                completeOldTx(rowid, qid, code, json);
            }
        }
    }

    /**
     * Cancel or delete the transaction.
     * @param q
     * @param pairs
     * NOTE: q gets invalidated by the call to apiGetJson().
     *         So be done with it sooner, to avoid "StaleDataException: Access closed cursor"
     */
    private void cancelTx(Q q, List<NameValuePair> pairs) {
        long rowid = q.rowid();

        ContentValues values = new ContentValues();
        for (String k : DbHelper.TXS_FIELDS.split(" ")) values.put(k, q.getString(k));

        A.auPair(pairs, "force", "-1");
        Json json = A.apiGetJson(A.region, pairs, false);
        if (json != null && json.get("ok").equals("1")) {
            if (!json.get("txid").equals("0")) { // record the reversing transaction
                // NOTE: we may need to pass ContentValues rather than q,
                reverseTx(rowid, json.get("undo"), values, json.get("txid"), json.get("created"));
            } else db.delete("txs", rowid); // does not exist on server, so just delete it
        }
    }

    /**
     * Complete the stored transaction, if we have -- or can get -- the customer's info.
     * @param txRowid: stored transaction record ID
     * @param qid: customer account code
     * @param code: customer's rCard security code
     * @param txJson: json parameters from the completed transaction on the server
     */
    private void completeOldTx(long txRowid, String qid, String code, Json txJson) {
        Json idJson = null;

        Q q = A.db.oldCustomer(qid);
        if (q == null) {
            List<NameValuePair> pairs = A.auPair(null, "op", "identify");
            A.auPair(pairs, "member", qid);
            A.auPair(pairs, "code", code);
            idJson = A.apiGetJson(rCard.qidRegion(qid), pairs, false); // get json-encoded info
            byte[] image = A.apiGetPhoto(qid, code, false);
            if (idJson == null || image == null || image.length == 0) return; // try again later
            A.db.saveCustomer(qid, image, idJson);
        } else  q.close();

        db.completeTx(txRowid, txJson);
    }

    /**
     * Record a reversing transaction from the server.
     * @param rowid1: original (local) transaction record ID
     * @param txid1: original transaction ID on server
     * @param values2: most parameters for the (local) reversing transaction
     * @param txid2: reversing transaction ID on server
     * @param created2: reversing transaction creation time
     */
    private void reverseTx(long rowid1, String txid1, ContentValues values2, String txid2,  String created2) {
        values2.put("txid", txid2); // remember record ID of reversing transaction on server
        values2.put("status", A.TX_DONE); // mark offsetting transaction done
        values2.put("created", created2); // reversal date
        values2.put("amount", A.fmtAmt(-values2.getAsDouble("amount"), false)); // negate

        db.beginTransaction();
        db.insert("txs", values2); // record offsetting transaction from server
        db.changeStatus(rowid1, A.TX_DONE, Long.valueOf(txid1)); // mark original transaction done
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    private void sleep(int seconds) {
        SystemClock.sleep(1000 * seconds);}
}
