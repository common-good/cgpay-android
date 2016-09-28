package org.rcredits.pos;
import android.annotation.TargetApi;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

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
 *
 * TESTS:
 * Upgrade AND Clean Install (run all relevant tests for each)
 * (assuming charge permission for cashier, all permissions except usd4r for agent)
 * A 0. Sign in, sign out. Charge customer $9,999.99. Get error message.
 * A 1. (signed out) Charge cust 11 cent. Check "balance" button. Repeat in landscape, charging 12 cents.
 *   2. (signed in) (undo and bal buttons show). Undo. Check "balance" button. Sign out (no balance or undo buttons).
 *   3. (signed in) refund customer 13 cents, check balance and undo, undo, check balance.
 *   4. (signed in) give customer 14 cents in rCredits for USD, check balance and undo, undo, check balance.
 * B 0. Install clean. Wifi off. Try scanning agent and customer card, check for good error messages. Wifi on, try scanning customer card (check for error message), sign in/out.
 *   1-4. Repeat A1-4 with wifi off, using amounts 21, 22, 23, and 24 cents.
 * C 1. (wifi off, signed out) Scan cust, wifi on, charge 31 cents.
 *   2. (wifi on, signed out) Scan cust, wifi off, charge 32 cents.
 * D 1. (wifi off) Sign in, scan cust, wifi on, charge 41 cents.
 *   2. (wifi off) Sign in, wifi on, scan cust, wifi off, charge 42 cents.
 *   3. (wifi off) Sign in, wifi on, scan cust, charge 43 cents.
 *   4. (wifi on) Sign in, wifi off, scan cust, charge 44 cents.
 *   5. (wifi on) Sign in, wifi off, scan cust, wifi on, charge 45 cents.
 *   6. (wifi on) Sign in, scan cust, wifi off, charge 46 cents.
 * E 1. (wifi on, signed in) Scan cust, charge 51 cents, wifi off, undo.
 *   2. (wifi off, signed in) Scan cust, charge 52 cents, wifi on, undo.
 * E After online reconciles, should see transactions for:
 *   11, 12, -12, -13, 13, -14, 14, 21, 31-32, 41-46, 51, -51, 52, -52 (or maybe neither 52 nor -52)
 */
public class A extends Application {
    public static boolean fakeScan = true; // for testing in simulator, without a webcam
    public static Context context;
    public static Resources resources;
    public static String versionCode; // version number of this software
    public static String versionName; // version name of this software
    private static SharedPreferences settings = null;
    private static String salt = null;
    private static ConnectivityManager cm = null;

    public static Long timeFix = 0L; // how much to add to device's time to get server time
//    public static String update = null; // URL of update to install on restart, if any ("1" means already downloaded)
    public static String failMessage = null; // error message to display upon restart, if any
    public static String serverMessage = null; // message from server to display when convenient
    public static String deviceId = null; // set once ever in storage upon first scan-in, read upon startup
    public static String debugString = null; // for debug messages
    public static Thread periodic = null; // periodic reconciliation process running in background
    public static Integer period = null; // how often to tickle (reconcile offline with server, etc.)
    public static boolean flip; // whether to flip the scanned image (for front-facing cameras)
    public static boolean positiveId; // did online customer identification succeed
    public static String packageName; // resource package
    public static SQLiteDatabase db_real;
    public static SQLiteDatabase db_test;

    // variables that get reset when testing (or not testing)
    public static boolean testing = false; // assume testing if scan is faked
    public static boolean wifiOff = false; // force wifi off
    public static boolean selfhelp = false; // whether to skip the photoID step and assume charging for default goods
    public static Db db; // real or test db (should be always open when needed)
    public static Json defaults = null; // parameters to use when no agent is signed in (empty if not allowed)
    public static String region = null; // device company's rCredits region (QID header) set upon scan-in
    public static String agent = null; // QID of device operator, set upon scan-in (eg NEW:AAB), otherwise null
    public static String xagent = null; // previous agent
    public static String agentName = null; // set upon scan-in
    public static boolean goingHome = false; // flag set to return to main activity

