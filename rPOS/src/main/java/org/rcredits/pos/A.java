package org.rcredits.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

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
 * Created by William on 10/17/2013.
 *
 * Scan a company agent card to "scan in".
 * Scan a customer card to charge him/her.
 * Or to give a refund (managers only).
 *
 * RPC API Definition:
 *
 * Basic params (always sent):
 * op       the requested operation
 * device   the device ID (supplied once by the server) -- empty if none
 * agent    the already-scanned-in agent's agent ID (for example NEW:AAB) -- ignored for "update" op
 * position (optional) latitude,longitude,elevation (from the device's GPS)
 *
 * Specific Ops:
 * "update"
 * params: version
 * return: uri of update or empty if no update for this device
 *
 * "identify"
 * params: member
 * return: json (name, place, company, logon, descriptions, manager, device)
 *   device only if none yet
 *   company only if member is an agent/company customer
 *   device, descriptions and manager only if scanning in (logon is true)
 *
 * "photo"
 * params: member
 * return: bitstream of member's photo or empty if error
 *
 * "charge"
 * params: member, amount, description
 * return: json (success, message, tx, balance)
 *
 * "undo"
 * params: tx
 * return: json (success, message, balance)
 *
 * "refund"
 * params: member, amount
 * return: json (success, message, balance)
 *
 */
public class A extends Application {
    private final static String PREFS_NAME = "rCreditsPOS";
    private final static String HTTP_ERROR_MESSAGE = "The rCredits server is not reachable at the moment. Try again later.";

    //public final static String API_PATH = "http://<region>.rc2.me/pos"; // for eventually (when we have multiple cttys
    //public final static String API_PATH = "http://new.rc2.me/pos"; // while everyone is on one server
    //public final static String API_PATH = "http://ws.rcredits.org/pos"; // testing on smartphone
    public final static String API_PATH = "http://192.168.2.101/devcore/pos"; // testing on emulator
    public final static String REFUND_DESC = "refund";
    public final static String OTHER_DESC = "(other)";
    public final static Integer DESC_COUNT = 8; // number of description choices to show

    public static String deviceId = ""; // set once ever in storage upon first scan-in, read upon startup
    public static Boolean firstScan = true; // set false after first scan

    public static String region; // set upon scan-in
    public static String[] descriptions = new String[DESC_COUNT]; // set upon scan-in

    public static String agent = ""; // set upon scan-in (eg NEW:AAB)
    public static String agentName; // set upon scan-in
    public static Boolean canRefund = false; // agent can give customer a refund

    public static String balance = ""; // last customer's balance
    public static String amount; // sale or refund amount
    public static String lastTx = ""; // number of last transaction

    private static ProgressDialog progressDlg; // for standard progress spinner

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = getStored("deviceId");
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

    public Boolean mention(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        return false;
    }

    public static void sayError(Context context, String message,  DialogInterface.OnClickListener listener) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        if (listener == null) listener = new DialogInterface.OnClickListener() { // default to do nothing
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        };
        alertDialogBuilder
            .setTitle("Oops")
            .setMessage(message)
            .setIcon(R.drawable.alert_red)
            .setCancelable(false)
            .setPositiveButton("OK", listener);
            // .setNegativeButton("No", new DialogInterface.OnClickListener() {...
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    /**
     * Return a keyed value for an api response array (json-encoded).
     * @param json: json-encoded response from an API call
     * @param key: key to the value wanted
     * @return the value for that key
     */
    public static String jsonString(String json, String key) {
        try {
            JSONObject jsonObj = new JSONObject(json);
            return String.valueOf(jsonObj.get(key));
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static ArrayList<String> jsonArray(String json, String key) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            JSONObject jsonObj = new JSONObject(json);
            JSONArray jsonArray = jsonObj.getJSONArray(key);
            for (int i = 0; i < jsonArray.length(); i++) list.add(i, jsonArray.get(i).toString());
        } catch (JSONException e) {e.printStackTrace();}
        return list;
    }

    public static HttpResponse post(String region, String op, List<NameValuePair> pairs, Context context) {
        A.auPair(pairs, "op", op);
        A.auPair(pairs, "agent", A.agent);
        A.auPair(pairs, "device", A.deviceId);
        //A.auPair(pairs, "location", A.location);

        HttpPost post = new HttpPost(API_PATH.replace("<region>", region));
        //HttpClient client = new DefaultHttpClient();

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 5000);
        HttpConnectionParams.setSoTimeout(params, 5000);
        DefaultHttpClient client = new DefaultHttpClient(params);
        HttpResponse response;
        final Context context2 = context;

        try {
            if (context != null) progress(context);
            post.setEntity(new UrlEncodedFormEntity(pairs));
            response = client.execute(post);
        } catch (Exception e) {
            if (context != null) sayError(context, HTTP_ERROR_MESSAGE + " (" + e.getMessage() + ")", new DialogInterface.OnClickListener() { // default to do nothing
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    context2.startActivity(new Intent(context2, CaptureActivity.class));
                }
            });
            response = null;
        }
        if (context != null) progress(null);
        return response;
    }

    public static String apiGetData(String region, String op, List<NameValuePair> pairs, Context context) {
        HttpResponse response = A.post(region, op, pairs, context);
        try {
            return response == null ? null : EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] apiGetPhoto(String region, String qid, Context context) {
        HttpResponse response = A.post(region, "photo", A.auPair(null, "member", qid), context);
        try {
            return response == null ? null : EntityUtils.toByteArray(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<NameValuePair> auPair(List<NameValuePair> pairs, String name, String value) {
        if (pairs == null) pairs = new ArrayList<NameValuePair>(1);
        pairs.add(new BasicNameValuePair(name, value));
        return pairs;
    }

    /**
     * Show a standard "in progress" message.
     * @param context: call with activity.this to start, null to stop
     * @return the dialog object, so caller can dismiss it with dialogObj.dismiss();
     */
    public static void progress(Context context) {
        if (context == null) {
            A.progressDlg.dismiss();
        } else {
            A.progressDlg = ProgressDialog.show(context, "In Progress", "Contacting server...");
        }
    }

    public static void putIntentString(Intent intent, String key, String value) {
        String pkg = intent.getComponent().getPackageName();
        intent.putExtra(pkg + "." + key, value);
    }

    public static String getIntentString(Intent intent, String key) {
        String pkg = intent.getComponent().getPackageName();
        return intent.getStringExtra(pkg + "." + key);
    }

    public static String ucFirst(String s) {return s.substring(0, 1).toUpperCase() + s.substring(1);}
}