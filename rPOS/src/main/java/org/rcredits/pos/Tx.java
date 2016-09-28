package org.rcredits.pos;

import android.content.DialogInterface;
import android.os.Looper;

/**
 * Created by William on 9/22/2016.
 */
public class Tx implements Runnable {
    private Long rowid;
    private boolean photoId;
    private final ResultHandler handle;
    private Pairs rpcPairs = null; // data to post
    private final static int MAX_DIGITS_OFFLINE = 5; // maximum $999.99 transaction offline
    public final static int OK = 0;
    public final static int ERROR = 1;
    public final static int FAIL = 2;

    public interface ResultHandler {boolean done(int action, String msg);}

    public Tx(Long rowid, boolean photoId, ResultHandler handle){
        this.rowid = rowid;
        this.photoId = photoId;
        this.handle = handle;
    }

    @Override
    public void run() {
        A.log(0);
        Looper.prepare();
        Json json;
        rpcPairs = A.db.txPairs(rowid);

        if (Integer.valueOf(rpcPairs.get("force")) == A.TX_PENDING) {
            A.log("completing pending tx: " + rowid);
            if (photoId) rpcPairs.add("photoid", "1");
            json = (A.positiveId) ? A.apiGetJson(A.region, rpcPairs) : null;
        } else {
            A.log("canceling tx " + rowid);
            json = A.db.cancelTx(rowid, rpcPairs.get("agent"));
        }

        if (json == null) offlineTx(); else afterTx(json);
        Looper.loop();
        A.log(9);
    }

    /**
     * After requesting a transaction, handle the server's response.
     * @param json: json-format parameter string returned from server
     */
    public boolean afterTx(Json json) {
        A.log(0);
        String message = json.get("message");
        A.balance = A.balanceMessage(A.customerName, json); // null if secret or no balance was returned
        if (A.selfhelping() && A.balance != null) message += " Your new balance is " + A.fmtAmt(json.get("balance"), true) + ".";

        if (json.get("ok").equals("1")) {
            A.undo = json.get("undo");
            if (A.undo != null && (A.undo.equals("") || A.undo.matches("\\d+"))) A.undo = null;

            A.db.completeTx(A.lastTxRow, json); // mark tx complete in db (unless deleted)
            return handle.done(OK, message);
        } else {
            A.log("tx failed; so deleting row " + A.lastTxRow);
            A.db.delete("txs", A.lastTxRow); // remove the rejected transaction
            A.lastTxRow = null;
            A.undo = null;
            return handle.done(FAIL, message);
        }
    }

    /**
     * Store the transaction for later.
     */
    public boolean offlineTx() {
        A.log("offline rpcPairs=" + rpcPairs.show());
        String msg;
        String amount = rpcPairs.get("amount");
        boolean positive = (amount.indexOf("-") < 0);
        amount = A.fmtAmt(amount.replace("-", ""), true);
        if (amount.length() > MAX_DIGITS_OFFLINE + (positive ? 1 : 2)) { // account for "." and "-"
            return handle.done(ERROR, "That is too large an amount for an offline transaction (your internet connection is not available).");
        }
        boolean charging = rpcPairs.get("force").equals("" + A.TX_PENDING); // as opposed to TX_CANCEL
        String qid = rpcPairs.get("member");
        String customer = A.db.customerName(qid);
//        if (A.empty(customer)) customer = "member " + qid;
        A.balance = null;
        String tofrom = (charging ^ positive) ? "to" : "from";
        String action = (charging ^ positive) ? "credited" : "charged";

        if (charging) { // set up undo text, if charging
            msg = String.format("You %s %s $%s.", action, customer, amount);
            A.undo = String.format("Undo transfer of $%s %s %s?", amount, tofrom, customer);
            A.db.changeStatus(A.lastTxRow, A.TX_OFFLINE, null);
//            if (!A.db.getField("status", "txs", A.lastTxRow).equals(A.TX_OFFLINE + "")) act.die("system error: status not set");
        } else {
            msg = String.format("The transaction has been canceled. You transferred $%s back %s %s.",
                    amount, tofrom, customer);
            A.undo = null;
        }

        A.log(9);
        return handle.done(OK, "OFFLINE " + msg + A.t(R.string.connect_soon));
    }

}
