/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.barcodescanner;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.barcodescanner.camera.CameraManager;
import com.android.barcodescanner.result.ResultHandler;
import com.android.barcodescanner.result.ResultHandlerFactory;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.scanner.ChangeAccountActivity;
import com.scanner.ChangeAgentActivity;
import com.scanner.PayChargeActivity;
import com.scanner.R;
import com.scanner.ScanResultActivity;
import com.scanner.Util;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements
		SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();
	private static final String INVALID_CODE = "Invalid QR Code!";
	private static final Set<ResultMetadataType> DISPLAYABLE_METADATA_TYPES;

	static {
		DISPLAYABLE_METADATA_TYPES = new HashSet<ResultMetadataType>(5);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ISSUE_NUMBER);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.SUGGESTED_PRICE);
		DISPLAYABLE_METADATA_TYPES
				.add(ResultMetadataType.ERROR_CORRECTION_LEVEL);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.POSSIBLE_COUNTRY);
	}

	private enum Source {
		NATIVE_APP_INTENT, PRODUCT_SEARCH_LINK, ZXING_LINK, NONE
	}

	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	// private TextView statusView;
	// private View resultView;
	private Result lastResult;
	private boolean hasSurface;
	private Source source;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	// private HistoryManager historyManager;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	
	//rCredit specific variables
	String target, action_flag;

	ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window window = getWindow();
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);

		CameraManager.init(getApplication());
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		// statusView = (TextView) findViewById(R.id.status_view);
		
		//Get the passed data from dashboard
		target = (String) getIntent().getExtras().get("target");
		action_flag = (String) getIntent().getExtras().get("action_flag");
		
		handler = null;
		lastResult = null;
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		resetStatusView();

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.viewfinder_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		initScanService();
	}

	public void initScanService() {
		Intent intent = getIntent();
		String action = intent == null ? null : intent.getAction();
		if (intent != null && action != null) {
			if (action.equals(Intents.Scan.ACTION)) {
				// Scan the formats the intent requested, and return the result
				// to the calling activity.
				source = Source.NATIVE_APP_INTENT;
				decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
				if (intent.hasExtra(Intents.Scan.WIDTH)
						&& intent.hasExtra(Intents.Scan.HEIGHT)) {
					int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
					int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
					if (width > 0 && height > 0) {
						CameraManager.get().setManualFramingRect(width, height);
					}
				}

				// pplante - intents can now enable flash if they want..
				try {
					if (intent.hasExtra(Intents.Scan.FLASH_MODE)) {
						CameraManager.get().enableFlash(
								intent.getStringExtra(Intents.Scan.FLASH_MODE));
					}
				} catch (IOException e) {
					Log.e("flash", e.toString());
				}
				Log.d("flash",
						"enabled = "
								+ intent.getStringExtra(Intents.Scan.FLASH_MODE));

			} else {
				// Scan all formats and handle the results ourselves (launched
				// from Home).
				source = Source.NONE;
				decodeFormats = null;
			}
			characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);
		} else {
			source = Source.NONE;
			decodeFormats = null;
			characterSet = null;
		}

		beepManager.updatePrefs();

		inactivityTimer.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		CameraManager.get().closeDriver();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			onBackClick();
		} else if (keyCode == KeyEvent.KEYCODE_FOCUS
				|| keyCode == KeyEvent.KEYCODE_CAMERA) {
			// Handle these events so they don't launch the Camera app
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public boolean onBackClick() {
		if (source == Source.NATIVE_APP_INTENT) {
			setResult(RESULT_CANCELED);
			finish();
		} else if ((source == Source.NONE || source == Source.ZXING_LINK)
				&& lastResult != null) {
			resetStatusView();
			if (handler != null) {
				handler.sendEmptyMessage(R.id.restart_preview);
			}
		}
		return true;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode) {
		inactivityTimer.onActivity();
		lastResult = rawResult;
		ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(
				this, rawResult);
		beepManager.playBeepSoundAndVibrate();
		switch (source) {
		case NONE:
			handleDecodeInternally(rawResult, resultHandler, barcode);
			break;
		}
	}

	// Put up our own UI for how to handle the decoded contents.
	private void handleDecodeInternally(Result rawResult,
			ResultHandler resultHandler, Bitmap barcode) {
		String data = resultHandler.getDisplayContents().toString();
		System.out.println("data: " + data);
		if (!data.equals("")) {
			Intent intent;
			if(target.equals("pay_charge_activity"))
			{
				//Let's go to the PayChargeActivitys
				intent = new Intent(this, PayChargeActivity.class);
				intent.putExtra("action_flag", action_flag);
			}
			else if(target.equals("change_agent")){
				//We change the Agent
				intent = new Intent(this, ChangeAgentActivity.class);
				intent.putExtra("action_flag", action_flag);
			}
			else if(target.equals("change_account")){
				//We change the Agent
				intent = new Intent(this, ChangeAccountActivity.class);
				intent.putExtra("action_flag", action_flag);
			}
			else
			{
				intent = new Intent(this, ScanResultActivity.class);
			}
			intent.putExtra("data", data);
			startActivity(intent);
			finish();
		} else {
			Util.showCallBackMessage(CaptureActivity.this, INVALID_CODE,
					CaptureActivity.this);
		}
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats,
						characterSet);
			}
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializating camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	private void resetStatusView() {
		// resultView.setVisibility(View.GONE);
		// statusView.setText(R.string.msg_default_status);
		// statusView.setVisibility(View.VISIBLE);
		viewfinderView.setVisibility(View.VISIBLE);
		lastResult = null;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}
}
