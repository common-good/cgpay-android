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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.rcredits.zxing.client.android.CaptureActivity;
import org.rcredits.zxing.client.android.PreferencesActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

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

        setLayout();

        Calendar now = Calendar.getInstance(); // catch dead clocks before trying to contact server
        if (now.get(Calendar.YEAR) < 2014) A.failMessage = A.t(R.string.clock_off);

        if (A.failMessage != null) {
            act.sayError(A.failMessage, null); // show failure message from previous (failed) activity
            A.failMessage = null;
        }
        if (A.serverMessage != null) {
            act.sayOk("Note", A.serverMessage, null); // show failure message from previous (failed) activity
            A.serverMessage = null;
        }

        /*else if (A.update != null && !A.testing) {
            act.askOk("Okay to update now? (takes a few seconds)", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    act.progress(true);
                    new UpdateApp().execute(A.update); // download the update in the background
                }
            });
        } */

        // } else if ... mention(R.string.connect_soon); // if this business is often offline, ask for ID

        /*
        final TextView debug = (TextView) findViewById(R.id.debug);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(10000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                debug.setText(A.debugString);
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        t.start();
        */
    }

    /**
     * Do what needs doing upon startup or after signing out.
     */
    private void setLayout() {
        Button signedAs = (Button) findViewById(R.id.signed_as);
        TextView welcome = (TextView) findViewById(R.id.welcome);
        TextView version = (TextView) findViewById(R.id.version);
        if (version != null) version.setText("v. " + ((A.testing && !A.demo) ? A.versionCode : A.versionName));

        A.useDefaults(); // get agent, etc., if necessary

        boolean showUndo = (A.can(A.CAN_UNDO) && A.undo != null);
        boolean showBalance = (A.balance != null);
        findViewById(R.id.undo_last).setVisibility(showUndo ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.show_balance).setVisibility(showBalance ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.test).setVisibility(A.testing ? View.VISIBLE : View.INVISIBLE);
        if (A.demo) ((TextView) findViewById(R.id.test)).setText("DEMO");
        findViewById(R.id.settings).setVisibility(A.can(A.CAN_MANAGE) ? View.INVISIBLE : View.INVISIBLE); // not used yet

        if (A.agent == null) {
            welcome.setText(R.string.no_company);
            signedAs.setText(R.string.not_signed_in);
        } else {
            welcome.setText((showUndo || showBalance) ? "" : "Ready for customers...");
            if (!A.agent.equals(A.xagent) && A.failMessage == null) {
                act.mention("Success! You are now signed in. (To sign out, tap your name.)");
                A.xagent = A.agent;
            }
            signedAs.setText(A.agentName);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            act.start(PrefsActivity.class);
            return true;
        } else return super.onKeyUp(keyCode, event);
    }

    /**
     * NO LONGER USED
     * from ldmuniz at http://stackoverflow.com/questions/4967669/android-install-apk-programmatically
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
                String FILENAME = "update.apk";
                File file = new File(PATH);
                file.mkdirs();
                File outputFile = new File(file, FILENAME);
                if(outputFile.exists()) outputFile.delete();
                FileOutputStream fos = new FileOutputStream(outputFile);
                InputStream is = c.getInputStream();

                byte[] buffer = new byte[1024];
                int len1 = 0;
                while ((len1 = is.read(buffer)) != -1) fos.write(buffer, 0, len1);
                fos.close();
                is.close();

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(new File(PATH + FILENAME)), "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
                act.startActivity(intent);
                A.update = null; // don't redo current update
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
*/

    public void doScan(View v) {act.start(CaptureActivity.class);} // user pressed the SCAN button
    // NOT YET USED public void doPrefs(View v) {act.start(PrefsActivity.class);} // user pressed the gear button

    public void doShowBalance(View v) {
        act.sayOk("Customer Balance", A.balance, null); // user pressed the Balance button
    }

    /**
     * Undo the last transaction after confirmation (when the user presses the "Undo" button).
     * @param v
     */
    public void doUndo(View v) {
        act.askOk(A.undo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
                A.db.changeStatus(A.lastTxRow, A.TX_CANCEL, null);
//                new Act.Tx().execute(A.lastTxRow);
                A.executeAsyncTask(new Act.Tx(), A.lastTxRow);
                /*
                Pairs pairs = new Pairs("op", "undo");
                pairs.add("txid", A.lastTx);
                act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
                new Tx().execute(pairs); // act.Tx (but android does not recognize that syntax)
                */
            }
        });
    }

    /**
     * Sign the cashier out after confirmation.
     */
    public void doSignOut(View v) {
        //if (A.agent.equals(A.dftAgent)) return; // already signed out
        if (A.agent != null) act.askOk("Sign out?", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                A.signOut();
                setLayout();
            }
        });
    }

    /**
     * Just pretend to scan, when using AVD (since the webcam feature doesn't work). For testing, of course.
     * @param v
     *//*
    public void onFakeScan(View v) {
        try {
            act.onScan(new rCard("HTTP://NEW.RC2.ME/I/" + (A.agent == null ? "ZZD-" : "ZZA.") + "zot"));
        } catch (Exception e) {}
    }*/
}
