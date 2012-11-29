package org.rcredits;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
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
import org.rcredits.R;

public class DashboardActivity extends BaseActivity {
  // Variables
  String my_id, code, userDataResponse, str_update_link;
  String str_tx_id;
  // Controls 
  Button btnCharge, btnPay, btnChangeAgent, btnUndo, btnOtherBalance, btnUpdateApp, btnChangeAccount;
  Map<Button, Intent> map;
  View.OnClickListener myListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.layout_dashboard);
    
    // First keep a bread crumb for the welcome screen within the shared preferences
    putData("finish_on_load", "1");
	
    map = new HashMap<Button, Intent>();

  // The controls and buttons and everything
    btnCharge = buttonSetup(R.id.btnCharge, "charge", "pay_charge", CaptureActivity.class);
    btnPay = buttonSetup(R.id.btnPay, "pay", "pay_charge", CaptureActivity.class);
    btnChangeAgent = buttonSetup(R.id.btnChangeAgent, "change", "change_agent", CaptureActivity.class);
    btnChangeAccount = buttonSetup(R.id.btnChangeAccount, "change", "change_account", CaptureActivity.class);

    btnUndo = (Button) findViewById(R.id.btnUndo);
    btnOtherBalance = (Button) findViewById(R.id.btnShowBalance);
    btnUpdateApp = (Button) findViewById(R.id.btnUpdateApp);

    // Get some API related data from the shared preferences
    my_id = getData("my_id");
    code = getData("loginToken");
  }

  public Button buttonSetup(int id, String action, String activity, Class theClass) {
    Button button = (Button) findViewById(id);
    button.setOnClickListener(new View.OnClickListener() {
      public void onClick(View theButton) {
        putData("finish_on_load", "1"); // keep a bread crumb for the welcome screen
        startActivity(map.get(theButton));
      }
    }); 
    Intent intent = new Intent(DashboardActivity.this, theClass);
    intent.putExtra("target", activity);
    intent.putExtra("action", action);
    map.put(button, intent);
    return button;
  
  }

  @Override
  protected void onStart() {
    Log.e(tag, "Dashboard activity on start");
    // Prepare interface
    prepareInterface();
    super.onStart();
  }

  /**
   * Prepares the interfaces, sets up buttons and events etc
   */
  public void prepareInterface() {
    // Check if there was a transaction done, and if that could be un done

    str_tx_id = getData("tx_id");
    btnUndo.setVisibility(str_tx_id.equals("") ? View.GONE : View.VISIBLE);
    // The tap event for the undo button
    btnUndo.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        // Show the progress bar dialog
        mDialog = new ProgressDialog(DashboardActivity.this);
        mDialog.setMessage("Please wait...");
        mDialog.show();
        // Need to make API call, make it on a new thread
        new Thread(new Runnable() {
          public void run() {
            // **************The actual API call follows****************
            JSONObject apiParams = new JSONObject();
            try {
              apiParams.put("my_id", my_id);
              apiParams.put("code", code);
              apiParams.put("op", "undo");
              apiParams.put("tx_id", str_tx_id);
              apiParams.put("confirmed", "0");
              // Now further create a name value pair to be passed to the http post
              List<NameValuePair> pairs;
              pairs = new ArrayList<NameValuePair>(1);
              pairs.add(new BasicNameValuePair("json", apiParams.toString()));
                
              userDataResponse = makeAPICall(pairs);
              
              // UPDATE UI ON UI THREAD
              runOnUiThread(new Runnable() {
                public void run() {
                  mDialog.dismiss();
                  
                  // Check for success in the last API call
                  String str_success = getValueForJSONKey(userDataResponse, "success");
                  if(str_success.equals("1")) {
                    // Ask from user with the message obtained from the server
                    String str_message = getValueForJSONKey(userDataResponse, "message");
                    askAndFireLastTransactionUndo(str_message);
                  }
                }
              });
                
            } catch(JSONException je) {
              Log.e(tag, je.toString());
            }
          }
        }).start();
      }
    });
    
    // Check if the last transaction customer balance could be seen
    String str_cust_balance = getData("other_balance");
    btnOtherBalance.setVisibility(str_cust_balance.equals("") ? View.GONE : View.VISIBLE);

    // The tap event for the cust balance
    final String local_cust_balance = str_cust_balance;
    btnOtherBalance.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDialog(tag, "The customer's current balance is: " + local_cust_balance);
      }
    });
    
    str_update_link = getData("update_link").toString();
    btnUpdateApp.setVisibility(str_update_link.equals("") ? View.GONE : View.VISIBLE);
    
    // The tap event for the update button
    btnUpdateApp.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(str_update_link))); // simple download, for now
        //Intent intent = new Intent(Intent.ACTION_VIEW ,Uri.parse("market://details?id=com.package.name"));
        //startActivity(intent);          
      }
    });
  }
  
  /**
   * Confirms from user and fires a API call to the server to UNDO the last transaction
   * @param strMessage
   */
  public void askAndFireLastTransactionUndo(final String strMessage) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(tag);
    dialog.setMessage(strMessage);
    dialog.setCancelable(true);
    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // API call to UNDO transaction
        dialog.dismiss();
        
        // Show progress bar
        mDialog = new ProgressDialog(DashboardActivity.this);
        mDialog.setMessage("Please wait...");
        mDialog.show();
        
        // API call again, this time with no confirmation
        JSONObject apiParams = new JSONObject();
        try {
          apiParams.put("my_id", my_id);
          apiParams.put("code", code);
          apiParams.put("op", "undo");
          apiParams.put("tx_id", str_tx_id);
          apiParams.put("confirmed", "1");
          List<NameValuePair> pairs = new ArrayList<NameValuePair>(1);
          pairs.add(new BasicNameValuePair("json", apiParams.toString()));
          
          userDataResponse = makeAPICall(pairs);
          
          // UPDATE UI ON UI THREAD
          runOnUiThread(new Runnable() {
            public void run() {
              mDialog.dismiss();
              
              // Check for success in the last API call
              String str_success = getValueForJSONKey(userDataResponse, "success");
              String str_message = getValueForJSONKey(userDataResponse, "message");
              if(str_success.equals("1")) {
                showDialog(tag, str_message);
                // remove the transactionid and the last customer balance from the system
                putData("tx_id", "");
                btnUndo.setVisibility(View.GONE);
              } else {
                showDialogNegative(tag, str_message);
                // showDialog(tag, str_message);
              }
            }
          });
            
        }
        catch(JSONException je) {
          Log.e(tag, je.toString());
        }
        // ***********************************************************************************
      }
    });
    
    dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        // do nothing and dissmiss this dialog
        dialog.dismiss();
      }
    });

    dialog.show();
  }
}
