/*
 * Copyright (C) 2008 ZXing authors
 * and Common Good Finance (for the modifications)
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

package org.rcredits.pos;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.rcredits.zxing.client.android.CaptureActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Give the user (usually a cashier) a button to press, to scan rCards at POS.
 * When appropriate, also offer a button to show the customer's balance and a button to undo the last transaction.
 * This activity restarts (and cancels all child processes) before each new scan. See Act.restart().
 * @todo: rework deprecated KeyguardManager lines with WindowManager.FLAG_DISMISS_KEYGUARD (see zxing code for tips)
 */
public final class MainActivity extends Act {
    private final Act act = this;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        lock.disableKeyguard();

        findViewById(R.id.undo_last).setVisibility((A.agentCan(A.CAN_REFUND) && !A.lastTx.equals("")) ? View.VISIBLE : View.GONE);
        findViewById(R.id.show_balance).setVisibility(A.balance.equals("") ? View.GONE : View.VISIBLE);
        if (A.agent.equals("")) {
            if (A.failMessage.equals("")) act.mention("Welcome!\n\nPress the SCAN button to sign in with your Company Agent rCard.");
        } else {
            Button signedAs = (Button) findViewById(R.id.signed_as);
            if (!A.agent.equals(A.xagent) && A.failMessage.equals("")) {
                act.mention("Success! You are now signed in.\nReady to scan a customer rCard...");
                A.xagent = A.agent;
            }
            signedAs.setText("You: " + A.agentName);
        }

        if (!A.failMessage.equals("")) {
            act.sayError(A.failMessage, null); // show failure message from previous (failed) activity
            A.failMessage = "";
        }

        if (!A.update.equals("")) {
            act.askOk("Okay to update now? (takes a few seconds)", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    //new UpdateApp().execute(A.update); // download the update in the background
                    act.progress(true);
                    new UpdateApp().execute(A.update);
                }
            });
        }
    }

    /**
     * from ldmuniz at http://stackoverflow.com/questions/4967669/android-install-apk-programmatically
     */
    private class UpdateApp extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setDoOutput(true);
                c.connect();

                String PATH = "/mnt/sdcard/Download/";
                File file = new File(PATH);
                file.mkdirs();
                File outputFile = new File(file, "update.apk");
                if(outputFile.exists()) outputFile.delete();
                FileOutputStream fos = new FileOutputStream(outputFile);
                InputStream is = c.getInputStream();

                byte[] buffer = new byte[1024];
                int len1 = 0;
                while ((len1 = is.read(buffer)) != -1) fos.write(buffer, 0, len1);
                fos.close();
                is.close();

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(new File("/mnt/sdcard/Download/update.apk")), "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
                act.startActivity(intent);
                A.update = ""; // don't redo current update
            } catch (Exception e) {
                Log.e("UpdateAPP", "Update failed: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute() {
            act.progress(false);
        }
    }

    /**
     * Download and install the latest app update, mostly in the background.
     * (untested)
     *//*
    private class downloadUpdate extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) { // must be "String... something"
            String url = urls[0];

            HttpClient http = new DefaultHttpClient();
            HttpGet request = new HttpGet(url);

            try {
                HttpResponse response = http.execute(request);
                InputStream fileStream = response.getEntity().getContent();
                FileOutputStream output = openFileOutput(A.APP_FILENAME, MODE_WORLD_READABLE); // deprecated but doesn't work otherwise
                //boolean hasExternal = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
                //String baseFolder = (hasExternal ? getExternalFilesDir(null) : getFilesDir()).getAbsolutePath();
                File file = new File(Environment.getExternalStorageDirectory(), A.APP_FILENAME);
                FileOutputStream output = new FileOutputStream(file);

                String permission="666";

                try {
                    String command = "chmod " + permission + " " + file.getAbsolutePath();
                    Runtime runtime = Runtime.getRuntime();
                    runtime.exec(command);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte[] buffer = new byte[1024];
                int len;
                while ((len = fileStream.read(buffer)) > 0) output.write(buffer, 0, len);
                fileStream.close();
                output.close();

                Intent intent = new Intent(Intent.ACTION_VIEW);
                //intent.setDataAndType(Uri.fromFile(getFileStreamPath(A.APP_FILENAME)), "application/vnd.android.package-archive");
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
                startActivity(intent);
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                return e.getMessage();
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            }

            A.update = "";
            return A.MSG_DOWNLOAD_SUCCESS;
        }

        @Override
        protected void onPostExecute(String msg) {
            if (msg.equals(A.MSG_DOWNLOAD_SUCCESS)) {
                //A.update = "1";
                act.mention(msg);
            } else act.sayError("Download failed: " + msg, null);
        }
    }*/

    /**
     * Start the Capture activity (when the user presses the SCAN button).
     * @param v
     */
    public void doScan(View v) {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(intent);
    }

    /**
     * Show the last customer's balance (when the user presses the "Balance" button).
     * @param v
     */
    public void doShowBalance(View v) {
        act.sayOk("Customer Balance", A.balance, null);
    }

    /**
     * Undo the last transaction after confirmation (when the user presses the "Undo" button).
     * @param v
     */
    public void doUndo(View v) {
        act.askOk(A.undo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                List<NameValuePair> pairs = A.auPair(null, "op", "undo");
                A.auPair(pairs, "tx", A.lastTx);
                act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
                new Tx().execute(pairs);
            }
        });
    }

    private class Tx extends AsyncTask<List<NameValuePair>, Void, String> {
        @Override
        protected String doInBackground(List<NameValuePair>... pairss) {
            return A.apiGetJson(A.region, pairss[0]);
        }

        @Override
        protected void onPostExecute(String json) {act.afterTx(json);}
    }

    /**
     * Sign the cashier out after confirmation.
     */
    public void doSignOut(View v) {
        if (A.agent.equals("")) {
            act.mention("You are not signed in.");
            return; // not signed in
        }
        act.askOk("Sign out?", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                A.agent = A.agentName = ""; // sign out
                ((Button) findViewById(R.id.signed_as)).setText(R.string.not_signed_in);
            }
        });
    }

    /**
     * Just pretend to scan, when using AVD (since the webcam feature doesn't work). For testing, of course.
     * @param v
     *//*
    public void onFakeScan(View v) {
        if (!A.FAKE_SCAN) return;
        try {
            act.onScan(new rCard("HTTP://NEW.RC2.ME/I/" + (A.agent.equals("") ? "ZZD-" : "ZZA.") + "zot"));
        } catch (Exception e) {}
    }*/
}
