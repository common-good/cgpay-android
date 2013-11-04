package org.rcredits.pos;

import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * rCredits POS App
 *
 * Scan a company agent card to "scan in".
 * Scan a customer card to doTx him/her.
 * Or to give a refund (managers only).
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
 */
public class A extends Application {
    public final static boolean FAKE_SCAN = true; // disable scanning, for testing other features

    public final static String DESC_REFUND = "refund";
    public final static String DESC_CASH_IN = "cash in";
    public final static String DESC_CASH_OUT = "cash out";
    public final static String APP_FILENAME = "rPOS.apk"; // internal filename of downloaded update(s)

    public static String versionCode; // VERSION number of this software
    public static String update = ""; // URL of update to install on restart, if any ("1" means already downloaded)
    public static String failMessage = ""; // error message to display upon restart, if any
    public static String deviceId; // set once ever in storage upon first scan-in, read upon startup
    public static String region; // set upon scan-in
    public static List<String> descriptions; // set upon scan-in
    public static String agent = ""; // set upon scan-in (eg NEW:AAB)
    public static String agentName; // set upon scan-in
    public static int can = 0; // what the agent can do

    public final static int CAN_CHOOSE_DESC = 1 << 0;
    public final static int CAN_REFUND =      1 << 1;
    public final static int CAN_SELL_CASH =   1 << 2;
    public final static int CAN_BUY_CASH =    1 << 3;

    public static String balance = ""; // message about last customer's balance
    public static String undo = ""; // message about reversing last transaction
    public static String lastTx = ""; // number of last transaction

    public final static String MSG_DOWNLOAD_SUCCESS = "Update download is complete.";
    private final static String MSG_HTTP_ERROR = "The rCredits server is not reachable at the moment. Try again later.";

    private final static String PREFS_NAME = "rCreditsPOS";
    //private final static String API_PATH = "http://<region>.rc2.me/pos"; // for eventually (when we have multiple cttys
    //private final static String API_PATH = "http://new.rc2.me/pos"; // while everyone is on one server
    private final static String API_PATH = "http://ws.rcredits.org/pos"; // testing on smartphone
    //private final static String API_PATH = "http://192.168.2.101/devcore/pos"; // testing on emulator

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = getStored("deviceId");
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = pInfo.versionCode + "";
        } catch (PackageManager.NameNotFoundException e) {e.printStackTrace();}
    }

    public String getStored(String name) {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(name, "");
    }

    public void setStoredValue(String name, String value) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(name, value);
        editor.commit();
    }

    /**
     * Return a keyed value from an api response array (json-encoded).
     * @param json: json-encoded response from an API call
     * @param key: key to the value wanted
     * @return the value for that key
     */
    public static String jsonString(String json, String key) {
        try {
            JSONObject jsonObj = new JSONObject(json);
            return String.valueOf(jsonObj.get(key));
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * Return a keyed array from an api response array (json-encoded).
     * @param json: json-encoded response from an API call
     * @param key: key to the array wanted
     * @return: the array for that key
     */
    public static ArrayList<String> jsonArray(String json, String key) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            JSONObject jsonObj = new JSONObject(json);
            JSONArray jsonArray = jsonObj.getJSONArray(key);
            for (int i = 0; i < jsonArray.length(); i++) list.add(i, jsonArray.get(i).toString());
        } catch (JSONException e) {e.printStackTrace();}
        return list;
    }

    /**
     * Send a request to the server and return its response.
     * @param a: the current Activity
     * @param region: the agent's region
     * @param op: the requested operation
     * @param pairs: name/value pairs to send with the request
     * @return: the server's response
     * (restarts on failure)
     */
    public static HttpResponse post(Act a, String region, String op, List<NameValuePair> pairs) {
        a.mention("starting");
        a.progress(true);
        A.auPair(pairs, "op", op);
        A.auPair(pairs, "agent", A.agent);
        A.auPair(pairs, "device", A.deviceId);
        A.auPair(pairs, "version", A.versionCode);
        //A.auPair(pairs, "location", A.location);

        HttpPost post = new HttpPost(API_PATH.replace("<region>", region));
        //HttpClient client = new DefaultHttpClient();

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 5000);
        HttpConnectionParams.setSoTimeout(params, 5000);
        DefaultHttpClient client = new DefaultHttpClient(params);
        HttpResponse response;

        try {
            post.setEntity(new UrlEncodedFormEntity(pairs));
            response = client.execute(post);
        } catch (Exception e) {
            a.sayFail(MSG_HTTP_ERROR + " (" + e.getMessage() + ")");
            response = null;
        }
        a.progress(false);
        return response;
    }

    /**
     * Get a string response from the server (usually json-encoded)
     * @param a: the current Activity
     * @param region: the agent's region
     * @param op: the requested operation
     * @param pairs: name/value pairs to send with the request
     * @return
     */
    public static String apiGetData(Act a, String region, String op, List<NameValuePair> pairs) {
        HttpResponse response = A.post(a, region, op, pairs);
        try {
            return response == null ? null : EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get a string response from the server (usually json-encoded)
     * @param a: the current Activity
     * @param region: the agent's region
     * @param qid: the custoemr's account id
     * @return: the customer's photo, as a byte array
     */
    public static byte[] apiGetPhoto(Act a, String region, String qid) {
        HttpResponse response = A.post(a, region, "photo", A.auPair(null, "member", qid));
        try {
            return response == null ? null : EntityUtils.toByteArray(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert the name and value to a "pair" (a NameValuePair) and add it to the list.
     * @param pairs: (returned) the list to add to (null if no list yet)
     * @param name: name of parameter to add
     * @param value: value of paramter to add
     * @return: the modified list
     */
    public static List<NameValuePair> auPair(List<NameValuePair> pairs, String name, String value) {
        if (pairs == null) pairs = new ArrayList<NameValuePair>(1);
        pairs.add(new BasicNameValuePair(name, value));
        return pairs;
    }

    /**
     * Send a request to the server to doTx someone (or credit them, if amount param is negative)
     * @param a: the current Activity
     * @param op: type of transaction
     * @param pairs: additional parameters to the transaction
     * (restarts, whether successful or not)
     */
    public static void doTx(Act a, String op, List<NameValuePair> pairs) {
        final Act a2 = a;
        final String json = A.apiGetData(a, A.region, op, pairs);
        if (json == null) return; // probably server is down, so let user try again
        String message = A.jsonString(json, "message");
        if (A.jsonString(json, "ok").equals("1")) {
            a.sayOk("Success!", message, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    A.balance = A.jsonString(json, "balance");
                    A.undo = A.jsonString(json, "undo");
                    A.lastTx = A.jsonString(json, "tx");
                    a2.restart();
                }
            });
        } else {
            a.sayError(message, null);
        }
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

    public static String ucFirst(String s) {return s.substring(0, 1).toUpperCase() + s.substring(1);}
}