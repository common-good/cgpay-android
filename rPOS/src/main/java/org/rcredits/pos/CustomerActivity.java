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
    private byte[] image; // photo of customer
    private Json json; // json-encoded response from server
    private Integer scanResult = null; // no scan result yet
    private String photoId = null; // no photoId yet
    private final static String UNKNOWN_CUST = "Unidentified Member";

    // return values for API (see onScan())
    private final static int SCAN_FAIL = 0;
    private final static int SCAN_CUSTOMER = 1;
    private final static int SCAN_AGENT = 2;
    private final static int SCAN_NO_WIFI = 3;
    private final static int GET_PHOTO_ID = 4; // scan customer's license/photo ID before permitting transaction

    /**
     * Show just the scanned account code, while we get more info in the background
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        qr = A.getIntentString(this.getIntent(), "qr");
        try {
            rcard = new rCard(qr); // parse the coded info and save it for use throughout this activity
            A.deb("Identify rcard qid=" + rcard.qid);
        } catch (rCard.OddCard e) {
            if (e.type == rCard.CHANGE_MODE) {
                String msg = t(A.testing ? R.string.switch_to_test : R.string.switch_to_real);
                act.sayOk("Changed Mode",  msg, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        onCreate(savedInstanceState); // try again
                        //A.executeAsyncTask(new Identify(), qr); // re-query the (other) server in the background
                        return;
                    }
                });
            } else act.sayFail(R.string.invalid_rcard);
            return;
        }

        if (rcard.qid.equals(A.agent)) {act.sayFail(R.string.already_in); return;}
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

        int[] buttons = {R.id.refund, R.id.usdin, R.id.usdout, R.id.charge, R.id.back};
        for (int b : buttons) findViewById(b).setVisibility(View.INVISIBLE); // hide here so we can see them on layout
        findViewById(R.id.customer_company).setVisibility(View.GONE); // show only as needed

        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText("Card scan successful."); // temporarily, while we contact the server for more info
        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText("Identifying...");

        act.progress(true); // this progress meter gets turned off in handleScan()
        if (scanResult == null) {
            A.executeAsyncTask(new Identify(), qr); // query the server in the background
        } else handleScan(scanResult);
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
        final String description = (id == R.id.charge && !A.descriptions.isEmpty()) ? A.descriptions.get(0) : (String) v.getContentDescription();
        A.putIntentString(intent, "description", description);
        A.putIntentString(intent, "customer", rcard.qid);
        A.putIntentString(intent, "code", rcard.code);
        A.putIntentString(intent, "goods", (id == R.id.charge || id == R.id.refund) ? "1" : "0");
        A.putIntentString(intent, "photoId", photoId == null ? null : "1");
        startActivity(intent);
    }

    /**
     * Process the result from QR scan:
     * Get agent, agentName, customer (etc), device, success, message from server, and handle it.
     * @return result code (SCAN_ID, SCAN_CUSTOMER, SCAN_AGENT, SCAN_NO_WIFI, or SCAN_FAIL)
     */
    public int onScan() throws Db.NoRoom {
        int result;
        A.log("scanned: " + rcard.qid);
        image = null; // no image yet
        String codeEncrypted = A.hash(rcard.code);
//        String was = A.decrypt(codeEncrypted, rcard.qid + rcard.code);
//        String other = A.decrypt("e045adaf90f1ab73aa84401f917d70d8", rcard.qid + rcard.code);

        act.rpcPairs = new Pairs("op", "identify");
        act.rpcPairs.add("member", rcard.qid);
        act.rpcPairs.add("code", codeEncrypted);

//        if (A.demo) SystemClock.sleep(1000); // pretend to contact server
        json = A.apiGetJson(rcard.region, act.rpcPairs); // get json-encoded info
        if (json == null) return SCAN_NO_WIFI; // assume it's a customer (since we can't tell)

        A.deb("onScan json message: " + A.nn(json.get("message")));

        if (!json.get("ok").equals("1")) {
            act.sayFail(json.get("message"));
            return SCAN_FAIL;
        }

        A.can = Integer.parseInt(json.get("can")); // stay up-to-date on the signed-out permissions
        A.descriptions = json.getArray("descriptions");
        if (A.descriptions.isEmpty()) {act.sayFail(R.string.no_descriptions); return 0;}
        A.setTime(json.get("time")); // region/server changed so clock might be different (or not set yet)
        String logon = json.get("logon");

        if (logon.equals("1")) { // company agent card
            if (A.deviceId.equals("")) A.setStored("deviceId", A.deviceId = json.get("device"));
            A.setDefaults(json);
            A.db.saveAgent(rcard.qid, rcard.code, image, json); // save or update manager info
            result = SCAN_AGENT;
        } else { // customer card
            A.setDefaults(json, "can descriptions");
            if (A.defaults.get("descriptions").isEmpty()) {act.die("setDefaults description error"); return 0;}
            image = A.apiGetPhoto(rcard.qid, codeEncrypted);
            if (image == null || image.length == 0) {
                image = photoFile("no_photo");
                result = GET_PHOTO_ID;
            } else {
                A.db.saveCustomer(rcard.qid, image, json);
                result = (logon.equals("0") || A.selfhelping()) ? SCAN_CUSTOMER : GET_PHOTO_ID;
            }
        }

        return result;
    }

    private void handleScan(int result) {
        A.positiveId = (result != SCAN_NO_WIFI);

        if (result == SCAN_AGENT) gotAgent(json.get("name"));
        if (result == SCAN_CUSTOMER) gotCustomer(); // this has to happen on the UI thread (here, not in background)
        if (result == SCAN_NO_WIFI) noWifi();
//        if (result == GET_PHOTO_ID) getPhotoId(); // MAYBE
        if (result == GET_PHOTO_ID) askPhotoId();

        act.progress(false);
    }

    /**
     * Get cashier to ask customer for a photo ID.
     */
    private void askPhotoId() {
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
        A.log("nowifi");

        if (A.defaults == null) {act.sayFail(R.string.wifi_for_setup); return;}

        Q q = A.db.oldCustomer(rcard.qid);
        if (q != null && q.isAgent()) { // got agent for current company
            if (A.db.badAgent(rcard.qid, rcard.code)) {act.sayFail("That Company Agent rCard is not valid."); return;}
            A.can = q.getInt(DbHelper.AGT_CAN);
            gotAgent(q.getString("name"));
            q.close();
        } else if (q != null) {
//            image = (A.demo && A.wifi) ? photoFile(rcard.qid.substring(4).toLowerCase()) : q.getBlob("photo");
            image = q.getBlob("photo");
            if (json == null) {
                String company = q.getString("company");
                gotCustomer(q.getString("name"), company + (company.equals("") ? "" : ", ") + q.getString("place"), t(R.string.offline));
            } else gotCustomer();
            q.close();
        } else {
            if (rcard.co.equals(A.defaults.get("default"))) {act.sayFail(R.string.wifi_for_setup); return;} // same company, must be an agent
            askPhotoId(); // if this business is often offline, nudge cashier to ask for ID
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
    private void gotAgent(String name) {
        A.log("got agent: " + name);
        A.xagent = A.agent; // remember previous agent, for comparison
        A.agent = rcard.qid;
        A.region = rcard.region;
        A.agentName = "Agent: " + name;
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
        if (image == null) image = photoFile("no_photo"); // has happened (possibly when got customer, but not photo)
        photo.setImageBitmap(A.scale(A.bray2bm(image), A.PIC_H));
        if (A.selfhelping()) onRClick(findViewById(R.id.charge));
    }

    private void gotCustomer() {
        gotCustomer(json.get("name"), json.get("company"), json.get("place"));
    }

    /**
     * Return the named image.
     * @param photoName: the image filename (no extension)
     * @return: a byte array image
     */
    private byte[] photoFile(String photoName) {
        int photoResource = A.resources.getIdentifier(photoName, "drawable", getPackageName());
        Bitmap bm = BitmapFactory.decodeResource(A.resources, photoResource);
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
            //A.deb("Identify qr=" + A.nn(qr));

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
