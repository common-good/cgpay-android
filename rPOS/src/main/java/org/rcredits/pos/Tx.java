package org.rcredits.pos;

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

    Tx (Long rowid, boolean photoId, ResultHandler handle){
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
            json = (A.positiveId) ? A.apiGetJson(A.b.region(), rpcPairs) : null;
        } else {
            A.log("canceling tx " + rowid);
            json = A.db.cancelTx(rowid, rpcPairs.get("agent"));
        }

        if (json == null) offlineTx(); else afterTx(json);
        A.b.getTime(null); // check in with server after each transaction, whether successful or not
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

        if (json.get("ok").equals("1")) {
            A.db.completeTx(this.rowid, json); // mark tx complete in db (unless deleted)
            A.balance = A.balanceMessage(A.customerName, json); // null if secret or no balance was returned
            if (A.selfhelping() && A.balance != null) {
                    int i = A.balance.indexOf("\n");
                    if (i > -1) message += A.balance.substring(i);
//                if (A.selfhelping() && A.balance != null) message += " Your new balance is $" + A.fmtAmt(json.get("balance"), true) + ".";
            }
            A.undo = json.get("undo");
            if (A.empty(A.undo) || A.undo.matches("\\d+")) {
                A.noUndo();
            } else A.undoRow = this.rowid;
            return handle.done(OK, message);
        } else {
            A.log("tx failed; so deleting row " + this.rowid);
            A.db.delete("txs", this.rowid); // remove the rejected transaction
            A.noUndo();
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
            if (A.selfhelping()) {
                msg = String.format("You paid %s %s.", A.agentName, amount);
            } else msg = String.format("You %s %s $%s.", action, customer, amount);
            A.db.changeStatus(this.rowid, A.TX_OFFLINE, null);
            A.undo = String.format("Undo transfer of $%s %s %s?", amount, tofrom, customer);
            A.undoRow = this.rowid;
        } else {
            msg = String.format("The transaction has been canceled. You transferred $%s back %s %s.",
                    amount, tofrom, customer);
            A.noUndo();
        }

        A.log(9);
        return handle.done(OK, "OFFLINE " + msg + A.t(R.string.connect_soon));
    }

}