    // global variables that Periodic process must not screw up (refactor these to be local and passed)
//    public static Pairs rpcPairs = null; // parameters from last RPC request
    public static String customerName = null; // set upon identifying a customer
    public static String balance = null; // message about last customer's balance
    public static String undo = null; // message about reversing last transaction
    public static Long lastTxRow = null; // row number of last transaction in local db
    public static String httpError = null; // last HTTP failure message
    public static boolean doReport = false; // tells Periodic to send report to server

    public static List<String> descriptions; // set upon scan-in
    public final static String DESC_REFUND = "refund";
    public final static String DESC_USD_IN = "USD in";
    public final static String DESC_USD_OUT = "USD out";

    public static int can = 0; // what the user can do (permissions come from PrefsActivity, limited by server)
    public final static int CAN_CHARGE    = 0;
    public final static int CAN_UNDO      = 1;
    public final static int CAN_R4USD     = 2;
    public final static int CAN_USD4R     = 3;
    public final static int CAN_REFUND    = 4;
    public final static int CAN_BUY       = 5;
    public final static int CAN_U6        = 6; // unused
    public final static int CAN_MANAGE    = 7; // never true unless signed in

    public final static int CAN_CASHIER   = 0; // how far right to shift bits for cashier permissions
    public final static int CAN_AGENT     = 8; // how far right to shift bits for agent permissions

    public final static String MSG_DOWNLOAD_SUCCESS = "Update download is complete.";

    // values for status field in table txs
    public final static int TX_CANCEL = -1; // transaction has been canceled offline, but may exist on server
    public final static int TX_PENDING = 0; // transaction is being sent to server
    public final static int TX_OFFLINE = 1; // connection failed, offline transaction is waiting to be uploaded
    public final static int TX_DONE = 2; // completed transaction is on server

    public final static int REAL_PERIOD = 20 * 60; // (20 mins.) how often to tickle when not testing
    public final static int TEST_PERIOD = 20; // (20 secs.) how often to tickle
    public final static int PIC_H = 600; // standard pixel height of photos
    public final static int PIC_H_OFFLINE = 60; // standard pixel height of photos stored for offline use
    public final static double PIC_ASPECT = .75; // standard photo aspect ratio

    public final static int TX_AGENT = -1; // agent flag in AGT_FLAG field (negative to distinguish)
    public final static String NUMERIC = "^-?\\d+([,\\.]\\d+)?$"; // with exponents: ^-?\d+([,\.]\d+)?([eE]-?\d+)?$
    public final static int MAX_REWARDS_LEN = 20; // anything longer than this is a photoId in the rewards (db) field

    private final static String PREFS_NAME = "rCreditsPOS";
    private final static String REAL_API_PATH = "https://<region>.rcredits.org/pos"; // the real server (rc2.me fails)
//    private final static String REAL_API_PATH = "https://new.rcredits.org/pos"; // the only real server for now
    private final static String TEST_API_PATH = "https://stage-<region>.rcredits.org/pos"; // the test server
    //private final static String TEST_API_PATH = "http://192.168.2.101/rMembers/pos"; // testing locally
    private final static int TIMEOUT = 10; // how many seconds before HTTP POST times out

    @Override
    public void onCreate() {
        A.log(0);
        super.onCreate();

        A.context = this;
        A.resources = getResources();
        A.packageName = getApplicationContext().getPackageName();
        A.deviceId = getStored("deviceId");
        A.salt = getStored("salt");
        try {
            if (A.salt == null) setStored("salt", A.salt = A.getSalt());
        } catch (NoSuchAlgorithmException e) {e.printStackTrace();}

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            A.versionCode = pInfo.versionCode + "";
            A.versionName = pInfo.versionName + "";
        } catch (PackageManager.NameNotFoundException e) {e.printStackTrace();}

        A.cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        db_real = (new DbHelper(false)).getWritableDatabase();
        db_test = (new DbHelper(true)).getWritableDatabase();

        A.setMode(A.testing);

