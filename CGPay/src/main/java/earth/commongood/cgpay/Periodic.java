package earth.commongood.cgpay;

import android.os.SystemClock;

/**
 * Do stuff periodically in the background:
 *  - Reconcile offline transactions with server
 *  - Synchronize the system clock with the server's
 *  - Check for available updates (included in clock sync)
 * cancel by setting A.stop = true;
 */
public class Periodic implements Runnable {
    private B b;
    private int period;
    private final static int OLD_TX_SECS = 60; // ok to upload or cancel any transaction older than this
    private final static Long GETTIME_INTERVAL = 12 * 60 * 60L; // check in with server at least twice a day

    Periodic (B b) {
        this.b = b;
        this.period = b.test ? (A.fakeScan ? 20 : A.TEST_PERIOD) : A.REAL_PERIOD;
    }

    @Override
    public void run() {
        A.log(0);
        Q q;
        String[] params;
        final String sql = "SELECT rowid, * FROM txs WHERE status<>? AND created<?";

        while (!A.stop) {
            if (b.doReport) {b.doReport = false; b.getTime("report: " + A.sysLog());}
            if (A.now() - b.lastGetTime > (A.fakeScan ? 20 : GETTIME_INTERVAL)) A.setTime(b.getTime(null));

            params = new String[] {String.valueOf(A.TX_DONE), String.valueOf(A.now() - OLD_TX_SECS)};
            q = b.db.q(sql, params);
            if (q != null) {
                do { // for each non-current transaction in limbo [NOTE: do not use "continue" in do...while]
                    reconcile(q);
                    sleep(1); // breathe between db operations, to make sure UI runs fast
                    if (q.invalid()) q = b.db.q(sql, params); // refresh, if invalidated
                } while (q != null && q.moveToNext());
//            } while (q != null && q.moveToNext() && !isCancelled());

                q.close();
            }
            sleep(period);
        }

//    return 0;
    }

    /**
     * Reconcile the given transaction with the server.
     * @param q: pointer to transaction record
     * NOTE: q gets invalidated by the call to apiGetJson().
     *         So be done with it sooner, to avoid "StaleDataException: Access closed cursor"
     */
    private void reconcile(Q q) {
        Pairs pairs;
        long rowid = q.rowid();
        int status = q.getInt("status");
        A.log("reconcile txid=" + q.getString("txid") + " status=" + status + " amount=" + q.getString("amount"));

        if (status == A.TX_CANCEL || status == A.TX_PENDING) { // change pending to cancel because cashier assumed it failed
            b.report("discovered pending tx row " + rowid);
            b.db.cancelTx(rowid, null);
        } else if (status == A.TX_OFFLINE) { // tell server about a completed transaction
            pairs = b.db.txPairs(rowid).add("offline", "1");
            Json json = A.apiGetJson(b.region(), pairs);
            if (json != null) {
                String code = q.getString(DbSetup.TXS_CARDCODE); // card code was stored temporarily
                String qid = q.getString("member");
                if (json.get("ok").equals("1")) completeOldTx(rowid, qid, code, json);
            }
        } else b.report("bad status:" + status);
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

        Q q = b.db.oldCustomer(qid);
        if (q == null) {
            Pairs pairs = new Pairs("op", "identify");
            pairs.add("member", qid);
            pairs.add("code", code);
            idJson = A.apiGetJson(rCard.qidRegion(qid), pairs); // get json-encoded info
            byte[] image = A.apiGetPhoto(qid, code);
            if (idJson == null || image == null || image.length == 0) return; // try again later

            if (idJson.get("ok").equals("0")) { // tx refused by server
                b.db.delete("txs", txRowid); // forget it
                return; // that's all (server will tell company and mark customer bad if appropriate)
            }

            try {
                b.db.saveCustomer(qid, image, code, idJson);
            } catch (Db.NoRoom e) {A.sysMessage = A.t(R.string.no_room);} // no room to store customer record; try later
        } else  q.close(); // we already have customer info

        b.db.completeTx(txRowid, txJson);
    }

    /**
     * Do nothing here for the indicated number of seconds, while other threads continue.
     * @param seconds
     */
    private void sleep(int seconds) {
        SystemClock.sleep(1000 * seconds);
    }
}
