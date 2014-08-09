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
import android.view.View;
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
    private ProgressDialog progressDlg; // for standard progress spinner
    private AlertDialog alertDialog;

    public void goBack(View v) {onBackPressed();}

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
        if (progressDlg != null) progressDlg.dismiss();
        if (go) {
            progressDlg = ProgressDialog.show(act, "In Progress", "Contacting server...");
        } else progressDlg = null;
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

    public void start(Class cls) {
        Intent intent = new Intent(act, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(intent);
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
     * Turn wifi on or off for this app (useful for saving time when out of range for a long time).
     */
    public void setWifi(boolean wifi) {
        A.wifi = wifi;
        act.sayOk("Wifi", wifi ? R.string.wifi_on : R.string.wifi_off, null);
    }

    public void showTables(View v) {if (A.testing) sayOk("Records", A.db.showCust() + "\n\n" + A.db.showTxs(), null);}

    /**
     * Provide wifi toggle shortcuts when testing (clicking +id/test, +id/customer_place, or +id/amount).
     * @param v
     */
    public void setWifi(View v) {if (A.testing) setWifi(!A.wifi);}

    /**
     * After requesting a transaction, handle the server's response.
     * @param json: json-format parameter string returned from server
     */
    public void afterTx(final Json json) {
        act.progress(false);

        String message = json.get("message");
        A.balance = A.balanceMessage(A.customerName, json); // null if secret or no balance was returned

        if (json.get("ok").equals("1")) {
            A.undo = json.get("undo");
            if (A.undo != null && (A.undo.equals("") || A.undo.matches("\\d+"))) A.undo = null;
            A.db.completeTx(A.lastTxRow, json); // mark tx complete in db (unless deleted)

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
        String msg;
        boolean charging = A.rpcPairs.get("force").equals("" + A.TX_PENDING);
        String qid = A.rpcPairs.get("member");
        String customer = A.db.customerName(qid);
        A.balance = A.demo ? A.balanceMessage(customer, qid) : null;
        String amount = A.rpcPairs.get("amount");
        boolean positive = (amount.indexOf("-") < 0);
        amount = A.fmtAmt(amount.replace("-", ""), true);
        String tofrom = (charging ^ positive) ? "to" : "from";
        String action = (charging ^ positive) ? "credited" : "charged";

        if (charging) { // set up undo text, if charging
            msg = String.format("You %s %s $%s.", action, customer, amount);
            A.undo = String.format("Undo transfer of $%s %s %s?", amount, tofrom, customer);
            A.db.changeStatus(A.lastTxRow, A.TX_OFFLINE, null);
        } else {
            msg = String.format("The transaction has been canceled. You transferred $%s back %s %s.",
                    amount, tofrom, customer);
            A.undo = null;
        }

        if (!A.wifi) msg = "OFFLINE " + msg + A.t(R.string.connect_soon); // in demo mode maybe pretending wifi is ON
        act.sayOk("Done!", msg, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.restart();
            }
        });
    }

    /**
     * Submit and handle a transaction request, in the background (but called from the UI).
     * If the status of the transaction is pending, complete it.
     * If the status is TX_DONE, reverse it.
     */
    public class Tx extends AsyncTask<Long, Void, Json> {
        @Override
        protected Json doInBackground(Long... txRows) {
            Long rowid = txRows[0];

            Pairs pairs = A.db.txPairs(rowid);

            if (Integer.valueOf(pairs.get("force")) == A.TX_PENDING) {
                if (A.setTime(A.getTime())) A.db.fixTxTime(rowid, pairs); // sync creation date with server time
                return A.apiGetJson(A.region, pairs, true);
            } else return A.db.cancelTx(rowid, true);
        }

        @Override
        protected void onPostExecute(Json json) {
            if (json == null) act.offlineTx(); else act.afterTx(json);
        }

/*            if (A.positiveId) {
                act.askOk(A.nn(A.httpError) + " " + A.t(R.string.try_offline), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        act.offlineTx();
                    }
                });
            } else act.offlineTx();
            return;
        }; */
    }
}
