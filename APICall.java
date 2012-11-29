package org.rcredits;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

public class APICall extends AsyncTask<BasicNameValuePair, Void, String> {

	protected String doInBackground(BasicNameValuePair... pairs) {
	    JSONObject apiParams = new JSONObject();
	    try {
          for (BasicNameValuePair pair : pairs) apiParams.put(pair.getName(), pair.getValue());
          BasicNameValuePair[] json = {new BasicNameValuePair("json", apiParams.toString())};
	      return makeAPICall(Arrays.asList(json));
        } catch(JSONException je) {
          Log.e(tag, je.toString());
          return "";
          // For now, tell user connection to server failed and return to main menu
          // Later, Set flag and warn user that all transactions will be offline  
        }
	}

    protected void onProgressUpdate() {
        //setProgressPercent(progress[0]);
    }

    protected void onPostExecute(String answer) {
      if (answer.equals("")) JOptionPane.showMessageDialog(MSG_CANT_CONNECT);
    }
}