        Camera.CameraInfo info = new Camera.CameraInfo();
        if (!A.fakeScan) {
            Camera.getCameraInfo(0, info);
            flip = (Camera.getNumberOfCameras() == 1 && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
            //flip = true; versionCode = "0" + versionCode; // uncomment for old devices with only an undetectable front-facing camera.
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        System.setProperty("http.keepAlive", "false"); // as of 8/16/2016 this prevent background http POSTs from being duplicated
        // ... and possibly prevents timing bugs between background and foreground http POSTs?
        A.log(9);
    }

    /**
     * Return true if we can connect to the internet.
     */
    public static boolean connected() {
        NetworkInfo ni = A.cm.getActiveNetworkInfo();
        return (!A.wifiOff && ni != null && ni.isAvailable() && ni.isConnected());
    }

    /**
     * Change from test mode to real mode or vice versa
     * @param testing: <new mode is test mode (otherwise real)>
     */
    public static void setMode(boolean testing) {
        A.log(0);
        A.testing = testing;
        A.defaults = Json.make(A.getStored(testing ? "defaults_test" : "defaults")); // null if none
        A.signOut();

        A.db = new Db(testing);
        new Thread(new Periodic(A.db, testing ? A.TEST_PERIOD : A.REAL_PERIOD)).start();
        A.log(9);
    }

    public static void setDefaults(Json json, String ks) {
        A.log(0);
        if (json == null) return;

        if (ks != null) {
            for (String k : ks.split(" ")) A.defaults.put(k, json.get(k));
        } else A.defaults = json.copy();
        A.setStored(A.testing ? "defaults_test" : "defaults", A.defaults.toString());
        A.log(9);
    }
    public static void setDefaults(Json json) {setDefaults(json, null);}

    public static void useDefaults() {
        A.log(0);
        if (A.defaults == null) return;
        A.agent = A.xagent = A.defaults.get("default");
        A.region = rCard.qidRegion(A.agent);
        A.agentName = A.defaults.get("company");
        A.descriptions = A.defaults.getArray("descriptions");
        A.can = Integer.valueOf(A.defaults.get("can"));
        A.log(9);
    }

    /**
     * Reset all global variables to the "signed out" state.
     */
    public static void signOut() {
        A.log(0);
        A.agent = A.xagent = A.agentName = A.region = A.balance = A.undo = null;
        A.customerName = A.httpError = null;
        A.lastTxRow = null;
        A.useDefaults();
        A.log(9);
    }

    public static boolean signedIn() {return (A.defaults != null && !A.agent.equals(A.defaults.get("default")));}

    public static boolean selfhelping() {return (A.selfhelp && !A.signedIn());}

    /**
     * Return a "shared preference" (stored value) as a string (the only way we store anything).
     * @param name
     * @return the stored value ("" if none)
     */
    public static String getStored(String name) {
        if (A.settings == null) A.settings = A.context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return settings.getString(name, "");
    }

    public static void setStored(String name, String value) {
        SharedPreferences.Editor editor = A.settings.edit();
        editor.putString(name, value);
        editor.commit();
    }

    /**
     * Send a request to the server and return its response.
     * @param region: the agent's region
     * @param pairs: name/value pairs to send with the request (returned with extra info)
     * @return: the server's response. null if failure (with message in A.httpError)
     */
    public static HttpResponse post(String region, Pairs pairs) {
        A.log(0);
        final int timeout = TIMEOUT * 1000; // milliseconds

        String api = (A.testing ? TEST_API_PATH : REAL_API_PATH).replace("<region>", region);

        pairs.add("agent", A.agent);
        pairs.add("device", A.deviceId);
        pairs.add("version", A.versionCode);
        //pairs.add("location", A.location);
        pairs.add("region", region);
        if (!A.connected()) return A.log("not connected") ? null : null;

        String data = pairs.get("data"); if (data != null) A.log("datalen = " + data.length());
        A.log("post: " + api + " " + pairs.show("data")); // don't log data field sent with time op (don't recurse)

        HttpPost post = new HttpPost(api);
        //HttpClient client = new DefaultHttpClient();

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, timeout);
        HttpConnectionParams.setSoTimeout(params, timeout);
        DefaultHttpClient client = new DefaultHttpClient(params);

        try {
            post.setEntity(new UrlEncodedFormEntity(pairs.toPost()));
            A.log(9);
            return client.execute(post);
        } catch (Exception e) {
            String msg = e.getMessage();
            pairs.add("httpError", (msg != null && msg.equals("No peer certificate")) ? t(R.string.clock_off)
                : (t(R.string.http_err) + " (" + msg + ")"));
            return A.log(e) ? null : null;
        }
    }

