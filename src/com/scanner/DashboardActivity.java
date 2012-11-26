package com.scanner;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.android.barcodescanner.CaptureActivity;

public class DashboardActivity extends BaseActivity {
	//Variables
	String my_id, code, userDataResponse, str_update_link;
	String str_tx_id;
	//Controls 
	Button btn_charge, btn_pay, btn_change_agent, btn_undo_last, btn_cust_balance, btn_update_app, btn_change_account;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.layout_dashboard);
		
		//First keep a bread crumb for the welcome screen within the shared preferences
		saveSettings("finish_on_load", "1", 1);
		
		//The controls and buttons and everything
		//The charge button
		btn_charge = (Button) findViewById(R.id.btnCharge);
		//The pay button
		btn_pay = (Button) findViewById(R.id.btnPay);
		//The change agent (scan) button
		btn_change_agent = (Button) findViewById(R.id.btnChangeAgent);
		//Button undo last
		btn_undo_last = (Button) findViewById(R.id.btnUndoLast);
		//Customer balance
		btn_cust_balance = (Button) findViewById(R.id.btnShowBalance);
		//Update app
		btn_update_app = (Button) findViewById(R.id.btnUpdateApp);
		//Change account button
		btn_change_account = (Button) findViewById(R.id.btnChangeAccount);
		
		//Get some API related data from the shared preferences
		my_id = retriveSettings("my_id", 1).toString();
		code = retriveSettings("loginToken", 1).toString();
		str_update_link = retriveSettings("update_link", 1).toString();
	}
	
	@Override
	protected void onStart()
	{
		Log.e(tag, "Dashboard activity on start");
		//Prepare interface
		prepareInterface();
		super.onStart();
	}
	
	/**
	 * Prepares the interfaces, sets up buttons and events etc
	 */
	public void prepareInterface()
	{
		//Button charge event
		btn_charge.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				//First keep a bread crumb for the welcome screen within the shared preferences
				saveSettings("finish_on_load", "1", 1);
				//Loads up the scanner, then depending on passed params, loads up the charge screen
				Intent intent = null;
				intent = new Intent(DashboardActivity.this, CaptureActivity.class);
				intent.putExtra("target", "pay_charge_activity");
				intent.putExtra("action_flag", "charge");
				startActivity(intent);
				
				/*
				//TEST TEST TEST
				Intent intent = new Intent(DashboardActivity.this, PayChargeActivity.class);
				startActivity(intent);
				*/
			}
		});
		
		//Button pay event
		btn_pay.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//First keep a bread crumb for the welcome screen within the shared preferences
				saveSettings("finish_on_load", "1", 1);
				//Load up the pay screen, with the params
				Intent intent = new Intent(DashboardActivity.this, CaptureActivity.class);
				intent.putExtra("target", "pay_charge_activity");
				intent.putExtra("action_flag", "pay");
				startActivity(intent);
			}
		});
		
		//Button change agent event
		btn_change_agent.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				//First keep a bread crumb for the welcome screen within the shared preferences
				saveSettings("finish_on_load", "1", 1);
				//Start for scanning
				Intent intent = null;
				intent = new Intent(DashboardActivity.this, CaptureActivity.class);
				intent.putExtra("target", "change_agent");
				intent.putExtra("action_flag", "change");
				startActivity(intent);
			}
		});
		
		//Check if there was a transaction done, and if that could be un done
		str_tx_id = retriveSettings("tx_id", 1).toString();
		if(str_tx_id.equals(""))
		{
			//Hide it
			btn_undo_last.setVisibility(View.GONE);
		}
		else
		{
			//We have a last transaction id, show the undo button
			btn_undo_last.setVisibility(View.VISIBLE);
		}
		
		//The tap event for the undo button
		btn_undo_last.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//Show the progress bar dialog
				mDialog = new ProgressDialog(DashboardActivity.this);
				mDialog.setMessage("Please wait...");
				mDialog.show();
				//Need to make API call, make it on a new thread
				new Thread(new Runnable() {
		        	public void run() {
						//**************The actual API call follows****************
						JSONObject apiParamsJsonUndo = new JSONObject();
						//Add name value pairs in it
						try
						{
							apiParamsJsonUndo.put("my_id", my_id);
							apiParamsJsonUndo.put("code", code);
							apiParamsJsonUndo.put("op", "undo");
							apiParamsJsonUndo.put("tx_id", str_tx_id);
							apiParamsJsonUndo.put("confirmed", "0");
							//Now further create a name value pair to be passed to the http post
							List<NameValuePair> nameValuePairsUndo;
							nameValuePairsUndo = new ArrayList<NameValuePair>(1);
					    	// Adding parameters to send to the HTTP server.
							nameValuePairsUndo.add(new BasicNameValuePair("json", apiParamsJsonUndo.toString()));
					    	
					    	userDataResponse = makeAPICall(nameValuePairsUndo);
					    	
					    	//UPDAYTE UI ON UI THREAD
					    	runOnUiThread(new Runnable() {
			        			public void run() {
			        				//Dismiss the dialog
			        				mDialog.dismiss();
			        				
			        				//Check for success in the last API call
			        				String str_success = getValueForJSONKey(userDataResponse, "success");
			        				if(str_success.equals("1"))
			        				{
			        					//Ask from user with the message obtained from the server
			        					String str_message = getValueForJSONKey(userDataResponse, "message");
			        					askAndFireLastTransactionUndo(str_message);
			        				}
			        			}
			        		});
					    	
						}
						catch(JSONException je)
						{
							Log.e(tag, je.toString());
						}
						//*********************************************************
						
		        	}
				}).start();
				
				
			}
		});
		
		//Check if the last transaction customer balance could be seen
		String str_cust_balance = retriveSettings("other_balance", 1).toString();
		if(str_cust_balance.equals(""))
		{
			//No balance to be shown
			btn_cust_balance.setVisibility(View.GONE);
		}
		else
		{
			btn_cust_balance.setVisibility(View.VISIBLE);
		}
		//The tap event for the cust balance
		final String local_cust_balance = str_cust_balance;
		btn_cust_balance.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				//Show me the customer balance
				showDialog(tag, "The customer balance after the last successful transaction is: " + local_cust_balance);
			}
		});
		
		//The tap event for the change account event
		btn_change_account.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				//First keep a bread crumb for the welcome screen within the shared preferences
				saveSettings("finish_on_load", "1", 1);
				//Start for scanning
				Intent intent = null;
				intent = new Intent(DashboardActivity.this, CaptureActivity.class);
				intent.putExtra("target", "change_account");
				intent.putExtra("action_flag", "change");
				startActivity(intent);
				
			}
		});
		
		//Check if the update app button is at all required
		if(str_update_link.equals("") || str_update_link == null)
		{
			//Hide the link
			btn_update_app.setVisibility(View.GONE);
			Log.e(tag, "UPDATE APP SHOULD BE HIDDEN -> " + str_update_link);
		}
		else
		{
			//Show the update app button
			btn_update_app.setVisibility(View.VISIBLE);
			Log.e(tag, "UPDATE APP SHOULD BE DISPLAYED -> " + str_update_link);
		}
		
		//The tap event for the update button
		btn_update_app.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//Let's open up the link in browser for the time being
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(str_update_link)));
				
			}
		});
	}
	
	/**
	 * Confirms from user and fires a API call to the server to UNDO the last transaction
	 * @param strMessage
	 */
	public void askAndFireLastTransactionUndo(final String strMessage)
	{
		AlertDialog.Builder diaolg = new AlertDialog.Builder(this);
		diaolg.setTitle(tag);
		diaolg.setMessage(strMessage);
		diaolg.setCancelable(true);
		diaolg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				//API call to UNDO transaction
				dialog.dismiss();
				
				//Show progress bar
				mDialog = new ProgressDialog(DashboardActivity.this);
				mDialog.setMessage("Please wait...");
				mDialog.show();
				
				//**************The Actual API call again, this time with no confirmation*****************
				JSONObject apiParamsJsonUndo = new JSONObject();
				//Add name value pairs in it
				try
				{
					apiParamsJsonUndo.put("my_id", my_id);
					apiParamsJsonUndo.put("code", code);
					apiParamsJsonUndo.put("op", "undo");
					apiParamsJsonUndo.put("tx_id", str_tx_id);
					apiParamsJsonUndo.put("confirmed", "1");
					//Now further create a name value pair to be passed to the http post
					List<NameValuePair> nameValuePairsUndo;
					nameValuePairsUndo = new ArrayList<NameValuePair>(1);
			    	// Adding parameters to send to the HTTP server.
					nameValuePairsUndo.add(new BasicNameValuePair("json", apiParamsJsonUndo.toString()));
			    	
			    	userDataResponse = makeAPICall(nameValuePairsUndo);
			    	
			    	//UPDATE UI ON UI THREAD
			    	runOnUiThread(new Runnable() {
	        			public void run() {
	        				//Dismiss the dialog
	        				mDialog.dismiss();
	        				
	        				//Check for success in the last API call
	        				String str_success = getValueForJSONKey(userDataResponse, "success");
	        				String str_message = getValueForJSONKey(userDataResponse, "message");
	        				if(str_success.equals("1"))
	        				{
	        					showDialog(tag, "The last transaction is cancelled successfully");
	        					
	        					//It is assumed that the transaction is undone, now remove 
	        					//the transactionid and the last customer balance from the system
	        					saveSettings("tx_id", "", 1);
	        					saveSettings("other_balance", "", 1);
	        					//The the buttons as well
	        					btn_undo_last.setVisibility(View.GONE);
	        					btn_cust_balance.setVisibility(View.GONE);
	        				}
	        				else
	        				{
	        					showDialogNegative(tag, str_message);
	        					//showDialog(tag, str_message);
	        				}
	        			}
	        		});
			    	
				}
				catch(JSONException je)
				{
					Log.e(tag, je.toString());
				}
				//***********************************************************************************
			}
		});
		
		diaolg.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// do nothing and dissmiss this dialog
				dialog.dismiss();
			}
		});

		diaolg.show();
	}
}
