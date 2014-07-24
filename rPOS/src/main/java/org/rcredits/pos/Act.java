package org.rcredits.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;

import java.util.List;
import java.util.Map;

/**
 * An Activity class extension that includes some utility methods.
 * The convention in this project is to define act = this in each instance of Act, then
 *   call these methods using "act." rather than "this.".
 */
public class Act extends Activity {
    private final Act act = this;
    private static ProgressDialog progressDlg; // for standard progress spinner
    private AlertDialog alertDialog;

//    @Override        public void onBackPressed() {

    /**
     * Show a short message briefly (2 secs) -- or longer (3.5 secs) for longer messages
     * @param message: the message to show
     */
    public void mention(String message) {
        int duration = (message.length() < 30) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(act, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    public void mention(int res) {mention(A.t(res));}

    private class doNothing implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
        }
    }

    /**
     * Show a customized dialog with an OK button, and handle it.
     * @param title: large, very short title to show to the right of the icon
     * @param icon: conceptual graphic to show at the top of the message
     * @param message: what to say
     * @param ok: callback to do when user presses OK (null if nothing)
     * @param cancelable: <include a Cancel button>
     * @param cancel: callback to do when user presses Cancel (null if nothing)
     */
    private void say(String title, int icon, String message, DialogInterface.OnClickListener ok,
                     boolean cancelable, DialogInterface.OnClickListener cancel) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(act);

/*        DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel(); // default to do nothing
            }
        }; */
        if (ok == null) ok = new doNothing();
        if (cancel == null) cancel = new doNothing();

        alertDialogBuilder
                .setTitle(title)
                .setMessage(message)
                .setIcon(icon)
                .setCancelable(cancelable)
                .setPositiveButton("OK", ok);
        if (cancelable) alertDialogBuilder.setNegativeButton("Cancel", cancel);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void say(String title, int icon, String message, DialogInterface.OnClickListener ok) {
        say(title, icon, message, ok, false, null);
    }

    public void sayFail(String message) {
        A.failMessage = message;
        act.finish();
        restart();
    }
    public void sayFail(int res) {sayFail(A.t(res));}

    public void sayError(String message,  DialogInterface.OnClickListener ok) {
        say("Error", R.drawable.alert_red, message, ok);
    }
    public void sayError(int res,  DialogInterface.OnClickListener ok) {sayError(A.t(res), ok);}

    public void sayOk(String title, String message,  DialogInterface.OnClickListener ok) {
        say(title, R.drawable.smile_icon, message, ok);
    }
    public void sayOk(String title, int res,  DialogInterface.OnClickListener ok) {sayOk(title, A.t(res), ok);}

    public void askOk(String message, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener cancel) {
        say("Confirm", R.drawable.question_icon, message, ok, true, cancel);
    }
    public void askOk(String message,  DialogInterface.OnClickListener ok) {
        say("Confirm", R.drawable.question_icon, message, ok, true, null);
    }
    public void askOk(int res,  DialogInterface.OnClickListener ok) {askOk(A.t(res), ok);}

    /**
     * Show a standard "in progress" message.
     * PROBLEM: this doesn't show up until it's canceled (ie never). If the call with false is omitted, it doesn't show up until the post operation is completed.
     * @param go: true to start, false to stop
     */
    public void progress(boolean go) {
        if (go) {
            progressDlg = ProgressDialog.show(act, "In Progress", "Contacting server...");
        } else {
            progressDlg.dismiss();
        }
    }

