package org.rcredits.pos;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Show the name, location, and photo of the customer.
 * @intent qr: QR code scanned from an rCard (either a customer or cashier)
 */
public class CustomerActivity extends Act {
    private final Act act = this;
    private rCard rcard; // the info from the rCard, parsed
    private byte[] image; // photo of customer

    /**
     * Show just the scanned account code, while we get more info in the background
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);
        //if (!A.debugString.equals("")) {sayFail(A.debugString); return;}

        int[] buttons = {R.id.refund, R.id.cashin, R.id.cashout, R.id.charge};
        for (int b : buttons) findViewById(b).setVisibility(View.INVISIBLE); // hide here so we can see them on layout
        findViewById(R.id.customer_company).setVisibility(View.GONE); // show only as needed

        String qr = A.getIntentString(this.getIntent(), "qr");
        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText("Card scan successful."); // temporarily, while we contact the server for more info
        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText("Identifying...");

        act.progress(true); // this progress meter gets turned off in Identify's onPostExecute()
        new Identify().execute(qr); // download the update in the background
    }

    /**
     * The user clicked on of four buttons: charge, refund, cash in, or cash out. Handle each in the Tx Activity.
     * @param v: which button was pressed.
     */
    public void onRClick(View v) {
        Intent intent = new Intent(this, TxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        String description = v.getId() == R.id.charge ? A.descriptions.get(0) : (String) v.getContentDescription();
        A.putIntentString(intent, "description", description);
        A.putIntentString(intent, "customer", rcard.qid);
        startActivity(intent);
    }

    /**
     * Process the result from QR scan:
     * Get agent, agentName, customer (etc), device, success, message from server, and handle it.
     * @return the json-encoded response from the server, if the scanned card is for a customer. Else null.
     */
    public String onScan() {
        List<NameValuePair> pairs = A.auPair(null, "op", "identify");
        A.auPair(pairs, "member", rcard.qid);
        A.auPair(pairs, "code", rcard.code);
        String json = A.apiGetJson(rcard.region, pairs); // get json-encoded info
        if (json == null) {
            act.sayFail(A.httpError); // probably server is down
            return null;
        }

        if (!A.jsonString(json, "ok").equals("1")) {
            act.sayFail(A.jsonString(json, "message"));
            return null;
        }

        if (A.jsonString(json, "logon").equals("1")) { // scanning in
            gotAgent(json);
            return null;
        } else { // for a customer, we need their photo too
            image = A.apiGetPhoto(rcard.region, rcard.qid);
            if (image.length == 0) {
                act.sayFail(A.httpError); // probably server is down
                return null;
            }
            return json;
        }
    }

    /**
     * Handle successful scan of company agent's rCard: remember who, check for update, report success, wait to scan.
     * @param json: json-encoded response from the server
     */
    private void gotAgent(String json) {
        A.xagent = A.agent; // remember previous agent, for comparison
        A.agent = rcard.qid;
        A.region = rcard.region;
        A.agentName = A.jsonString(json, "name");
        A.can = Integer.parseInt(A.jsonString(json, "can"));
        A.descriptions = A.jsonArray(json, "descriptions");
        if (A.deviceId.equals("")) A.deviceId = A.jsonString(json, "device");
        if (!A.update.equals("1")) A.update = A.jsonString(json, "update"); // don't re-download if we already got it
        act.restart();
    }

    /**
     * Handle successful scan of customer rCard: launch Customer activity to display identifying info
     * @param json: json-encoded response from the server
     */
    private void gotCustomer(String json) {
        A.lastTx = ""; // previous customer info is no longer valid
        A.balance = "";
        A.undo = "";

        if (A.agentCan(A.CAN_REFUND)) findViewById(R.id.refund).setVisibility(View.VISIBLE);
        if (A.agentCan(A.CAN_BUY_CASH)) findViewById(R.id.cashin).setVisibility(View.VISIBLE);
        if (A.agentCan(A.CAN_SELL_CASH)) findViewById(R.id.cashout).setVisibility(View.VISIBLE);
        findViewById(R.id.charge).setVisibility(View.VISIBLE);

        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText(A.jsonString(json, "name"));

        String company = A.jsonString(json, "company");
        TextView customerCompany = (TextView) findViewById(R.id.customer_company);
        customerCompany.setText(company);
        customerCompany.setVisibility(company.equals("") ? View.GONE : View.VISIBLE);

        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText(A.jsonString(json, "place"));

        A.balance = A.jsonString(json, "balance"); // save in case transaction fails

        ImageView photo = (ImageView) findViewById(R.id.photo);
        if (image != null) photo.setImageBitmap(BitmapFactory.decodeByteArray(image, 0, image.length));
    }

    /**
     * Get the customer's info from the server and display it, with options for what to do next.
     */
    private class Identify extends AsyncTask<String, Void, String> {
        /**
         * Do the background part
         * @param qrs: one-element array of the scanned QR
         * @return true if it's a customer
         */
        @Override
        protected String doInBackground(String... qrs) { // must be "String... something"
            String qr = qrs[0];

            try {
                rcard = new rCard(qr); // parse the coded info and save it for use throughout this activity
                return onScan();
            } catch (Exception e) {
                act.sayFail(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String json) {
            if (json != null) gotCustomer(json); // this has to happen on the UI thread (so here in onPostExecute())
            act.progress(false);
        }
    }
}
