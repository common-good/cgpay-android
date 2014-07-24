package org.rcredits.pos;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.BitmapFactory.decodeResource;
import static java.lang.Thread.sleep;

/**
 * rCredits POS App
 *
 * Scan a company agent card to "scan in".
 * Scan a customer card to charge him/her, exchange USD for rCredits, or give a refund.
 * See functional specs (SRS): https://docs.google.com/document/d/1bSVn_zaS26SZgtTg86bvR51hwEWOMynVmOEjEbIUNw4
 * See RPC API specs: https://docs.google.com/document/d/1fZE_wmEBRTBO9DAZcY4ewQTA70gqwWqeqkUnJSfWMV0
 *
 * This class also holds global static variables and constants.
 *
 * The application uses the open source Zxing project.
 * The package com.google.zxing.client.android has been changed to org.rcredits.zxing.client.android
 * All other changes are commented with "cgf". Small modifications have been made to the following classes.
 *   CaptureActivity
 *   CameraManager
 *   ViewfinderView
 *
 * Some cameras don't tell Android that their camera faces front (and therefore needs flipping), so a separate
 * version (rposb) should be built with flip=true in onCreate()
 */
public class A extends Application {
    public static Boolean demo = false; // set this true to build a demo version that does not require internet
    public static Context context;
    public static Resources resources;
    public static String versionCode; // version number of this software
    public static String versionName; // version name of this software
    private static SharedPreferences settings = null;

    public static Long timeFix = 0L; // how much to add to device's time to get server time
    public static String update = null; // URL of update to install on restart, if any ("1" means already downloaded)
    public static String failMessage = null; // error message to display upon restart, if any
    public static String deviceId = null; // set once ever in storage upon first scan-in, read upon startup
    public static String debugString = null; // for debug messages
    public static Periodic periodic = null; // periodic reconciliation process running in background
    public static boolean flip; // whether to flip the scanned image (for front-facing cameras)
    public static boolean positiveId; // did online customer identification succeed
    public static SQLiteDatabase db_real;
    public static SQLiteDatabase db_test;

    // variables that get reset when testing (or not testing)
    public static Boolean testing = demo ? true : false;
    public static Db db; // real or test db (should be always open when needed)
    public static Json defaults = null; // parameters to use when no agent is signed in (empty if not allowed)
    public static String region = null; // set upon scan-in
    public static String agent = null; // set upon scan-in (eg NEW:AAB)
    public static String xagent = null; // previous agent
    public static String agentName = null; // set upon scan-in
    public static List<NameValuePair> rpcPairs = null; // parameters for last RPC request (in case wifi is not available)
    public static String customerName = null; // set upon identifying a customer
    public static String balance = null; // message about last customer's balance
    public static String undo = null; // message about reversing last transaction
    public static String lastTx = null; // number of last transaction
    public static Long lastTxRow = null; // row number of last transaction in local db
    public static String httpError = null; // last HTTP failure message

    public static List<String> descriptions; // set upon scan-in
    public final static String DESC_REFUND = "refund";
    public final static String DESC_CASH_IN = "cash in";
    public final static String DESC_CASH_OUT = "cash out";

    public static int can = 0; // what the agent can do
    public final static int CAN_REFUND =     1 << 1;
    public final static int CAN_SELL_CASH =  1 << 2;
    public final static int CAN_BUY_CASH =   1 << 3;

    public final static String MSG_DOWNLOAD_SUCCESS = "Update download is complete.";

    // values for status field in table txs
    public final static int TX_CANCEL = -1; // transaction has been canceled offline, but may exist on server
    public final static int TX_PENDING = 0; // transaction is being sent to server
    public final static int TX_OFFLINE = 1; // connection failed, offline transaction is waiting to be uploaded
    public final static int TX_DONE = 2; // completed transaction is on server

    public final static int PERIOD = 20 * 60; // (20 mins.) how often to tickle (reconcile offline with server, etc.)
    public final static int PIC_H = 600; // standard pixel height of photos
    public final static int PIC_H_OFFLINE = 60; // standard pixel height of photos stored for offline use
    public final static double PIC_ASPECT = .75; // standard photo aspect ratio

