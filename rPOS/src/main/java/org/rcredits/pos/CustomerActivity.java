package org.rcredits.pos;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;

/**
 * Show the name, location, and photo of the customer.
 * @intent qr: QR code scanned from an rCard (either a customer or cashier)
 */
public class CustomerActivity extends Act {
    private rCard rcard; // the info from the rCard, parsed
    private String qr; // the scanned QR code
    private String photoId; // no photoId yet
    private final static String UNKNOWN_CUST = "Unidentified Member";
    public Json json; // json-encoded response from server
    public Bitmap image; // photo of customer

    /**
     * Show just the scanned account code, while we get more info in the background
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        A.log("cust create");
        super.onCreate(savedInstanceState);
    }

    /**
     * Restart the activity, if we went away and came back.
     */
    @Override
    protected void onResume() {
        super.onResume();
        qr = A.getIntentString(this.getIntent(), "qr");
        try {
            rcard = new rCard(qr); // parse the coded info and save it for use throughout this activity
        } catch (rCard.BadCard e) {
            act.sayFail(R.string.invalid_rcard);
            return;
        }

        A.log("rcard qid=" + rcard.qid);
        if (rcard.qid.equals(A.agent)) {act.sayFail(R.string.already_in); return;}
        image = null;
        photoId = null;
        if (rcard.isOdd) {
            String msg = t(A.testing ? R.string.switch_to_test : R.string.switch_to_real);
            act.sayOk("Changed Mode",  msg, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    setLayout();
                }
            });
        } else setLayout();
    }

    /**
     * Adjust the layout according to the device's orientation.
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
// no config changes currently        setLayout();
    }

    /**
     * Populate the screen according to what was scanned.
     */
    private void setLayout() {
        A.log(0);
        setContentView(R.layout.activity_customer);

        int[] buttons = {R.id.refund, R.id.usdin, R.id.usdout, R.id.charge, R.id.back};
        for (int b : buttons) findViewById(b).setVisibility(View.INVISIBLE); // hide here so we can see them on layout
        findViewById(R.id.customer_company).setVisibility(View.GONE); // show only as needed

        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText("Card scan successful."); // temporarily, while we contact the server for more info
        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText("Identifying...");

        act.progress(true); // this progress meter gets turned off in handleScan()
        new Thread(new Identify(rcard, new handleIdResult())).start();
        A.log(9);
//        A.executeAsyncTask(identifyTask = new Identify(), qr); // query the server in the background
    }

    /**
     * Go all the way back to Main screen on Back Button press.
     *//*
    @Override
    public void onBackPressed() {act.restart();}

    @Override
    public void goBack(View v) {onBackPressed();}
*/

    /**
     * The user clicked one of four buttons: charge, refund, USD in, or USD out. Handle each in the Tx Activity.
     * @param v: which button was pressed.
     */
    public void onRClick(View v) {
        Intent intent = new Intent(this, TxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        final int id = v.getId();
        A.log(id);
        final String description = (id == R.id.charge && !A.descriptions.isEmpty()) ? A.descriptions.get(0) : (String) v.getContentDescription();
        A.putIntentString(intent, "description", description);
        A.putIntentString(intent, "customer", rcard.qid);
        A.putIntentString(intent, "code", rcard.code);
        A.putIntentString(intent, "goods", (id == R.id.charge || id == R.id.refund) ? "1" : "0");
        A.putIntentString(intent, "photoId", photoId == null ? null : "1");
        startActivity(intent);
    }

    public class handleIdResult implements Identify.ResultHandler {

        public handleIdResult() {}

        @Override
        public boolean done(int result0, String msg0, Json json0, Bitmap image0) {
            final int result = result0;
            final String msg = msg0;
            json = json0;
            image = image0;

            act.runOnUiThread(new Runnable() {
                public void run() {
                    A.log(0);
                    act.progress(false);
                    A.positiveId = (result != Identify.NO_WIFI);

                    if (result == Identify.CUSTOMER) gotCustomer();
                    else if (result == Identify.NO_WIFI) noWifi();
                    else if (result == Identify.GET_PHOTO_ID) askPhotoId();
                    else if (result == Identify.AGENT) gotAgent();
                    else if (result == Identify.FAIL) act.sayFail(msg);
                    else {
                        A.report(String.format("id fail res=%d %s json=%s", result, msg, json.toString()));
                        act.sayFail("System Error: " + msg);
                    }
                }
            });
            return true;
        }
    }

    /*
    private void handleScan(int result) {
        A.log("handleScan");
        A.positiveId = (result != SCAN_NO_WIFI);

        if (result == SCAN_AGENT) gotAgent(json.get("name"));
        if (result == SCAN_CUSTOMER) gotCustomer(); // this has to happen on the UI thread (here, not in background)
        if (result == SCAN_NO_WIFI) noWifi();
//        if (result == GET_PHOTO_ID) getPhotoId(); // MAYBE
        if (result == GET_PHOTO_ID) askPhotoId();

        act.progress(false);
    }
*/

    /**
     * Get cashier to ask customer for a photo ID.
     */
    private void askPhotoId() {
        A.log(0);
        act.askYesNo(t(R.string.ask_for_id), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                if (json == null) {
                    gotCustomer("Member " + rcard.qid, UNKNOWN_CUST, t(R.string.offline));
                } else gotCustomer();
            }
        }, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.sayFail(R.string.need_photo_id);
            }
        });
    }

    /**
     * Get cashier to ask customer for a photo ID. Save the ID number for upload with transaction info.
     */
