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
    Db db;

    @Override
    protected Integer doInBackground(String... zot) {
        Q q;
        final String sql = "SELECT rowid, * FROM txs WHERE status<>? AND created<?";
        db = A.db; // use a private db pointer, in case user switches mode

        A.setTime(A.getTime(null)); // check for updates first thing

        while (!isCancelled()) {
            String[] params = new String[] {String.valueOf(A.TX_DONE), String.valueOf(A.now() - A.period)};
            q = db.q(sql, params);
            if (q != null) {
                do { // for each non-current transaction in limbo [NOTE: do not use "continue" in do...while]
                    reconcile(q);
                    sleep(1); // breathe between db operations, to make sure UI runs fast
                    if (q.invalid()) q = db.q(sql, params); // refresh, if invalidated
                } while (q != null && q.moveToNext() && !isCancelled());

                q.close();
            }
            sleep(A.period);
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
        long rowid = q.rowid();
        int status = q.getInt("status");
        A.log("reconcile txid=" + q.getString("txid") + " status=" + status + " amount=" + q.getString("amount"));

        if (status == A.TX_CANCEL || status == A.TX_PENDING) { // change pending to cancel because cashier assumed it failed
            A.log("discovered pending tx row " + rowid);
            db.cancelTx(rowid, null);
        } else if (status == A.TX_OFFLINE) { // tell server about a completed transaction
            String code = q.getString(DbHelper.TXS_CARDCODE); // card code was stored temporarily
            String qid = q.getString("member");

            Json json = A.apiGetJson(A.region, A.db.txPairs(rowid));
            if (json != null && json.get("ok").equals("1")) {
                completeOldTx(rowid, qid, code, json);
            }
        } else A.log("bad status:" + status);
    }

    /**
     * Complete the stored transaction, if we have -- or can get -- the customer's info.
     * @param txRowid: stored transaction record ID
     * @param qid: customer account code
     * @param code: customer's rCard security code
     * @param txJson: json parameters from the completed transaction on the server
     */
    private void completeOldTx(long txRowid, String qid, String code, Json txJson) {
        A.log("completing old " + txRowid + " qid=" + qid + " code=? txJson=" + txJson.toString());
        Json idJson = null;

        Q q = A.db.oldCustomer(qid);
        if (q == null) {
            Pairs pairs = new Pairs("op", "identify");
            pairs.add("member", qid);
            pairs.add("code", code);
            idJson = A.apiGetJson(rCard.qidRegion(qid), pairs); // get json-encoded info
            byte[] image = A.apiGetPhoto(qid, code);
            if (idJson == null || image == null || image.length == 0) return; // try again later
            try {
                A.db.saveCustomer(qid, image, idJson);
            } catch (Db.NoRoom e) {return;} // no room to store customer record; try later
        } else  q.close(); // we already have customer info

        db.completeTx(txRowid, txJson);
    }

    private void sleep(int seconds) {
        SystemClock.sleep(1000 * seconds);}
}
