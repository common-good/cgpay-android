package com.scanner;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class ChangeAccountActivity extends BaseActivity {
	
	//Local variables
	String action_flag, data_from_scanner, str_my_id, str_account_id, str_code; 
	TextView txt_message;
	Button btn_back;
	
	TextView lbl_status, lbl_message;
	
	//Progress dialog variable
	ProgressDialog mDialog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.layout_change_agent);	//We would be using the same layout as the components are same
		
		
		//Try and get the flag passed to this activity
		Intent thisIntent = getIntent();
		action_flag = thisIntent.getStringExtra("action_flag");
		data_from_scanner = thisIntent.getStringExtra("data");
		//Get the account id from the scanned code
		str_account_id = getIDFromScannedData(data_from_scanner);
		str_my_id = retriveSettings("my_id", 1).toString();
		str_code = retriveSettings("loginToken", 1).toString();
		
		Log.e(tag, "The newly scanned id is -> " + str_my_id);
		
		/*
		//Init up the controls
		lbl_status = (TextView) findViewById(R.id.textView1);
		lbl_message = (TextView) findViewById(R.id.textView2);
		*/
		
		makeServerCallForChangeAgent();
		
		/*
		//The back button
		btn_back = (Button) findViewById(R.id.btnBack);
		btn_back.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				//Let's go back
				finish();				
			}
		});
		*/
	}
	
	private void makeServerCallForChangeAgent()
	{
		//Show the progress bar dialog
		mDialog = new ProgressDialog(ChangeAccountActivity.this);
		mDialog.setMessage("Please wait...");
		mDialog.show();
		
		new Thread(new Runnable() {
        	public void run() {
        		//Our start load goes here
        		JSONObject apiParamsCA = new JSONObject();
        		try
        		{
        			apiParamsCA.put("my_id", str_my_id.toUpperCase());	//Though this is not mandatory from 4th Nov, 2012, but omitting it server returns "BAD ID FORMAT"
        			apiParamsCA.put("code", str_code);
        			apiParamsCA.put("account_id", str_account_id);
        			apiParamsCA.put("what", "account");
        			apiParamsCA.put("op", "change");
        			
        			//Now further create a name value pair to be passed to the http post
        			List<NameValuePair> nameValuePairs;
        	    	nameValuePairs = new ArrayList<NameValuePair>(1);  

        	    	// Adding parameters to send to the HTTP server.
        	    	nameValuePairs.add(new BasicNameValuePair("json", apiParamsCA.toString()));
        	    	//Make the server call
        	    	String changeAccountResp = makeAPICall(nameValuePairs);
        	    	Log.e(tag, changeAccountResp);
        	    	//Update UI with response
        	    	updateUI(changeAccountResp);
        		}
        		catch(JSONException je)
        		{
        			Log.e(tag, je.toString());
        		}
        	}
        }).start();
	}
	
	private void updateUI(final String changeAccountResp)
	{
		//Update UI on a UI thread
		//Get message and all that from response
		final String str_status, str_message, str_account_name;
		str_status = getValueForJSONKey(changeAccountResp, "success");
		str_message = getValueForJSONKey(changeAccountResp, "message");
		str_account_name = getValueForJSONKey(changeAccountResp, "account_name");
		
    	runOnUiThread(new Runnable() {
			public void run() {
				mDialog.dismiss();
				//Depending on success or failure
				if(str_status.equals("0"))
				{
					//Failed, notified user
					/*lbl_status.setText("Failure");
					lbl_message.setText(str_message);*/
					
					showDialogOKFinishesNegative(tag,  str_message);
				}
				else
				{
					//Success
					/*
					lbl_status.setText("Success");
					lbl_message.setText(str_message);
					*/
					//Additionally set the new my_id
					String str_my_id = getValueForJSONKey(changeAccountResp, "my_id");
					saveSettings("my_id", str_my_id, 1);
					saveSettings("account_name", str_account_name, 1);
					
					showDialogOKFinishes(tag,  str_message);
					
				}
			}
    	});
	}
}