    /**
     * Get a string response from the server
     * @param region: which regional server to ask
     * @param pairs: name/value pairs to send with the request (including op) (returned with extra info)
     * @return the response
     */
    public static Json apiGetJson(String region, Pairs pairs) {
        A.log(0);
        HttpResponse response = A.post(region, pairs);
        if (response == null) {A.log("got null"); return null;}
        try {
            String res = EntityUtils.toString(response.getEntity());
            A.log("got:" + res);
            A.log(9);
            return Json.make(res);
        } catch (IOException e) {return A.log(e) ? null : null;}
    }

    /**
     * Get the customer's photo from his/her server.
     * @param qid: the customer's account ID
     * @param code: the customer's rCard security code
     * @return: the customer's photo, as a byte array (length < 100 if invalid, null if no wifi)
     */
    public static byte[] apiGetPhoto(String qid, String code) {
        A.log(0);
        Pairs pairs = new Pairs("op", "photo");
        pairs.add("member", qid);
        pairs.add("code", code);
        HttpResponse response = A.post(rCard.qidRegion(qid), pairs);
        try {
            byte[] res = response == null ? null : EntityUtils.toByteArray(response.getEntity());
            A.log("photo len=" + (res == null ? 0 : res.length));
            A.log(9);
            return res;
        } catch (IOException e) {return A.log(e) ? null : null;}
    }

    /**
     * Get the server's clock time
     * @param data: whatever data the server just requested (null if no request)
     * @return the server's unixtime, as a string (null if not available)
     */
    public static String getTime(String data) {
        A.log(0);
        if (A.region == null) return null; // time is tied to region
        Pairs pairs = new Pairs("op", "time");
        if (data != null) pairs.add("data", data);
        HttpResponse response = A.post(region, pairs);
        if (response == null) return null;

        try {
            Json json = Json.make(EntityUtils.toString(response.getEntity()));
            if (json == null) return null;
            String msg = json.get("message");
            if (msg == null || msg.equals("")) return json.get("time");

//            if (data != null) { // don't risk tight looping (handle data only if called with no data)
                if (msg.equals("!log") || msg.equals("!members") || msg.equals("!txs")) { // send db data to server
                    return getTime(A.db.dump(msg.substring(1)));
                } else if (msg.equals("!device")) { // send device data to server
                    return getTime(A.getDeviceData());
                } else if (msg.length() > 8 && msg.substring(0, 8).equals("!delete:")) {
                    String[] parts = msg.split("[:,]");
                    int count = A.db.delete(parts[1], Long.valueOf(parts[2]));
                    return getTime("deleted:" + count);
                } else if (msg.equals("!report")) {
                    return A.report("report:");
//                }
            } // all other messages require the UI, so are handled in MainActivity
            if (A.serverMessage == null && !msg.substring(0, 1).equals("!")) A.serverMessage = msg; // don't overwrite
            A.log(9);
            return json.get("time");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Report something to the server on our own initiative
     * @param data
     */
    public static String report(String data) {
        /*
        final String data = data0;

        new Thread(new Runnable() { // run in background of course
            @Override
            public void run() {
                A.log(data, 2);
                A.doReport = true;
//                A.getTime(A.sysLog());
            }
        }).start();
        */
        A.log(data, 2);
        A.doReport = true;
        return null;
    }

    /**
     * Read the system log file.
     */
    public static String sysLog() {
        String res = "";
        String line;
        final int limit = 500; // number of lines to return
        ArrayList<String> commandLine = new ArrayList<String>();
        commandLine.add("logcat");
        commandLine.add("-t");
        commandLine.add("-" + limit);

        try {
            Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
//            Process process = Runtime.getRuntime().exec("logcat -t -" + limit); // better, if it works
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = bufferedReader.readLine()) != null) res += line + "|";
        } catch (java.io.IOException e) {
            return res;
        }

        return res.length() < 100 ? A.db.dump("log", limit) : res; // read logcat seems to not work. log table is similar
    }

/*

        try {
            Process process = Runtime.getRuntime().exec("logcat -t -500");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = bufferedReader.readLine()) != null) res += line + "|";
        } catch (java.io.IOException e) {}
        return res;
    }
     */

