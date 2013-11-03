package org.rcredits.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.KeyEvent;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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

    /*
    public Intent getIntent(Class<?> class) {
        Intent intent = new Intent(class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        return intent;
    }*/

    /**
     * Show a short message briefly (2 secs) -- or longer (3.5 secs) for longer messages
     * @param message: the message to show
     */
    public void mention(String message) {
        int duration = (message.length() < 30) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast.makeText(act, message, duration).show();
    }

    /**
     * Show a customized dialog with an OK button, and handle it.
     * @param title: large, very short title to show to the right of the icon
     * @param icon: conceptual graphic to show at the top of the message
     * @param message: what to say
     * @param listener: callback to do when user presses OK
     */
    private void say(String title, int icon, String message,
                            DialogInterface.OnClickListener listener, boolean cancelable) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(act);
        if (listener == null) listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel(); // default to do nothing
            }
        };
        alertDialogBuilder
                .setTitle(title)
                .setMessage(message)
                .setIcon(icon)
                .setCancelable(cancelable)
                .setPositiveButton("OK", listener);
        // .setNegativeButton("No", new DialogInterface.OnClickListener() {...
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void sayFail(String message) {
        A.failMessage = message;
        act.finish();
        restart();
    }

    public void sayError(String message,  DialogInterface.OnClickListener listener) {
        say("Oops!", R.drawable.alert_red, message, listener, false);
    }

    public void sayOk(String title, String message,  DialogInterface.OnClickListener listener) {
        say(title, R.drawable.smile_icon, message, listener, false);
    }

    public void askOk(String message,  DialogInterface.OnClickListener listener) {
        say("Confirm", R.drawable.question_icon, message, listener, true);
    }

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
     * Process the result from QR scan:
     * Get agent, agentName, customer (etc), device, success, message from server
     * @param rcard: info read from the card
     */
    public void onScan(rCard rcard) {
        List<NameValuePair> pairs = A.auPair(null, "member", rcard.qid);
        A.auPair(pairs, "code", rcard.code);
        String json = A.apiGetData(act, rcard.region, "identify", pairs);
        /* String json = "{'name':'William Wagner Spademan-Krawerkljpskjfkj', 'place':'Ashfield, MA'," +
                " 'company':'Common Good Finance', 'logon':'1', 'descriptions':['groceries','sundries']," +
                " 'can_refund':'1', 'device':'adlkjaghh'}"; // debug */
        if (json == null) return; // probably server is down (message already given)
        if (!A.jsonString(json, "ok").equals("1")) {
            act.sayFail(A.jsonString(json, "message"));
            return;
        }

        if (A.jsonString(json, "logon").equals("1")) { // scanning in
            gotAgent(json, rcard);
        } else {
            gotCustomer(json, rcard);
        }
    }

    /**
     * Handle successful scan of company agent's rCard: remember who, check for update, report success, wait to scan.
     * @param json: json-encoded response from the server
     * @param rcard: info read from the card
     */
    private void gotAgent(String json, rCard rcard) {
        A.agent = rcard.qid;
        A.region = rcard.region;
        A.agentName = A.jsonString(json, "name");
        A.can = Integer.parseInt(A.jsonString(json, "can"));
        A.descriptions = A.jsonArray(json, "descriptions");
        if (A.deviceId.equals("")) A.deviceId = A.jsonString(json, "device");
        if (!A.update.equals("1")) A.update = A.jsonString(json, "update"); // don't re-download if we already got it
        act.mention("Success! You are now signed in.\nReady to scan a customer rCard...");
        act.restart();
    }

    /**
     * Handle successful scan of customer rCard: launch Customer activity to display identifying info
     * @param json: json-encoded response from the server
     * @param rcard: info read from the card
     */
    private void gotCustomer(String json, rCard rcard) {
        Intent intent = new Intent(act, CustomerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        A.putIntentString(intent, "customer", rcard.qid);
        A.putIntentString(intent, "customerRegion", rcard.region);
        A.putIntentString(intent, "json", json);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
