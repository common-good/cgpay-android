package org.rcredits.pos;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;

import java.io.ByteArrayOutputStream;

/**
 * Created by William on 9/23/2016.
 */
public class Identify implements Runnable {
    private rCard rcard;
    private final ResultHandler onDone;
    private byte[] image; // photo of customer
    private Json json; // json-encoded response from server

    // return values
    public final static int FAIL = 0;
    public final static int CUSTOMER = 1;
    public final static int AGENT = 2;
    public final static int NO_WIFI = 3;
    public final static int GET_PHOTO_ID = 4; // scan customer's license/photo ID before permitting transaction
    public final static int DIE = 5;

    public static interface ResultHandler {boolean handle(int action, String msg, Json json, byte[] image);}

    public Identify(rCard rcard, ResultHandler onDone){
        this.rcard = rcard;
        this.onDone = onDone;
    }

    @Override
    public void run() {
        A.log(0);
        Looper.prepare();

        try {
            onScan();
        } catch (Db.NoRoom e) {
            onDone.handle(FAIL, A.t(R.string.no_room), null, null);
        } catch (Exception e) {
            onDone.handle(NO_WIFI, "", null, null);
        }
        Looper.loop();
        A.log(9);
    }

    /**
     * Process the result from QR scan:
     * Get agent, agentName, customer (etc), device, success, message from server, and handle it.
     * @return result code (ID, CUSTOMER, AGENT, NO_WIFI, or FAIL)
     */
    public boolean onScan() throws Db.NoRoom {
        A.log(0);
        int result;
        image = null; // no image yet
        String codeEncrypted = A.hash(rcard.code);

        Pairs pairs = new Pairs("op", "identify");
        pairs.add("member", rcard.qid);
        pairs.add("code", codeEncrypted);

//        if (A.demo) SystemClock.sleep(1000); // pretend to contact server
        json = A.apiGetJson(rcard.region, pairs); // get json-encoded info
        if (json == null) return onDone.handle(NO_WIFI, "", null, null); // assume it's a customer (since we can't tell)

        A.log("id msg: " + A.nn(json.get("message")));
        if (!json.get("ok").equals("1")) return onDone.handle(FAIL, json.get("message"), json, null);

        A.can = Integer.parseInt(json.get("can")); // stay up-to-date on the signed-out permissions
        A.descriptions = json.getArray("descriptions");
        if (A.descriptions.isEmpty()) return onDone.handle(FAIL, A.t(R.string.no_descriptions), json, null);
        A.setTime(json.get("time")); // region/server changed so clock might be different (or not set yet)
        String logon = json.get("logon");

        if (logon.equals("1")) { // company agent card
            if (A.deviceId.equals("")) A.setStored("deviceId", A.deviceId = json.get("device"));
            A.setDefaults(json);
            A.db.saveAgent(rcard.qid, rcard.code, image, json); // save or update manager info
            return onDone.handle(AGENT, "", json, image);
        } else { // customer card
            A.setDefaults(json, "can descriptions");
            if (A.defaults.get("descriptions").isEmpty()) return onDone.handle(DIE, "setDefaults description error", json, null);
            image = A.apiGetPhoto(rcard.qid, codeEncrypted);
            if (image == null || image.length == 0) return onDone.handle(GET_PHOTO_ID, "", json, photoFile("no_photo"));
            A.db.saveCustomer(rcard.qid, image, json);
            result = (logon.equals("0") || A.selfhelping()) ? CUSTOMER : GET_PHOTO_ID;
            return onDone.handle(result, "", json, image);
        }
    }

    /**
     * Return the named image.
     * @param photoName: the image filename (no extension)
     * @return: a byte array image
     */
    private byte[] photoFile(String photoName) {
        A.log(0);
        int photoResource = A.resources.getIdentifier(photoName, "drawable", A.packageName);
        Bitmap bm = BitmapFactory.decodeResource(A.resources, photoResource);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
}