    private final static String PREFS_NAME = "rCreditsPOS";
//    private final static String REAL_API_PATH = "https://<region>.rcredits.org/pos"; // the real server (rc2.me fails)
    private final static String REAL_API_PATH = "https://new.rcredits.org/pos"; // the only real server for now
    private final static String TEST_API_PATH = "https://ws.rcredits.org/pos"; // the test server
    //private final static String TEST_API_PATH = "http://192.168.2.101/devcore/pos"; // testing on emulator

    @Override
    public void onCreate() {
        super.onCreate();

        A.context = this;
        A.resources = getResources();
        A.deviceId = getStored("deviceId");

        db_real = (new DbHelper(false)).getWritableDatabase();
        db_test = (new DbHelper(true)).getWritableDatabase();
        A.setMode(false);

        A.setTime(A.getTime()); // check for updates first thing

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = pInfo.versionCode + "";
            versionName = pInfo.versionName + "";
        } catch (PackageManager.NameNotFoundException e) {e.printStackTrace();}

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        flip = (Camera.getNumberOfCameras() == 1 && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        //flip = true; versionCode = "0" + versionCode; // uncomment for old devices with only an undetectable front-facing camera.
    }

    /**
     * Change from test mode to real mode or vice versa
     * @param testing: <new mode is test mode (otherwise real)>
     */
    public static void setMode(boolean testing) {
        A.signOut();
        A.testing = testing;
        A.defaults = Json.make(A.getStored(testing ? "defaults_test" : "defaults"));
        if (A.defaults != null) A.agent = A.defaults.get("default"); // prevents "you have to scan in" message

        A.db = new Db(testing);

        if (A.periodic != null) A.periodic.cancel(true); // cancel the old tickler, if any (allowing it to finish with its db)
        (A.periodic = new Periodic()).execute(new String[1]); // launch a tickler for the new db
    }

    public static void setDefaults(Json json) {
        A.setStored(testing ? "defaults_test" : "defaults", (A.defaults = json).s);
    }

    /**
     * Reset all global variables to the "signed out" state.
     */
    public static void signOut() {
        A.agent = A.xagent = A.agentName = A.region = A.balance = A.undo = A.lastTx = null;
        A.customerName = A.httpError = null;
        A.rpcPairs = null;
        A.lastTxRow = null;
    }

    public static String getStored(String name) {
        if (A.settings == null) A.settings = A.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return settings.getString(name, null);
    }

    public static void setStored(String name, String value) {
        SharedPreferences.Editor editor = A.settings.edit();
        editor.putString(name, value);
        editor.commit();
    }

    /**
     * Send a request to the server and return its response.
     * @param region: the agent's region
     * @param pairs: name/value pairs to send with the request
     * @param ui: <called by user interaction>
     * @return: the server's response. null if failure (with message in A.httpError)
     */
    public static HttpResponse post(String region, List<NameValuePair> pairs, boolean ui) {
        final int timeout = 5 * 1000; // milliseconds
        A.auPair(pairs, "agent", A.agent);
        A.auPair(pairs, "device", A.deviceId);
        A.auPair(pairs, "version", A.versionCode);
        //A.auPair(pairs, "location", A.location);

        String api = A.testing ? TEST_API_PATH : REAL_API_PATH;
        HttpPost post = new HttpPost(api.replace("<region>", region));
        //HttpClient client = new DefaultHttpClient();

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, timeout);
        HttpConnectionParams.setSoTimeout(params, timeout);
        DefaultHttpClient client = new DefaultHttpClient(params);
        HttpResponse response;

