package org.rcredits;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import org.rcredits.R;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

public class PayChargeActivity extends BaseActivity {
  // Variables
  String action, my_id, code, data_from_scanner, account_id;
  String userDataResponse;
  boolean is_goods = true;
  byte[] superImage;
  // Controls
  ImageView ivMemberImage;
  TextView txt_name, txt_location, txt_howmuch;
  RadioButton radio_1, radio_2;
  Button btn_submit;
  EditText edt_amount, edt_desc;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.charge_pay_layout);
    
    // Try and get the flag passed to this activity
    Intent thisIntent = getIntent();
    action = thisIntent.getStringExtra("action");
    data_from_scanner = thisIntent.getStringExtra("data");
    // Get the account id from the scanned code
    account_id = getIDFromScannedData(data_from_scanner);
    
    /*
    // TEST TEST TEST
    action = "charge";
    account_id = "NEW.AAF";  // AAF is Jane, AAE is Bea Two
    */
    
    // Get the agent related id's from the shared preferences
    my_id = getData("my_id").toString();
    code = getData("loginToken").toString();
    
    prepareInterface();
    
  }
  
  private void prepareInterface()
  {
    // Check for which the screen is used
    if(action.equals("charge")) { // Charge
      Log.e(tag, "FOR CHARGING");
    } else { // pay
      Log.e(tag, "FOR Paying");
    }
    
    // Init up controls
    ivMemberImage = (ImageView) findViewById(R.id.imgMemberImage);
    txt_name = (TextView) findViewById(R.id.txtMemberName);
    txt_howmuch = (TextView) findViewById(R.id.textHowMuch);
    txt_location = (TextView) findViewById(R.id.txtLocation);
    radio_1 = (RadioButton) findViewById(R.id.radioButton1);
    radio_2 = (RadioButton) findViewById(R.id.radioButton2);
    btn_submit = (Button) findViewById(R.id.btnSubmit2);
    edt_amount = (EditText) findViewById(R.id.edtAmount);
    edt_desc = (EditText) findViewById(R.id.edtReason);
    
    // Events of the radio buttons
    radio_1.setChecked(true);
    radio_1.setOnClickListener(new myRadioClickedListener());
    radio_2.setOnClickListener(new myRadioClickedListener());
    
    
    btn_submit.setOnClickListener(new View.OnClickListener() {
      
      public void onClick(View v) {
        final String str_amount = edt_amount.getText().toString();
        final String str_desc = edt_desc.getText().toString();
        if (str_amount.equals("")) {
          showDialog(tag, "Please Enter Amount");
          edt_amount.requestFocus();
        } else {
          // Show the progress bar dialog
          mDialog = new ProgressDialog(PayChargeActivity.this);
          mDialog.setMessage("Please wait...");
          mDialog.show();
          
          // Call on different thread
          new Thread(new Runnable() {
            public void run() {
              // ***********we need to make API call**********
              JSONObject apiParamsJsonTransact = new JSONObject();
              try {
                apiParamsJsonTransact.put("my_id", my_id);
                apiParamsJsonTransact.put("code", code);
                apiParamsJsonTransact.put("op", "transact");
                apiParamsJsonTransact.put("account_id", account_id);
                apiParamsJsonTransact.put("type", action);
                apiParamsJsonTransact.put("amount", str_amount);
                apiParamsJsonTransact.put("goods", is_goods);
                apiParamsJsonTransact.put("purpose", str_desc);

                List<NameValuePair> pairs = new ArrayList<NameValuePair>(1);
                pairs.add(new BasicNameValuePair("json", apiParamsJsonTransact.toString()));
                  
                userDataResponse = makeAPICall(pairs);
                
                // Update UI on a UI thread
                runOnUiThread(new Runnable() {
                  public void run() {
                    // Lets see what the server has returned
                    String str_success = getValueForJSONKey(userDataResponse, "success");
                    String str_message = getValueForJSONKey(userDataResponse, "message");
                    if(str_success.equals("1")) {
                      // Transaction successful, keep all important data in shared preferences
                      String str_tx_id = getValueForJSONKey(userDataResponse, "tx_id");
                      String str_my_balance = getValueForJSONKey(userDataResponse, "my_balance");
                      String str_other_balance = getValueForJSONKey(userDataResponse, "other_balance");
                      putData("tx_id", str_tx_id);
                      putData("my_balance", str_my_balance);
                      putData("other_balance", str_other_balance);
                      // Dismmiss the progress bar
                      mDialog.dismiss();
                      // Then show a notification to the user, tapping OK should go back to dashboard
                      showDialogOKFinishes(tag, str_message);
                    } else {
                      // Transaction failed
                      Log.e(tag, str_message);
                      // Show the error message from the server and dismiss off the dialog
                      mDialog.dismiss();
                      showDialogOKFinishesNegative(tag, str_message);
                    }
                  }
                });
              } catch(JSONException je) {
                Log.e(tag, je.toString());
              }
              // *********************************************
            }
          }).start();
          
        }
      }
    });
    
    
    // Show the progress bar dialog
    mDialog = new ProgressDialog(PayChargeActivity.this);
    mDialog.setMessage("Please wait...");
    mDialog.show();
    
    // Server call on a separate thread to get details of the agent
    new Thread(new Runnable() {
          public void run() {
            // Some inner variables
            final byte[] imageData;
            
                // ************Let's try with the image first*************
            JSONObject apiParamsJson = new JSONObject();
        // Add name value pairs in it
        try {
          apiParamsJson.put("my_id", my_id);
          apiParamsJson.put("code", code);
          apiParamsJson.put("op", "photo");
          apiParamsJson.put("account_id", account_id);
          // Now further create a name value pair to be passed to the http post
          List<NameValuePair> nameValuePairs;
            nameValuePairs = new ArrayList<NameValuePair>(1);
            // Adding parameters to send to the HTTP server.
            nameValuePairs.add(new BasicNameValuePair("json", apiParamsJson.toString()));
            
          
            imageData = makeAPICallForImage(nameValuePairs);
            Log.e(tag, "Response is --> " + imageData);
            
            superImage = imageData;
            
        } catch(JSONException je) {
          Log.e(tag, je.toString());
        }
        
        // ***********And immediately let's try to fetch the details of the user from API********
        JSONObject apiParamsJsonUserDetails = new JSONObject();
        // Add name value pairs in it
        try {
          apiParamsJsonUserDetails.put("my_id", my_id);
          apiParamsJsonUserDetails.put("code", code);
          apiParamsJsonUserDetails.put("op", "identify");
          apiParamsJsonUserDetails.put("account_id", account_id);
          // Now further create a name value pair to be passed to the http post
          List<NameValuePair> nameValuePairsUsersDetails;
          nameValuePairsUsersDetails = new ArrayList<NameValuePair>(1);
            // Adding parameters to send to the HTTP server.
            nameValuePairsUsersDetails.add(new BasicNameValuePair("json", apiParamsJsonUserDetails.toString()));
            
            userDataResponse = makeAPICall(nameValuePairsUsersDetails);
        } catch(JSONException je) {
          Log.e(tag, je.toString());
        }
        
        // Launch a fork on the UI thread and update interface
        runOnUiThread(new Runnable() {
          public void run() {
            Bitmap bmobj = BitmapFactory.decodeByteArray(superImage, 0, superImage.length);
            ivMemberImage.setImageBitmap(bmobj);
            // Dismiss dialog
            mDialog.dismiss();
            
            // Now lets set the user details to the interface
            // check what is the status of the call
            String str_success = getValueForJSONKey(userDataResponse, "success");
            if(str_success.equals("0")) {
              String str_message = getValueForJSONKey(userDataResponse, "message");
              // show dialog regarding the error and finish this activity
              showDialogOKFinishesNegative(tag, str_message);
            } else {
              // We have data, we can set those to the interface
              // set name
              txt_name.setText(getValueForJSONKey(userDataResponse, "full_name"));
              txt_location.setText(getValueForJSONKey(userDataResponse, "location"));
              if(action.equals("charge")) {
                txt_howmuch.setText("Charge How Much?");
              } else {
                txt_howmuch.setText("Pay How Much?");
              }
            }
          }
        });
      }
    }).start();
  }
  
  class myRadioClickedListener implements OnClickListener
  {
    public void onClick(View v) {
      // TODO Auto-generated method stub
      if(v.getId() == R.id.radioButton1)
      {
        if(radio_1.isChecked())
        {
          radio_2.setChecked(false);
          is_goods = true;
        }
      }
      else
      {
        if(radio_2.isChecked())
        {
          radio_1.setChecked(false);
          is_goods = false;
        }
      }
    }
  }
}


