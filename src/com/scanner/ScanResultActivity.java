	package com.scanner;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

public class ScanResultActivity extends BaseActivity {

	private String data = null;
	
	//Controls
	TextView txtName, txtLocation;
	ImageView imgMember;
	RadioButton rb1, rb2;
	Spinner spnChargeMode;
	
	//Local members
	String[] id_parts = null;
	String[] data_parts = null;
	String[] name_parts = null;
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.scan_result_layout);
		//Get data from scanner
		data = (String) getIntent().getExtras().get("data");
		Log.i(tag, "SCANNED -> " + data);
		
		//Init up the controls
		txtName = (TextView) findViewById(R.id.txtMemberName);
		txtLocation = (TextView) findViewById(R.id.txtLocation);
		imgMember = (ImageView) findViewById(R.id.imgMemberImage);
		rb1 = (RadioButton) findViewById(R.id.radioButton1);
		rb2 = (RadioButton) findViewById(R.id.radioButton2);
		
		//Spinner charge mode
		spnChargeMode = (Spinner) findViewById(R.id.cmbAmount);
		ArrayAdapter sortByColArr = new ArrayAdapter(this,android.R.layout.simple_spinner_item, transactionMode);
		spnChargeMode.setAdapter(sortByColArr);
		
		
		populteInterface();
	}

	

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			//Back hit, we need to see what we can do here
		}
		return super.onKeyDown(keyCode, event);
	}
	
	/**
	 * this is the method which on temporary basis displayes the layout
	 */
	public void populteInterface()
	{
		//The data is typically a JSON of the following format
		// {"my_id":"NEW.AAC","code":"the unique permanent code"}
		//So lets try and get the data 
		String str_my_id, str_code;
		str_my_id = getValueForJSONKey(data, "my_id");
		str_code = getValueForJSONKey(data, "code");
		
		Log.e(tag, str_my_id + " " + str_code);
	}
	
	/**
	 * Basically show a message that the format of the QR code was not right
	 */
	public void showWrongFormatQRMessage()
	{
		setContentView(R.layout.wrong_qr_message);
		Button btnBack = (Button)findViewById(R.id.btnBack);
		btnBack.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				// TODO Auto-generated method stub
				finish();
			}
		});
	}

}
