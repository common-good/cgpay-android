package com.scanner;

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;

public class BaseActivity extends Activity {
	String tag = "rCredits";
	public String API_PATH = "http://devcore.rcredits.org/api"; 
	public static final String PREFS_NAME = "rCreditsPreferences";
	protected final String[] transactionMode = { "Charge", "Authorize"};
	//Progress dialog variable
	ProgressDialog mDialog;
	
	/**
	 * 
	 * Retrieves the data for passed in key and dataTypeIndex from
	 * sharedpreferences
	 */
	public Object retriveSettings(String key, int dataTypeIndex) {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME,
				MODE_PRIVATE);
		// Create a object to hold the return type from sharedPReferences
		Object obj = new Object();
		// Depending on the passed datatype we use different functions
		switch (dataTypeIndex) {
		case 1:
			// We set 1 for string
			obj = settings.getString(key, "");
			break;
		default:
			// For everything else but we don't implement it right now
			break;
		}

		// Finally return obj
		return obj;
	}

	/**
	 * The following function actually saves the data into the shared
	 * preferences
	 */
	public void saveSettings(String key, String val, int dataTypeIndex) {
		// Save data to local using Shared Preferences
		SharedPreferences settings = getSharedPreferences(PREFS_NAME,
				MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();

		// Depending on the passed datatype we use different functions
		switch (dataTypeIndex) {
			case 1:
				// We set 1 for string
				editor.putString(key, val);
				break;
			default:
				// For everything else but we don't implement it right now
				break;
		}

		// When done, commit
		editor.commit();
	}
	
	/**
	 * Accepts the entire JSON and reads a key from it and returns the value 
	 * @param theJSON
	 * @param key
	 * @return
	 */
	public String getValueForJSONKey(String theJSON, String key)
	{
		JSONObject jsonToBeLoaded;
		String retVal;
		try {
			jsonToBeLoaded = new JSONObject(theJSON);
			retVal = String.valueOf(jsonToBeLoaded.get(key));
			
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			retVal = "";
		}
		
		return retVal;
	}
	
	/**
	 * We processs the ID part out of the scanned data and return it
	 * @param scanned_data
	 * @return
	 */
	public String getIDFromScannedData(String scanned_data)
	{
		//The scanned data is of the format of a URL, we 
		//split it on the basis of / and return the last part 
		String[] dataParts = scanned_data.split("/");
		return dataParts[dataParts.length - 1];
	}
	
	/**
	 * 
	 * @param callParams
	 * @return
	 */
	public String makeAPICall(List<NameValuePair> callParams)
	{
		String callResponse = "";
		HttpPost httppost;
    	HttpClient httpclient;

    	//The path of the api and the obj init up with the same
    	httppost = new HttpPost(API_PATH); //Real server
    	
    	httpclient = new DefaultHttpClient();
    	

    	// Send POST message  with given parameters to the HTTP server.
    	try {                    
	    	httppost.setEntity(new UrlEncodedFormEntity(callParams));  
	
	    	HttpResponse response = httpclient.execute(httppost);
	    	HttpEntity httpEntity = response.getEntity();
            String apidata = EntityUtils.toString(httpEntity);
            
            //Log the response
            Log.i(tag, apidata);
            //And assign the same to the return value
            callResponse = apidata;
	    	
    	}
    	catch (Exception e) {
    		// Exception handling
    		callResponse = e.toString();		//Error
    	}
    	
    	return callResponse;
	}
	
	public byte[] makeAPICallForImage(List<NameValuePair> callParams)
	{
		byte[] callResponse = null;
		HttpPost httppost;
    	HttpClient httpclient;

    	//The path of the api and the obj init up with the same
    	httppost = new HttpPost(API_PATH); //Real server
    	
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
    		//Do nothing
    	}
    	
    	return callResponse;
	}
	
	/**
	 * The following shows a dialog box
	 */
	public void showDialog(String title, String msg) {
		AlertDialog.Builder diaolg = new AlertDialog.Builder(this);
		diaolg.setTitle(title);
		diaolg.setMessage(msg);
		diaolg.setCancelable(true);
		diaolg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// do things
			}
		});

		diaolg.show();
	}
	
	/**
	 * The following shows a dialog box with red alert
	 */
	public void showDialogNegative(String title, String msg) {
		AlertDialog.Builder diaolg = new AlertDialog.Builder(this);
		diaolg.setTitle(title);
		diaolg.setMessage(msg);
		diaolg.setIcon(R.drawable.alert_red);
		diaolg.setCancelable(true);
		diaolg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// do things
			}
		});

		diaolg.show();
	}
	
	/**
	 * The following shows a dialog box
	 */
	public void showDialogOKFinishes(String title, String msg) {
		AlertDialog.Builder diaolg = new AlertDialog.Builder(this);
		diaolg.setTitle(title);
		diaolg.setMessage(msg);
		diaolg.setCancelable(true);
		diaolg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// do things
				finish();
			}
		});

		diaolg.show();
	}
	
	/**
	 * The following shows a dialog box, pressing ok, finishes and with a negativbe icon
	 */
	public void showDialogOKFinishesNegative(String title, String msg) {
		AlertDialog.Builder diaolg = new AlertDialog.Builder(this);
		diaolg.setTitle(title);
		diaolg.setMessage(msg);
		diaolg.setIcon(R.drawable.alert_red);
		diaolg.setCancelable(true);
		diaolg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// do things
				finish();
			}
		});

		diaolg.show();
	}
	
}
