package org.rcredits.pos;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.NameValuePair;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Show the name, location, and photo of the customer.
 * @intent qr: QR code scanned from an rCard (either a customer or cashier)
 */
public class CustomerActivity extends Act {
    private final Act act = this;
    private static rCard rcard; // the info from the rCard, parsed
    private static String qr; // the scanned QR code
    private static byte[] image; // photo of customer
    private static Json json; // json-encoded response from server
    private Integer scanResult = null; // no scan result yet

    // return values for API (see onScan())
    private final static int SCAN_FAIL = 0;
    private final static int SCAN_CUSTOMER = 1;
    private final static int SCAN_AGENT = 2;
    private final static int SCAN_NO_WIFI = 3;
    private final static int SCAN_MODE = 4;

    /**
     * Show just the scanned account code, while we get more info in the background
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        qr = A.getIntentString(this.getIntent(), "qr");
        setLayout();
    }

    /**
     * Adjust the layout according to the device's orientation.
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLayout();
    }

    /**
     * Populate the screen according to what was scanned.
     */
    private void setLayout() {
        setContentView(R.layout.activity_customer);

        int[] buttons = {R.id.refund, R.id.cashin, R.id.cashout, R.id.charge};
        for (int b : buttons) findViewById(b).setVisibility(View.INVISIBLE); // hide here so we can see them on layout
        findViewById(R.id.customer_company).setVisibility(View.GONE); // show only as needed

        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText("Card scan successful."); // temporarily, while we contact the server for more info
        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText("Identifying...");

        act.progress(true); // this progress meter gets turned off in handleScan()
        if (scanResult == null) {
            new Identify().execute(qr); // query the server in the background
        } else handleScan(scanResult);
    }

    /**
     * Go all the way back to Main screen on Back Button press.
     */
    @Override
    public void onBackPressed() {
        //act.sayOk("Back to Top", "Transaction CANCELED.", null);
        act.restart();
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
     * @return result code (SCAN_CUSTOMER, SCAN_AGENT, SCAN_NO_WIFI, or SCAN_FAIL)
     */
    public int onScan() {
        List<NameValuePair> pairs = A.auPair(null, "op", "identify");
        A.auPair(pairs, "member", rcard.qid);
        A.auPair(pairs, "code", rcard.code);
        if (A.demo) {
            json = Json.make(rcard.qid.equals("NEW.ABB") ? "{'ok':'1','logon':'0','name':'Susan Shopper','place':'Montague, MA','company':'','balance':'Customer: Susan Shopper\\n\\nBalance: $306.56\\nTradable for cash: $178.83'}" // customer
                    : "{'ok':'1','logon':'1','device':'3i2ifsapEjwev3CwBCV7','name':'Bob Bossman','company':'Corner Store','descriptions':['groceries','gifts','sundries','deli','baked goods'],'can':14,'default':'NEW.AAB'}"); // manager
            SystemClock.sleep(1000);
        } else json = A.apiGetJson(rcard.region, pairs, true); // get json-encoded info

        A.deb("onScan json message: " + A.nn(json.get("message")));
        image = null; // no image yet
        if (json == null) return SCAN_NO_WIFI; // assume it's a customer (since we can't tell)

        if (!json.get("ok").equals("1")) {
            act.sayFail(json.get("message"));
            return SCAN_FAIL;
        }

        if (json.get("logon").equals("1")) { // scanning in
            return SCAN_AGENT;
        } else { // for a customer, we need their photo too
            image = A.apiGetPhoto(rcard.qid, rcard.code, true);
            if (image == null || image.length == 0) {
                image = nonPhoto();
                return SCAN_NO_WIFI;
            }
            A.db.saveCustomer(rcard.qid, image, json);
            return SCAN_CUSTOMER;
        }
    }

    private void handleScan(int result) {
        if (result == SCAN_MODE) {
            act.sayOk("Changed Mode",  A.t(A.testing ? R.string.switch_to_test : R.string.switch_to_real), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    new Identify().execute(qr); // re-query the (other) server in the background
                    return;
                }
            });
            return;
        }

        A.positiveId = (result != SCAN_NO_WIFI);