        try {
            post.setEntity(new UrlEncodedFormEntity(pairs));
            return client.execute(post);
        } catch (Exception e) {
            if (ui) { // set global vars for UI
                String msg = e.getMessage();
                A.auPair(pairs, "region", region);
                A.rpcPairs = pairs; // remember operation, to let cashier accept and store it
                A.httpError = msg.equals("No peer certificate") ? t(R.string.clock_off) : t(R.string.http_err);
//                : (t(R.string.http_err) + " (" + msg + ")");
            }
            return null;
        }
    }

    /**
     * Get a string response from the server
     * @param region: which regional server to ask
     * @param pairs: name/value pairs to send with the request (including op = the requested operation)
     * @param ui: <called by user interaction>
     * @return the response
     */
    public static Json apiGetJson(String region, List<NameValuePair> pairs, boolean ui) {
        A.deb("apiGetJson pairs is null: " + (pairs == null ? "yes" : "no"));
        A.deb("apiGetJson region=" + region + " ui=" + ui + " pairs: " + A.showPairs(pairs));
        HttpResponse response = A.post(region, pairs, ui);
        try {
            return response == null ? null : Json.make(EntityUtils.toString(response.getEntity()));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the customer's photo from his/her server.
     * @param qid: the customer's account ID
     * @param code: the customer's rCard security code
     * @param ui: <called by user interaction>
     * @return: the customer's photo, as a byte array
     */
    public static byte[] apiGetPhoto(String qid, String code, boolean ui) {
        if (A.demo) {
            Bitmap bitmap = decodeResource(A.resources, R.drawable.shopper);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            SystemClock.sleep(1000);
            return stream.toByteArray();
        }

        List<NameValuePair> pairs = A.auPair(null, "op", "photo");
        A.auPair(pairs, "customer", qid);
        A.auPair(pairs, "code", code);
        HttpResponse response = A.post(rCard.qidRegion(qid), pairs, ui);
        try {
            return response == null ? null : EntityUtils.toByteArray(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the server's clock time
     * @return the server's unixtime, as a string (null if not available)
     */
    public static String getTime() {
        if (A.region == null) return null; // time is tied to region
        HttpResponse response = A.post(region, A.auPair(null, "op", "time"), false);

        try {
            if (response == null) return null;
            Json json = Json.make(EntityUtils.toString(response.getEntity()));
            A.update = json.get("update");
            return json.get("time");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set the device's system time (to match the relevant server's clock) unless time is null.
     * NOTE: some devices crash on attempt to set time (even with SET_TIME permission), so we fudge it.
     * @param time: unixtime, as a string (or null if time is not available)
     * @return <time was set successfully>
     */
    public static boolean setTime(String time) {
        if (time == null || time.equals("")) return false;

        //AlarmManager am = (AlarmManager) A.context.getSystemService(Context.ALARM_SERVICE);
        //am.setTime(time * 1000L);
        A.timeFix = Long.parseLong(time) - now0();
        return true;
    }

    /**
     * Convert the name and value to a "pair" (a NameValuePair) and add it to the list.
     * @param pairs: (returned) the list to add to (null if no list yet)
     * @param name: name of parameter to add
     * @param value: value of parameter to add
     * @return: the modified list
     */
    public static List<NameValuePair> auPair(List<NameValuePair> pairs, String name, String value) {
        if (pairs == null) pairs = new ArrayList<NameValuePair>(1);
        pairs.add(new BasicNameValuePair(name, value));
        return pairs;
    }

    /**
     * Extract the named value from the array of name/value pairs.
     */
    public static String duPair(String k, List<NameValuePair> pairs) {
        for (NameValuePair pair : pairs) {
            if (pair.getName().equals(k)) return pair.getValue();
        }
        return null;
    }

    public static String duPair(String k) {
        return duPair(k, A.rpcPairs);
    }

    public static String showPairs(List<NameValuePair> pairs) {
//        A.deb("showPairs pairs is null: " + (pairs == null ? "yes" : "no"));
        String result = "";
        for (NameValuePair pair : pairs) {
//            A.deb("showPairs result=" + result);
//            A.deb("showPairs pair k=" + pair.getName());
            result = result + " " + pair.getName() + "=" + pair.getValue();
        }
        return result;
    }

    /**
     * Add a string parameter to the activity we are about to launch.
     * @param intent: the intended activity
     * @param key: parameter name
     * @param value: parameter value
     */
    public static void putIntentString(Intent intent, String key, String value) {
        String pkg = intent.getComponent().getPackageName();
        intent.putExtra(pkg + "." + key, value);
    }

    /**
     * Return a string parameter passed to the current activity.
     * @param intent: the intent of the current activity
     * @param key: parameter name
     * @return: the parameter's value
     */
    public static String getIntentString(Intent intent, String key) {
        String pkg = intent.getComponent().getPackageName();
        return intent.getStringExtra(pkg + "." + key);
    }

    /**
     * Say whether the agent can do something
     * @param permissions: bits representing things the agent might do
     * @return true if the agent can do it
     */
    public static boolean agentCan(int permissions) {return (A.can & permissions) != 0;}

    /**
     * Say whether the customer's balance is to be kept secret.
     * @param balance: the balance string -- starts with an asterisk if it is secret
     * @return <balance is secret or null>
     */
    public static boolean isSecret(String balance) {
        if (balance == null || balance.equals("")) return true;
        return balance.substring(0, 1).equals("*");
    }

    /**
     * Return a formatted dollar amount (no dollar sign).
     * @param n: the amount to format
     * @param commas: <include commas, if appropriate>
     * @return: the formatted amount
     */
    public static String fmtAmt(Double n, boolean commas) {
        String result = NumberFormat.getInstance().format(n);
        return commas ? result : result.replaceAll(",", "");
    }
    public static String fmtAmt(String v, boolean commas) {return fmtAmt(Double.valueOf(v), commas);}

    /**
     * Return a suitable message for when the "Show Customer Balance" button is pressed.
     * @param name: customer name (including agent and company name, for company agents)
     * @return: a suitable message or null if the balance is secret
     */
    public static String balanceMessage(String name, Json json) {
        String balance = json.get("balance");
        String rewards = json.get("rewards");
        String did = json.get("did");
        if (A.isSecret(balance)) return null;
        return "Customer: " + name + "\n\n" +
                "Balance: $" + A.fmtAmt(balance, true) + "\n" +
                "Rewards: $" + A.fmtAmt(rewards, true) +
                A.nn(did); // if not empty, did includes leading blank lines
    }

    public static String customerName(String name, String company) {
        return (company == null || company.equals("")) ? name : (name + ", for " + company);
    }

    /**
     * Shrink the image to 10% and convert to grayscale.
     * @param image
     * @return: the shrunken image
     */
    public static byte[] shrink(byte[] image) {
        Bitmap bm = A.bray2bm(image);
        bm = scale(bm, PIC_H_OFFLINE);
        A.deb("shrink img len=" + image.length + " bm size=" + (bm.getRowBytes() * bm.getHeight()));

        Bitmap bmGray = Bitmap.createBitmap((int) (PIC_ASPECT * PIC_H_OFFLINE), PIC_H_OFFLINE, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmGray);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bm, 0, 0, paint);

        return A.bm2bray(bmGray);
    }

    public static Bitmap scale(Bitmap bm, int height) {
        return Bitmap.createScaledBitmap(bm, (int) (A.PIC_ASPECT * height), height, true);
    }

    public static void deb(String s) {
//        A.debugString = nn(A.debugString) + "\n" + nn(s);
//        Log.d("debug", A.debugString);
        Log.d("debug", s);
    }

    public static byte[] bm2bray(Bitmap bm) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public static Bitmap bray2bm(byte[] bray) {return BitmapFactory.decodeByteArray(bray, 0, bray.length);}

    public static String nn(String s) {return s == null ? "" : s;}
    public static String ucFirst(String s) {return s.substring(0, 1).toUpperCase() + s.substring(1);}
    public static String t(int stringResource) {return A.resources.getString(stringResource);}
    public static Long now0() {return System.currentTimeMillis() / 1000L;}
    public static Long now() {return A.timeFix + A.now0();}
}