    /**
     * End all processes in this thread and go back to scanning cards.
     * (note that getApplicationContext() and startActivity() are activity methods)
     */
    public void restart() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // end all other activities
        startActivity(intent); // restart
    }

    /**
     * Return a string to the parent activity.
     * @param resultName: name of the returned value
     * @param result: the value to return
     */
    public void returnIntentString(String resultName, String result) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(resultName, result);
        act.setResult(Activity.RESULT_OK, returnIntent);
        act.finish();
    }

    /**
     * After requesting a transaction, handle the server's response.
     * @param json: json-format parameter string returned from server
     */
    public void afterTx(final Json json) {
        act.progress(false);

        if (json == null) { // no response from server -- ask cashier about doing it offline, if we haven't yet
            if (A.positiveId) {
                act.askOk(A.nn(A.httpError) + " " + A.t(R.string.try_offline), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        act.offlineTx();
                    }
                });
            } else act.offlineTx();
            return;
        };

        String message = json.get("message");
        A.balance = A.balanceMessage(A.customerName, json); // null if secret or no balance was returned

        if (json.get("ok").equals("1")) {
            A.undo = json.get("undo");
            A.lastTx = json.get("txid");
            A.deb("afterTx lastTx=" + A.lastTx + " lastTxRow=" + A.lastTxRow);
            A.db.completeTx(A.lastTxRow, json);

            act.sayOk("Success!", message, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    act.restart();
                }
            });
        } else act.sayError(message, null);
    }

    /**
     * Store the transaction for later.
     */
    public void offlineTx() {
        A.balance = null;
        A.lastTx = null;
        boolean charging = A.duPair("op").equals("charge");

        if (charging) {
            String customer = A.db.customerName(A.duPair("customer"));
            String amount = A.duPair("amount");
            String tofrom = (Double.valueOf(amount) < 0) ? "to" : "from";
            amount = A.fmtAmt(amount.replace("-", ""), true);
            A.undo = String.format("Undo transfer of %s %s %s?", amount, tofrom, customer);
        } else A.undo = null;

        A.db.changeStatus(A.lastTxRow, charging ? A.TX_OFFLINE : A.TX_CANCEL, null);

        String msg = String.format("The transaction has been %s.", charging ? "stored" : "canceled");
        act.sayOk("Done!", msg, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.restart();
            }
        });
    }

    /**
     * Submit and handle a transaction request, in the background.
     */
    public class Tx extends AsyncTask<List<NameValuePair>, Void, Json> {
        @Override
        protected Json doInBackground(List<NameValuePair>... pairss) {
            List<NameValuePair> pairs = pairss[0];
            if (A.demo) {
                SystemClock.sleep(1000);
                if (A.duPair("op", pairs).equals("charge")) {
                    String amount = A.duPair("amount", pairs);
                    if (amount == null) amount = "23";
                    return Json.make("{'ok':'1','message':'You charged Susan Shopper $AMOUNT (reward: 10%).','tx':'4069','balance':'Customer: Susan Shopper\\n\\nBalance: $285.86\\nSpendable: $284.94\\nTradable for cash: $154.91\\n\\nWe just charged $AMOUNT.','undo':'Undo transfer of $AMOUNT from Susan Shopper?'}".replaceAll("AMOUNT", amount));
                } else return Json.make("{'ok':'1','message':'Transaction has been reversed.','balance':'Customer: Susan Shopper\\n\\nBalance: $306.56\\nTradable for cash: $178.83'}"); // undo
            } else {

                ContentValues values = new ContentValues();
                A.auPair(pairs, "created", String.valueOf(A.now()));
                for (String k : DbHelper.TXS_FIELDS.split(" ")) values.put(k, A.duPair(k, pairs));
                values.put("status", A.TX_PENDING);
                values.put("agent", A.agent); // gets added to pairs in A.post (not yet)
                values.put("txid", A.duPair("code")); // temporarily store card code (from identify op) in txid field
                for (Map.Entry<String, Object> k : values.valueSet()) A.deb(String.format("Tx value %s: %s", k.getKey(), k.getValue()));
                A.lastTxRow = A.db.insert("txs", values);
                A.deb("Tx A.lastTxRow just set to " + A.lastTxRow);
                Q r = A.db.q("SELECT rowid, * FROM txs", new String[] {});
                if (r != null) {A.deb("Tx txid=" + r.getString("txid") + " customer=" + r.getString("customer")); r.close();}
                else A.deb("r is null");

                return A.apiGetJson(A.region, pairs, true);
            }
        }

        @Override
        protected void onPostExecute(Json json) {act.afterTx(json);}
    }
}
