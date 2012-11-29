package org.rcredits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import org.rcredits.R;
import static org.rcredits.BaseActivity.MSG_CANT_CONNECT;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;

public class WelcomeActivity extends BaseActivity {
  
  // Controls etc
  Button btn_scan, btn_login;
  EditText txt_userid, txt_password;
  // Layout allignment
  LinearLayout ll_main;
  
  
  // Timer related variables
  Timer t;
  TimerTask tt;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.layout_splash);
  
    // Launch a timer which would last for 2 seconds
    // We start scheduler and mark that the scheduler is running
    t = new Timer();
    tt = new TimerTask() {
      @Override
      public void run() {
        try {Thread.sleep(2000);} // sleep for 2 seconds
        catch(InterruptedException ie) { }
         // Call the method which decides what to do next
        prepareInterface();
        t.cancel();
      }
    };
    t.schedule(tt,0, 1 * 1000);
  }
  
  @Override
  protected void onStart() {
    Log.e(tag, "Welcome activity on start");
    // Check if I need to finish myself
    if(getData("finish_on_load").equals("1")) {
      // Reset variable and finish
      putData("finish_on_load", "");
      finish();
    }
    
    super.onStart();
  }
  
  /**
   * Prepare the interface to be used 
   */
  public void prepareInterface() {
    // As the original caller of this method was a separate thread, we need to run this on UI thread
    runOnUiThread(new Runnable() {
      public void run() {
        if(getData("loginToken").equals("")) { // first run
          // The interface for the first run
          setContentView(R.layout.layout_login);
          // Register button etc and login
          btn_login = (Button) findViewById(R.id.btnSubmit);
          // The text boxes
          txt_userid = (EditText) findViewById(R.id.txtUserID);
          txt_password = (EditText) findViewById(R.id.txtPassword);
          
          btn_login.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
              // The onclick event for the button click
              // And the userid and password texts
              String strUserID = txt_userid.getText().toString();
              String strPassword = txt_password.getText().toString();
              
              if(strUserID.equals(""))
              {
                showDialogNegative(tag, "Please enter your user ID");
                txt_userid.requestFocus();
              } else {
                if(strPassword.equals("")) {
                  showDialogNegative(tag, "Please enter your password");
                  txt_password.requestFocus();
                } else {
                  // All set, we make the http call to the server, a lengthy process of call
                  // First let's create the JSON
                  JSONObject apiParamsJson = new JSONObject();
                  // Add name value pairs in it
                  try {
                    apiParamsJson.put("my_id", strUserID.toUpperCase());
                    apiParamsJson.put("password", strPassword);
                    apiParamsJson.put("op", "first_time");
                    // Log.i(tag, apiParamsJson.toString());
                    // Now further create a name value pair to be passed to the http post
                    List<NameValuePair> nameValuePairs;
                    nameValuePairs = new ArrayList<NameValuePair>(1);  

                    // Adding parameters to send to the HTTP server.
                    nameValuePairs.add(new BasicNameValuePair("json", apiParamsJson.toString()));
                    
                    String loginResp = makeAPICall(nameValuePairs);
                    Log.e(tag, "Response is : " + loginResp);
                    if (loginResp.equals("")) return;
        
                    // check what is the status of the call
                    String str_success = getValueForJSONKey(loginResp, "success");
                    
                    // Also check if any update for the app is available
                    String str_update_link = getValueForJSONKey(loginResp, "update_link");
                    putData("update_link", str_update_link);
                    
                    // Depending on success
                    if(!str_success.equals("1")) {
                      String str_message = getValueForJSONKey(loginResp, "message");
                      showDialogNegative(tag, str_message);
                    } else {
                      // Success, store up the token and go the main switch board
                      String str_code = getValueForJSONKey(loginResp, "code");
                      // String str_id = strUserID.toUpperCase();
                      String str_id = getValueForJSONKey(loginResp, "my_id");
                      Log.e(tag, "THE NEW MY_ID IS -> " + str_id);
                      String str_account_name = getValueForJSONKey(loginResp, "account_name");
                      // Save settings
                      putData("loginToken", str_code);
                      putData("my_id", str_id);  // Default owner id or account id
                      putData("account_name", str_account_name);
                      
                      
                      // Lets go over to the main switch board
                      // Perform action on click
                          Intent intent = new Intent(WelcomeActivity.this, DashboardActivity.class);
                            startActivity(intent);
                    }
                  } catch(JSONException je) {
                    Log.e(tag, je.toString());
                  }
                  
                }
              }
            }
          });
          // Adjust the layout heights etc depending on the screen
          adjustLayoutAccordingToScreen();
          
        } else {
          // The interface for nth successive run, again authenticate from server
          // A new thread approach to get the initial authentication done
              new Thread(new Runnable() {
                public void run() {
                  // Our start load goes here
                      initialAuthentication();
                }
              }).start();
        }
      }
    });
  }  
  
  /**
   * Does the initial authentication call to the server on a new thread
   */
  public void initialAuthentication() {
    // Let's retrieve the stored up code and the id first
    String str_code = getData("loginToken");
    
    // Log.i(tag, "MY ID -> " + str_my_id + " | " + "CODE -> " + str_code);
    JSONObject apiParamsJson = new JSONObject();
    try {
//      apiParamsJson.put("my_id", str_my_id.toUpperCase());  // Though this is not mandatory from 4th Nov, 2012, but omitting it server returns "BAD ID FORMAT"
      apiParamsJson.put("code", str_code);
      apiParamsJson.put("op", "startup");
      
      // Now further create a name value pair to be passed to the http post
      List<NameValuePair> nameValuePairs;
        nameValuePairs = new ArrayList<NameValuePair>(1);  

        // Adding parameters to send to the HTTP server.
        nameValuePairs.add(new BasicNameValuePair("json", apiParamsJson.toString()));
        // Make the server call
        String loginResp = makeAPICall(nameValuePairs);
        if (loginResp.equals("")) return;

        // Update UI with response
        updateAuthenticationResult(loginResp);
    } catch(JSONException je) {
      Log.e(tag, je.toString());
    }
  }
  
  /**
   * Runs a UI thread and updates the interface about the result of the authentication
   * @param jsonResponse
   */
  public void updateAuthenticationResult(final String jsonResponse) {
    // Lauch a fork on the UI thread and update
    runOnUiThread(new Runnable() {
      public void run() {
        // Check if any update for the app is available
          String str_update_link = getValueForJSONKey(jsonResponse, "update_link");
          putData("update_link", str_update_link);
        String str_success = getValueForJSONKey(jsonResponse, "success");
        if(str_success.equals("0")) {
          // Login failed on startup, get message and display to user
          String message = getValueForJSONKey(jsonResponse, "message");
          // Also for the time being lets set the loginToken to be null ('')
          putData("loginToken", "");
          // showDialog(tag, message);
          showDialogOKFinishesNegative(tag, message);
        } else {
          // Login success, let's go to dashboard
          Intent intent = new Intent(WelcomeActivity.this, DashboardActivity.class);
          startActivity(intent);
        }
      }
    });
    
  }
  
  
  
  
  
  /**
   * Adjusts layout depending on screen
   */
  protected void adjustLayoutAccordingToScreen()
  {
    Log.e(tag, "called");
    // Get the height / width of the screen
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int win_height = displaymetrics.heightPixels;
        final int win_width = displaymetrics.widthPixels;
        // height of the linear layout
        
      ll_main = (LinearLayout) findViewById(R.id.linearLayout1);
      ll_main.post(new Runnable(){
          public void run(){
              final int ll_height = ll_main.getHeight();
              final int ll_width = ll_main.getWidth();
        // Update UI on a UI thread
        runOnUiThread(new Runnable() {
          public void run() {
            int diff_in_heights = win_height - ll_height;
            int diff_in_width = win_width - ll_width;
            // So top margin should be half of that
            int top_margin = diff_in_heights / 2;
            // Left margin 
            int left_margin = diff_in_width / 2;
            
            RelativeLayout.LayoutParams params1 = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params1.topMargin = top_margin;
            params1.leftMargin = left_margin;
            params1.width = ll_width;
            ll_main.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
          
            // params1.width = 240;
            ll_main.setLayoutParams(params1);
          }
        });
          }
      });  
  }
  
}