    /**
     * Return a json-encoded string of information about the device.
     * @return: json-encoded string of associative array
     */
    private static String getDeviceData() {
        Json j = new Json("{}"); // the overall result
        j.put("board", Build.BOARD);
        j.put("brand", Build.BRAND);
        j.put("device", Build.DEVICE);
        j.put("display", Build.DISPLAY);
        j.put("fingerprint", Build.FINGERPRINT);
        j.put("hardware", Build.HARDWARE);
        j.put("id", Build.ID);
        j.put("manufacturer", Build.MANUFACTURER);
        j.put("model", Build.MODEL);
        j.put("product", Build.PRODUCT);
        j.put("serial", Build.SERIAL);
        j.put("tags", Build.TAGS);
        j.put("time", Build.TIME + "");
        j.put("type", Build.TYPE);
        j.put("user", Build.USER);
        j.put("kLeft", A.db.kLeft() + "");

        j.put("defaults", A.defaults.toString());
        return j.toString();
    }

    /**
     * Set the device's system time (to match the relevant server's clock) unless time is null.
     * NOTE: some devices crash on attempt to set time (even with SET_TIME permission), so we fudge it.
     * @param time: unixtime, as a string (or null if time is not available)
     * @return <time was set successfully>
     */
    public static boolean setTime(String time) {
        A.log(0);
        if (time == null || time.equals("")) return false;

        //AlarmManager am = (AlarmManager) A.context.getSystemService(Context.ALARM_SERVICE);
        //am.setTime(time * 1000L);
        A.timeFix = Long.parseLong(time) - now0();
        A.log(9);
        return true;
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
     * Avoid single-thread limitation of some API versions.
     * Thanks to http://stackoverflow.com/questions/4068984/running-multiple-asynctasks-at-the-same-time-not-possible
     * @param asyncTask
     * @param params
     * @param <T>
     *//*
    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> asyncTask, T... params) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            A.log(">=honey");
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            A.log("<honey");
            asyncTask.execute(params);
        }
    }
*/

    /**
     * Say whether the agent can do something
     * @param permission: bits representing things the agent might do
     * @return true if the agent can do it
     */
    public static boolean can(int permission) {
        int can = A.can >> (A.signedIn() ? CAN_AGENT : CAN_CASHIER);
//        A.log("permission=" + permission + " can=" + can + " A.can=" + A.can + " signed in:" + A.signedIn() + " 1<<perm=" + (1<<permission));
        return ((can & (1 << permission)) != 0);
    }

    public static void setCan(int bit, boolean how) {A.can = how ? (A.can | (1 << bit)) : (A.can & ~(1 << bit));}

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
     * Return a formatted dollar amount (always show cents, no dollar sign).
     * @param n: the amount to format
     * @param commas: <include commas, if appropriate>
     * @return: the formatted amount
     */
    public static String fmtAmt(Double n, boolean commas) {
        NumberFormat num = NumberFormat.getInstance();
        num.setMinimumFractionDigits(2);
        String result = num.format(n);
        return commas ? result : result.replaceAll(",", "");
    }
    public static String fmtAmt(String v, boolean commas) {return fmtAmt(Double.valueOf(v), commas);}

    /**
     * Return a suitable message for when the "Show Customer Balance" button is pressed.
     * @param name
     * @param balance
     * @param rewards
     * @param did
     * @return
     */
     public static String balanceMessage(String name, String balance, String rewards, String did) {
        if (A.isSecret(balance)) return null;
        return "Customer: " + name + "\n\n" +
                "Balance: $" + A.fmtAmt(balance, true) + "\n" +
                "Credit Reserve: $" + A.fmtAmt(rewards, true) +
                A.nn(did); // if not empty, did includes leading blank lines
    }

    /**
     * Return a suitable message for when the "Show Customer Balance" button is pressed in demo mode.
     * @param name
     * @param qid
     * @return
     *//*
    public static String balanceMessage(String name, String qid) {
        assert A.demo;
        String where = "created>? AND member=? AND status<>?";
        String[] params = {"" + today(), qid, "" + TX_CANCEL};
        Double total = db.sum("amount", "txs", where, params);
        String balance = String.valueOf(1000 - total);
        total = db.sum("amount", "txs", "goods AND " + where, params);
        String rewards = String.valueOf(total * .10);
        return balanceMessage(name, balance, rewards, "");
    }
*/
    /**
     * Return a suitable message for when the "Show Customer Balance" button is pressed.
     * @param name: customer name (including agent and company name, for company agents)
     * @return: a suitable message or null if the balance is secret (or no data)
     */
    public static String balanceMessage(String name, Json json) {
        if (json == null) return null;
        String balance = json.get("balance");
        String rewards = json.get("rewards");
        String did = json.get("did");

        return balanceMessage(name, balance, rewards, did);
    }

    public static String customerName(String name, String company) {
        return (company == null || company.equals("")) ? name : (name + ", for " + company);
    }

    public static String hash(String text) {
        A.log(0);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes("UTF-8")); // Change this to "UTF-16" ifeeded
            A.log(9);
            return bytesToHex(md.digest());