        if (result == SCAN_AGENT) {
            gotAgent();
        } else {
//            String bug = " handleScan: result=" + result + " qid=" + rcard.qid;
            if (A.agent == null) {act.sayFail(R.string.no_agent); return;}
            if (result == SCAN_CUSTOMER) gotCustomer(); // this has to happen on the UI thread (so here in onPostExecute())
            if (result == SCAN_NO_WIFI) noWifi();
        }
        act.progress(false);
    }

    private void noWifi() {
        String offline = "OFFLINE (no internet)";
        Q q = A.db.oldCustomer(rcard.qid);
        if (q != null) {
            image = q.getBlob("photo");
            if (json == null) {
                String company = q.getString("company");
                gotCustomer(q.getString("name"), company + (company.equals("") ? "" : ", ") + q.getString("place"), offline);
            } else gotCustomer();
            q.close();
        } else {
            image = nonPhoto();
            gotCustomer("Member " + rcard.qid, "Unidentified Customer", offline);
            //if () mention(R.string.ask_for_id); // if this business is often offline, ask for ID
        }
/*
        act.askOk(A.nn(A.httpError) + " " + A.t(R.string.try_offline), null, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { // Cancel
                dialog.cancel();
                sayFail("Transaction canceled.");
            } // otherwise fall through and continue the transaction
        }); // can't identify. give the cashier the option to do the transaction anyway
        */
    }

    /**
     * Handle successful scan of company agent's rCard: remember who, check for update, report success, wait to scan.
     */
    private void gotAgent() {
        A.xagent = A.agent; // remember previous agent, for comparison
        A.agent = rcard.qid;
        A.region = rcard.region;
        A.agentName = "Agent: " + json.get("name");
        A.can = Integer.parseInt(json.get("can"));
        A.descriptions = json.getArray("descriptions");
        if (A.deviceId == null) A.setStored("deviceId", A.deviceId = json.get("device"));
        Json xdefaults = A.defaults;
        A.setDefaults(json);
        A.setTime(json.get("time")); // region/server changed so clock might be different (or not set yet)
        if (xdefaults == null) A.signOut(); // first time signing in is just to set the company
        act.restart();
    }

    /**
     * Display customer's identifying info (on scan of rCard or offline retrieval of customer record)
     * @param name
     * @param company
     * @param place
     */
    private void gotCustomer(String name, String company, String place) {
        A.lastTx = A.undo = null; // previous customer info is no longer valid
        A.customerName = A.customerName(name, company);
        A.balance = json == null ? null : A.balanceMessage(A.customerName, json); // in case tx fails or is canceled

        if (A.agentCan(A.CAN_REFUND)) findViewById(R.id.refund).setVisibility(View.VISIBLE);
        if (A.agentCan(A.CAN_BUY_CASH)) findViewById(R.id.cashin).setVisibility(View.VISIBLE);
        if (A.agentCan(A.CAN_SELL_CASH)) findViewById(R.id.cashout).setVisibility(View.VISIBLE);
        findViewById(R.id.charge).setVisibility(View.VISIBLE);

        setField(R.id.customer_name, name);
        setField(R.id.customer_company, company).setVisibility(company == null ? View.GONE : View.VISIBLE);
        setField(R.id.customer_place, place);

        ImageView photo = (ImageView) findViewById(R.id.photo);
        photo.setImageBitmap(A.scale(A.bray2bm(image), A.PIC_H));
    }

    private void gotCustomer() {
        gotCustomer(json.get("name"), json.get("company"), json.get("place"));
    }

    /**
     * Return an image that announces the unavailability of a photo for the customer.
     * @return: a byte array image
     */
    private byte[] nonPhoto() {
        Bitmap bm = BitmapFactory.decodeResource(act.getResources(), R.drawable.no_photo_available);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    /**
     * Get the customer's info from the server and display it, with options for what to do next.
     */
    private class Identify extends AsyncTask<String, Void, Integer> {
        /**
         * Do the background part
         * @param qrs: one-element array of the scanned QR
         * @return true if it's a customer
         */
        @Override
        protected Integer doInBackground(String... qrs) { // param list must be "Type... varname"
            String qr = qrs[0];
            A.deb("Identify qr=" + A.nn(qr));

            try {
                rcard = new rCard(qr); // parse the coded info and save it for use throughout this activity
                A.deb("Identify rcard qid=" + rcard.qid);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg.equals("mode")) { // not a fatal error
                    return SCAN_MODE;
                } else {
                    act.sayFail(msg);
                    return SCAN_FAIL;
                }
            }

            try {
                return onScan();
            } catch (Exception e) {
                return SCAN_NO_WIFI;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            handleScan(scanResult = result);
        }
    }

    /**
     * Set a text field to the appropriate text.
     * @param id: field identifier
     * @param v: the value to set
     * @return: the field
     */
    private TextView setField(int id, String v) {
        TextView view = (TextView) findViewById(id);
        view.setText(v);
        return view;
    }

}
