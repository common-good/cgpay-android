package org.rcredits;

import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import org.rcredits.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.AsyncTask;
import android.os.HandlerThread;

public class BaseActivity extends Activity {
  String tag = "rCredits";
  public String API_PATH = "http://devcore.rcredits.org/api"; 
  public static final String PREFS_NAME = "rCreditsPreferences";
  protected final String[] transactionMode = { "Charge", "Authorize"};
  public static final String MSG_CANT_CONNECT = "Connection to the rCredits Server is not possible at this time.";
  public static final String MSG_FATAL_TITLE = "Alas";
  
  // Progress dialog variable
  ProgressDialog mDialog;
  
  /**
   * Retrieves data passed in key from sharedpreferences
   */
  public String getData(String key) {
    return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, "");
  }

  /**
   * Save the data into the shared preferences
   */
  public void putData(String key, String val) {
    // Save data to local using Shared Preferences
    SharedPreferences settings = getSharedPreferences(PREFS_NAME,
        MODE_PRIVATE);
    SharedPreferences.Editor editor = settings.edit();
    editor.putString(key, val);
    editor.commit();
  }
  
  /**
   * Accepts the entire JSON and reads a key from it and returns the value 
   * @param theJSON
   * @param key
   * @return
   */
  public String getValueForJSONKey(String theJSON, String key) {
    JSONObject jsonToBeLoaded;
    try {
      jsonToBeLoaded = new JSONObject(theJSON);
      return String.valueOf(jsonToBeLoaded.get(key));
    } catch (JSONException e) {
      e.printStackTrace();
      return "";
    }
  }
  
  /**
   * We processs the ID part out of the scanned data and return it
   * @param scanned_data
   * @return
   */
  public String getIDFromScannedData(String scanned_data)  {
    // The scanned data is of the format of a URL, we 
    // split it on the basis of / and return the last part 
    String[] dataParts = scanned_data.split("/");
    return dataParts[dataParts.length - 1];
  }
  
  /**
   * 
   * @param callParams
   * @return
   */
  public String makeAPICall(List<NameValuePair> callParams) {
    String callResponse = "";
    HttpPost httppost;
      HttpClient httpclient;

      // The path of the api and the obj init up with the same
      httppost = new HttpPost(API_PATH); // Real server
      httpclient = new DefaultHttpClient();

      // Send POST message with given parameters to the HTTP server.
      try {                    
        httppost.setEntity(new UrlEncodedFormEntity(callParams));  
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity httpEntity = response.getEntity();
        callResponse = EntityUtils.toString(httpEntity);
        Log.i(tag, callResponse);
      } catch (Exception e) {
        //callResponse = e.toString();
          putData("die", "yes"); // System.exit(0);  
          putData("finish_on_load", "");
          runOnUiThread(new Runnable() {
        	  public void run() {
        	  showDialogNegative(MSG_FATAL_TITLE, MSG_CANT_CONNECT);
        	  }
        	});        	  
          return "";
      }
      return callResponse;
  }
  
  public byte[] makeAPICallForImage(List<NameValuePair> callParams) {
    byte[] callResponse = null;
    HttpPost httppost;
      HttpClient httpclient;

      // The path of the api and the obj init up with the same
      httppost = new HttpPost(API_PATH); // Real server
      
      httpclient = new DefaultHttpClient();
      

      // Send POST message  with given parameters to the HTTP server.
      try {                    
        httppost.setEntity(new UrlEncodedFormEntity(callParams));  
  
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity httpEntity = response.getEntity();
        callResponse = EntityUtils.toByteArray(httpEntity);
      }
      catch (Exception e) {
        // Exception handling
        // Do nothing
      }
      
      return callResponse;
  }
  
  /**
   * The following shows a dialog box
   */
  public void showDialog(String title, String msg) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(title);
    dialog.setMessage(msg);
    dialog.setCancelable(true);
    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
      }
    });
    dialog.show();
  }
  
  /**
   * The following shows a dialog box with red alert
   */
  public void showDialogNegative(final String title, String msg) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(title);
    dialog.setMessage(msg);
    dialog.setIcon(R.drawable.alert_red);
    dialog.setCancelable(true);
    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
          if (title.equals(MSG_FATAL_TITLE)) System.exit(0);
      }
    });

    dialog.show();
  }
  
  /**
   * The following shows a dialog box
   */
  public void showDialogOKFinishes(String title, String msg) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(title);
    dialog.setMessage(msg);
    dialog.setCancelable(true);
    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // do things
        finish();
      }
    });

    dialog.show();
  }
  
  /**
   * The following shows a dialog box, pressing ok, finishes and with a negativbe icon
   */
  public void showDialogOKFinishesNegative(String title, String msg) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(title);
    dialog.setMessage(msg);
    dialog.setIcon(R.drawable.alert_red);
    dialog.setCancelable(true);
    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // do things
        finish();
      }
    });

    dialog.show();
  }
}
