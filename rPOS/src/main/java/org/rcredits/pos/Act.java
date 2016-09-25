package org.rcredits.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import java.text.NumberFormat;

/**
 * An Activity class extension that includes some utility methods.
 * The convention in this project is to define act = this in each instance of Act, then
 *   call these methods using "act." rather than "this.".
 */
public class Act extends Activity {
    protected final Act act = this;
    protected ProgressDialog progressDlg; // for standard progress spinner
    private AlertDialog alertDialog;
    protected String photoId; // got customer's photo ID number (null or "1", used in TxActivity and Act.Tx)
    private final String YES_OR_NO = "Yes or No";
    private final static int TIMEOUT = 10; // number of minutes before activity times out
    private CountDownTimer timer = null; // timeout timer
    private boolean onTop = false; // activity is visible
    public static String name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act.name = act.getLocalClassName();
        A.log(act.name + " onCreate");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        A.log(act.name + " onPause");
        onTop = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        A.log(act.name + " onResume");
        onTop = true;

        if (timer == null) timer = new CountDownTimer(TIMEOUT * 60 * 1000, 1000) {

            public void onTick(long millisUntilFinished) {}

            public void onFinish() {
                timer.cancel();
                if (onTop) {
                    A.balance = A.undo = null; // don't show these too long
                    if (isMain()) {
                        onResume();
                    } else {
                        A.log("rCredits activity timed out.");
                        goHome(t(R.string.timed_out));
                    }
                }
            }
        };
        timer.start();
    }

    /**
     * Terminate activity on Back Button press.
     */
    @Override
    public void onBackPressed() {act.finish();}

    public void goBack(View v) {onBackPressed();}

    public String t(int resource) {return A.t(resource);}

    private boolean isMain() {return act.name.equals("MainActivity");}

    /**
     * Show a short message briefly (2 secs) -- or longer (3.5 secs) for longer messages
     * @param message: the message to show
     */
    public void mention(String message) {
//        int duration = (message.length() < 30) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(act, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    public void mention(int res) {mention(t(res));}

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
        A.log("SAY " + title + ": " + message);

        alertDialogBuilder
                .setTitle(title)
                .setMessage(message)
                .setIcon(icon)
                .setCancelable(cancelable)
                .setPositiveButton(title.equals(YES_OR_NO) ? "Yes" : "OK", ok);
        if (cancelable) alertDialogBuilder.setNegativeButton(title.equals(YES_OR_NO) ? "No" : "Cancel", cancel);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void say(String title, int icon, String message, DialogInterface.OnClickListener ok) {
        say(title, icon, message, ok, false, null);
    }

    public void sayFail(String message) {
        A.log("FAIL: " + message);
        A.failMessage = message;
        goHome();
    }

    public void sayFail(int res) {sayFail(t(res));}

    public void sayError(String message,  DialogInterface.OnClickListener ok) {
        say("Error", R.drawable.alert_red, message, ok);
    }
    public void sayError(int res,  DialogInterface.OnClickListener ok) {
        sayError(t(res), ok);}

    public void sayOk(String title, String message,  DialogInterface.OnClickListener ok) {
        say(title, R.drawable.smile_icon, message, ok);
    }
    public void sayOk(String title, int res,  DialogInterface.OnClickListener ok) {sayOk(title, t(res), ok);
    }

    public void askOk(String title, String message, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener cancel) {
        say(title, R.drawable.question_icon, message, ok, true, cancel);
    }
    public void askOk(String message,  DialogInterface.OnClickListener ok) {askOk("Confirm", message, ok, null);}
    public void askOk(int res, DialogInterface.OnClickListener ok) {
        askOk(t(res), ok);
    }

    public void askYesNo(String message, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener cancel) {
        askOk(YES_OR_NO, message, ok, cancel);
    }
    public void askYesNo(String message, DialogInterface.OnClickListener ok) {askYesNo(message, ok, null);}

    /**
     * Show a standard "in progress" message.
     * @param go: true to start, false to stop
     */
    public void progress(boolean go) {
	    if (progressing()) try {
            progressDlg.dismiss(); // use .cancel() instead?
	    } catch (final IllegalArgumentException e) {
		    A.log("dismissing - activity vanished"); // ignore (work around Android bug)
		}
		
        if (go) {
            progressDlg = ProgressDialog.show(act, "In Progress", "Contacting server...");
        } else progressDlg = null;
    }
    
    public boolean progressing() {
        try {
            return (progressDlg != null && progressDlg.isShowing());
        } catch (final IllegalArgumentException e) {return false;} // work around Android bug
    }

    /**
     * End all processes in this thread and go back to scanning cards.
     * (note that getApplicationContext() and startActivity() are activity methods)
     */
    public void goHome(String msg) {
        A.log(0);
        if (msg != null) A.serverMessage = msg;
        progress(false);
        if (timer != null) timer.cancel();
//        A.doPeriodic(); // restart background uploads, just in case

        if (isMain()) {
            onResume();
        } else {
            act.finish();
            Intent intent = new Intent(A.context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // end all other activities
            act.startActivity(intent); // restart
        }
    }
    public void goHome() {goHome(null);}

    /**
     * Launch a new activity.
     * @param cls: the activity to launch
     * @param id: identifier for the activity when it returns a result (0 if no value returned)
     * @param k: name of value to pass to the activity (null if none)
     * @param v: value to pass to the activity (null if none)
     */
    public void start(Class cls, int id, String k, String v) {
        A.log("starting " + cls.getName());
        Intent intent = new Intent(act, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        if (k != null) A.putIntentString(intent, k, v);
        if (id == 0) startActivity(intent); else startActivityForResult(intent, id);
    }

    public void start(Class cls, int id) {act.start(cls, id, null, null);}

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
        A.wifiOff = !wifi;
        int msg = wifi ? (A.connected() ? R.string.wifi_on : R.string.wifi_unavailable) : R.string.wifi_off;
        act.sayOk("Wifi", msg, null);
    }

    public void showTables(View v) {if (A.testing) sayOk("Records", A.db.showCust() + "\n\n" + A.db.showTxs(), null);}

    /**
     * Provide wifi toggle shortcuts when testing (clicking +id/test, +id/customer_place, or +id/amount).
     * @param v
     */
    public void setWifi(View v) {if (A.testing) setWifi(A.wifiOff);}

    public class handleTxResult implements Tx.ResultHandler {
        public handleTxResult() {}

        @Override
        public boolean handle(int action0, String msg0) {
            final int action = action0;
            final String msg = msg0;

            act.runOnUiThread(new Runnable() {
                public void run() {
                    A.log(0);
                    act.progress(false);
                    if (action == Tx.OK) {
                        act.sayOk("Success!", msg, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                //                    dialog.cancel();
                                act.goHome();
                            }
                        });
                    } else if (action == Tx.FAIL || act.isMain()) {
                        act.sayFail(msg);
                    } else act.sayError(msg, null);
                }
            });
            return true;
        }
    }

    /**
     * After requesting a transaction, handle the server's response.
     * @param json: json-format parameter string returned from server
     *//*
    public void afterTx(Json json) {
        String message = json.get("message");
        A.balance = A.balanceMessage(A.customerName, json); // null if secret or no balance was returned
        if (A.selfhelping() && A.balance != null) message += " Your new balance is " + A.fmtAmt(json.get("balance"), true) + ".";

        if (json.get("ok").equals("1")) {
            A.undo = json.get("undo");
            if (A.undo != null && (A.undo.equals("") || A.undo.matches("\\d+"))) A.undo = null;

// seems to fail (probably because cashiers don't press OK)    A.db.beginTransaction();
            A.log("before complete of " + A.lastTxRow);
            A.db.completeTx(A.lastTxRow, json); // mark tx complete in db (unless deleted)
            A.log("after complete of " + A.lastTxRow);

            String status = A.db.getField("status", "txs", A.lastTxRow); // make sure it succeeded (remove this?)
            if (status == null) {
                A.log("null status -- tx was deleted from db?");
            } else if (!status.equals(A.TX_DONE + "")) act.die("status not set");

            act.sayOk("Success!", message, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    A.log("after OK of " + A.lastTxRow);
//                    A.db.setTransactionSuccessful();
//                    A.db.endTransaction();
                    dialog.cancel();
                    goHome();
                }
            });
        } else {
            A.log("tx failed; so deleting row " + A.lastTxRow);
            A.db.delete("txs", A.lastTxRow); // remove the rejected transaction
            A.lastTxRow = null;
            A.undo = null;
            if (act.getLocalClassName().equals("MainActivity")) act.sayFail(message); else act.sayError(message, null);
        }
    }

    /**
     * Store the transaction for later.
     *//*
    public void offlineTx() {
        String msg;
        A.log("offline rpcPairs=" + rpcPairs.show());
        String amount = rpcPairs.get("amount");
        boolean positive = (amount.indexOf("-") < 0);
        amount = A.fmtAmt(amount.replace("-", ""), true);
        if (amount.length() > MAX_DIGITS_OFFLINE + (positive ? 1 : 2)) { // account for "." and "-"
            act.sayError("That is too large an amount for an offline transaction (your internet connection is not available).", null);
            return;
        }
        boolean charging = rpcPairs.get("force").equals("" + A.TX_PENDING); // as opposed to TX_CANCEL
        String qid = rpcPairs.get("member");
        String customer = A.db.customerName(qid);
//        A.balance = A.demo ? A.balanceMessage(customer, qid) : null;
        A.balance = null;
        String tofrom = (charging ^ positive) ? "to" : "from";
        String action = (charging ^ positive) ? "credited" : "charged";

        if (charging) { // set up undo text, if charging
            msg = String.format("You %s %s $%s.", action, customer, amount);
            A.undo = String.format("Undo transfer of $%s %s %s?", amount, tofrom, customer);
            A.db.changeStatus(A.lastTxRow, A.TX_OFFLINE, null);
            if (!A.db.getField("status", "txs", A.lastTxRow).equals(A.TX_OFFLINE + "")) act.die("status not set");
        } else {
            msg = String.format("The transaction has been canceled. You transferred $%s back %s %s.",
                    amount, tofrom, customer);
            A.undo = null;
        }

        msg = "OFFLINE " + msg + t(R.string.connect_soon);
        act.sayOk("Done!", msg, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.goHome();
            }
        });
    }

    /**
     * Submit and handle a transaction request, in the background (but called from the UI).
     * If the status of the transaction is pending, complete it.
     * If the status is TX_DONE, reverse it.
     *//*
    public class Tx0 extends AsyncTask<Long, Void, Json> {
        @Override
        protected Json doInBackground(Long... txRows) {
            Long rowid = txRows[0];

            rpcPairs = A.db.txPairs(rowid);

            if (Integer.valueOf(rpcPairs.get("force")) == A.TX_PENDING) {
                A.log("completing pending tx: " + rowid);
// (Do this on identifying instead)  if (A.setTime(A.getTime(null))) A.db.fixTxTime(rowid, rpcPairs); // sync creation date with server time
                //A.db.fixTxTime(rowid, rpcPairs); // sync creation date with server time NO! might cause dups
                if (photoId != null) rpcPairs.add("photoid", photoId);
                return (A.positiveId) ? A.apiGetJson(A.region, rpcPairs) : null;
            } else {
                A.log("canceling tx " + rowid);
                return A.db.cancelTx(rowid, rpcPairs.get("agent"));
            }
        }

        @Override
        protected void onPostExecute(Json json) {
            A.log("Tx PostExecute");
            act.progress(false);
            if (json == null) {
                act.offlineTx();
            } else act.afterTx(json);
        }

/*            if (A.positiveId) {
                act.askOk(A.nn(A.httpError) + " " + t(R.string.try_offline), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        act.offlineTx();
                    }
                });
            } else act.offlineTx();
            return;
        }; */
//    }
}