//            return new String(md.digest());
        } catch(Exception e) {
            A.report(e.getMessage());
            return null;
        }
    }

    public static String bytesToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
/*
    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
*/
    public static byte[] hexToBytes(String str) {
        if (str == null || str.length() < 2) return null;
        int len = str.length() / 2;
        byte[] buffer = new byte[len];
        for (int i = 0; i < len; i++) {
            buffer[i] = (byte) parseInt(str.substring(i * 2, i * 2 + 2), 16);
        }
        return buffer;
    }
/*
    public static String encrypt(String text, String key) {
        key = padRight(key, 32);
        String iv = padRight(key, 16);
        if(text == null || text.length() == 0) return null;

        IvParameterSpec ivspec = new IvParameterSpec(iv.getBytes());
        SecretKeySpec keyspec = new SecretKeySpec(key.getBytes(), "AES");

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyspec, ivspec);
            return bytesToHex(cipher.doFinal(padRight(text).getBytes()));
        } catch (Exception e) {Log.e("encrypt", e.getMessage()); return null;}
    }


    public static String decrypt(String text, String key) {
        key = padRight(key, 32);
        String iv = padRight(key, 16);
        if(text == null || text.length() == 0) return null;

        IvParameterSpec ivspec = new IvParameterSpec(iv.getBytes());
        SecretKeySpec keyspec = new SecretKeySpec(key.getBytes(), "AES");

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyspec, ivspec);
            return (new String(cipher.doFinal(hexToBytes(text)))).trim();
        } catch (Exception e) {Log.e("decrypt", e.getMessage()); return null;}
    }


    public static String bytesToHex(byte[] data) {
        if (data == null) return null;
        int len = data.length;
        int c;
        String str = "";

        for (int i = 0; i < len; i++) {
            c = data[i] & 0xFF;
            str += (c < 16 ? "0" : "") + java.lang.Integer.toHexString(c);
        }
        return str;
    }

    public static byte[] hexToBytes(String str) {
        if (str == null || str.length() < 2) return null;
        int len = str.length() / 2;
        byte[] buffer = new byte[len];
        for (int i = 0; i < len; i++) {
            buffer[i] = (byte) parseInt(str.substring(i * 2, i * 2 + 2), 16);
        }
        return buffer;
    }
*/

    /**
     * Pad the given string to the given length n or multiple of n characters
     * @param s
     * @return
     */
  /*  private static String padRight(String s) {
        return String.format("%1$-" + (s.length() + 16 - (s.length() % 16)) + "s", s);
    } */