//    private void getPhotoId() {act.start(PhotoIdActivity.class, GET_PHOTO_ID, "place", json.get("place"));}

    /**
     * Receive the photo ID state and number.
     * @param requestCode
     * @param resultCode
     * @param data: state and idNumber
     */ /*
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_PHOTO_ID) {
            if(resultCode == RESULT_OK) {
                photoId = data.getStringExtra("photoId");
                scanResult = SCAN_CUSTOMER;
                setLayout();
            } else if (resultCode == RESULT_CANCELED) {} // do nothing if no result
        }
    }
    */

    /**
     * Handle the case where connection to the internet failed or is turned off (A.wifiOff).
     */
    private void noWifi() {
        A.log(0);
        Q q = A.db.oldCustomer(rcard.qid);
        if (q != null) {
            if (q.getString("code") == null) {sayFail("There is no stored card security code for that customer."); return;}
            if (!A.nn(q.getString("code")).equals(A.hash(rcard.code))) {sayFail(R.string.invalid_rcard); return;}
            gotCustomer(q.getString("name"), A.nnc(q.getString("company")) + q.getString("place"), t(R.string.offline));
            q.close();
        } else askPhotoId(); // if this business is often offline, nudge cashier to ask for ID
        A.log(9);

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
     * Handle successful scan of company agent's rCard.
     */
    private void gotAgent() {
        act.goHome();
    }

    /**
     * Display customer's identifying info (on scan of rCard or offline retrieval of customer record)
     * @param name
     * @param company
     * @param place
     */
    private void gotCustomer(String name, String company, String place) {
        A.log("got customer: " + name + " company=" + company + " place=" + place);
        if (A.agent == null) {act.sayFail(R.string.no_agent); return;}
        if (!A.can(A.CAN_CHARGE) && !A.can(A.CAN_REFUND) && !A.can(A.CAN_R4USD) && !A.can(A.CAN_USD4R)) {
            act.sayFail(R.string.no_permission); return;
        }
        A.undo = null; // previous customer info is no longer valid
        A.customerName = A.customerName(name, company);
        A.balance = json == null ? null : A.balanceMessage(A.customerName, json); // in case tx fails or is canceled
//        if (A.demo) A.balance = A.balanceMessage(A.customerName, rcard.qid);

        if (A.can(A.CAN_REFUND)) findViewById(R.id.refund).setVisibility(View.VISIBLE);
        if (!company.equals(UNKNOWN_CUST)) { // no cash transactions without pre-identification (FinCEN requirement)
            if (A.can(A.CAN_R4USD)) findViewById(R.id.usdin).setVisibility(View.VISIBLE);
            if (A.can(A.CAN_USD4R)) findViewById(R.id.usdout).setVisibility(View.VISIBLE);
        }
        if (A.can(A.CAN_CHARGE)) findViewById(R.id.charge).setVisibility(View.VISIBLE);

        findViewById(R.id.back).setVisibility(View.VISIBLE);

        setField(R.id.customer_name, name);
        setField(R.id.customer_company, company).setVisibility(company == null ? View.GONE : View.VISIBLE);
        setField(R.id.customer_place, place);

        ImageView photo = (ImageView) findViewById(R.id.photo);
        if (image == null) image = Identify.scaledPhoto(A.photoFile("no_photo")); // happens when we get an unknown customer offline
        photo.setImageBitmap(image);
        if (A.selfhelping()) onRClick(findViewById(R.id.charge));
        A.log(9);
    }

    private void gotCustomer() {
        gotCustomer(json.get("name"), json.get("company"), json.get("place"));
    }

    /**
     * Get the customer's info from the server and display it, with options for what to do next.
     *//*
    private class Identify extends AsyncTask<String, Void, Integer> {
        /**
         * Do the background part
         * @param qrs: one-element array of the scanned QR
         * @return true if it's a customer
         *//*
        @Override
        protected Integer doInBackground(String... qrs) { // param list must be "Type... varname"
            String qr = qrs[0];
            A.log("in Identify background qr=" + A.nn(qr));

            try {
                return onScan();
            } catch (Db.NoRoom e) {
                act.sayFail(R.string.no_room);
                return SCAN_FAIL;
            } catch (Exception e) {
                return SCAN_NO_WIFI;
            }
        }

        @Override
        protected void onCancelled(Integer result) {
            A.log("idCanceled");
            identifyTask = null;
        }

        @Override
        protected void onPostExecute(Integer result) {
            handleScan(scanResult = result);
            identifyTask = null;
            A.log("idPostExec");
        }
    }
*/
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