//    private static String padRight(String s, int n) {return String.format("%1$-" + n + "s", s).substring(0, n);}

    /*
    public static String hash(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256"); // -1, -256, -384, or -512
            md.update(A.salt.getBytes());
            byte[] bytes = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length ;i++) {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    */

    /**
     * Get salt for encryption.
     * @return a random salt
     * @throws NoSuchAlgorithmException
     */
    private static String getSalt() throws NoSuchAlgorithmException {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return salt.toString();
    }

    /**
     * Shrink the image to 10% and convert to grayscale.
     * @param image
     * @return: the shrunken image
     */
    public static byte[] shrink(byte[] image) {
        A.log(0);
        Bitmap bm = A.bray2bm(image);
        bm = scale(bm, PIC_H_OFFLINE);
        A.log("shrink img len=" + image.length + " bm size=" + (bm.getRowBytes() * bm.getHeight()));

        Bitmap bmGray = Bitmap.createBitmap((int) (PIC_ASPECT * PIC_H_OFFLINE), PIC_H_OFFLINE, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmGray);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bm, 0, 0, paint);

        A.log(9);
        return A.bm2bray(bmGray);
    }

    public static Bitmap scale(Bitmap bm, int height) {
        return Bitmap.createScaledBitmap(bm, (int) (A.PIC_ASPECT * height), height, true);
    }

    public static boolean log(String s, int traceIndex) {
        if (A.db != null) logDb(s, traceIndex > 0 ? traceIndex + 1 : 2); // redundant logging
        if (traceIndex != 0) {
            StackTraceElement l = new Exception().getStackTrace()[traceIndex]; // look at caller
            s += String.format(" - %s.%s %s", l.getClassName().replace("org.rcredits.pos.", ""), l.getMethodName(), l.getLineNumber());
        }
        Log.i("DEBUG", s);
        return true;
    }
    public static boolean log(String s) {return log(s, 2);}
    public static boolean log(int n) {return log("p" + n, 2);}

    /**
     * Log the given message in the log table, possibly for reporting to rCredits admin
     * @param s: the message
     */
    public static boolean logDb(String s, int traceIndex) {
        StackTraceElement l = new Exception().getStackTrace()[traceIndex]; // look at caller
        ContentValues values = new ContentValues();
        values.put("class", l.getClassName().replace("org.rcredits.", ""));
        values.put("method", l.getMethodName());
        values.put("line", l.getLineNumber());
        values.put("time", A.now());
        values.put("what", s);

        A.db.enoughRoom(); // always make room for logging, if there's any room for anything
        try {
            A.db.insert("log", values);
        } catch (Db.NoRoom noRoom) {} // nothing to be done
        return true;
    }

    /**
     * Log the exception.
     * @param e: exception object
     * @return true for the convenience of callers, for example: return Log(e) ? null : null;
     */
    public static boolean log(Exception e) {
        String trace = Log.getStackTraceString(e).replace("org.rcredits.pos.", "").replace("android.", ".");
        return A.log(e.getMessage() + "! " + terseTrace(trace), 0);
    }

    /**
     * Return an abbreviated stacktrace for common known issues
     * @param s
     * @return s abbreviated, if possible
     */
    private static String terseTrace(String s) {
        String msg;
        return (
            s.contains(msg = "Network is unreachable") ||
            s.contains(msg = "SSLPeerUnverifiedException") ||
            s.contains(msg = "Connection refused") ||
            s.contains(msg = "No address associated with hostname") ||
            s.contains(msg = "org.apache.http.conn.ConnectTimeoutException") ||
            s.contains(msg = "at java.net.InetAddress.lookupHostByName") ||
            s.contains(msg = "java.net.SocketTimeoutException") ||
            s.contains(msg = "javax.net.ssl.SSLException") ||
            s.contains(msg = "org.apache.http.conn.HttpHostConnectException") ||
            s.contains(msg = "java.net.SocketException")
        ) ? msg : s;
    }

    public static byte[] bm2bray(Bitmap bm) {
        A.log(0);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        A.log(9);
        return stream.toByteArray();
    }

    public static Bitmap bray2bm(byte[] bray) {return BitmapFactory.decodeByteArray(bray, 0, bray.length);}

    /**
     * Return the named image.
     * @param photoName: the image filename (no extension)
     * @return: a byte array image
     */
    public static byte[] photoFile(String photoName) {
        A.log(0);
        int photoResource = A.resources.getIdentifier(photoName, "drawable", A.packageName);
        Bitmap bm = BitmapFactory.decodeResource(A.resources, photoResource);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public static String nn(String s) {return s == null ? "" : s;}
    public static boolean empty(String s) {return nn(s).length() == 0;}
    public static boolean empty(byte[] b) {return (b == null || b.length == 0);}
    public static String nnc(String s) {return empty(s) ? "" : (s + ", ");}
//    public static String nn(CharSequence s) {return s == null ? "" : s.toString();}
    public static Long n(String s) {return s == null ? null : Long.parseLong(s);}
    public static String ucFirst(String s) {return s.substring(0, 1).toUpperCase() + s.substring(1);}
    public static String t(int stringResource) {return A.resources.getString(stringResource);}
    public static Long now0() {return System.currentTimeMillis() / 1000L;}
    public static Long now() {return A.timeFix + A.now0();}
    public static Long today() {return now() - (now() % (60 * 60 * 24));}
    public static Long daysAgo(int daysAgo) {return now() - daysAgo * 60 * 60 * 24;}
}