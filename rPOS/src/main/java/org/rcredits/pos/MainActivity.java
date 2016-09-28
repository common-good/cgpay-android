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
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.rcredits.zxing.client.android.CaptureActivity;
import org.rcredits.zxing.client.android.PreferencesActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
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
    private final int CAPTURE = 1; // the capture activity
    private final static int TX_OLD_INTERVAL = 15 * 60; // number of seconds Undo and Balance buttons last
    final String QRS = "," +
            "OLD Bob/NEW/AAB-/WeHlioM5JZv1O9G," +
            "Bob/6VM/H010/WeHlioM5JZv1O9G," +
            "OLD Susan/NEW/ABB./ZzhWMCq0zcBowqw," +
            "Susan/6VM/G0R/ZzhWMCq0zcBowqw," +
//            "OLD Curt/NEW/AAK./NyCBBlUF1qWNZ2k," +
//            "Curt/6VM/G0A/NyCBBlUF1qWNZ2k," +
            "OLD Helga's/NEW/AAD-/utbYceW3KLLCcaw," +
            "Helga's/6VM/H0G0/utbYceW3KLLCcaw," +
//            "OLD Cathy Cashier/NEW/ABJ-/ME04nW44DHzxVDg," +
            "Cathy Cashier/6VM/H011/ME04nW44DHzxVDg," +
//            "OLD P Honey/NEW/ABB./WrongCode4Susan," +
            "P Honey/6VM/G0R/WrongCode4Susan";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
//        getWindow().requestFeature(Window.FEATURE_RIGHT_ICON);
        setContentView(R.layout.activity_main);

        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        lock.disableKeyguard();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        act.menu = menu;
/*        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
*/
        if (A.fakeScan && false) {
//            SubMenu scanMenu = menu.addSubMenu(Menu.NONE, R.id.action_scan, Menu.NONE, "Scan");
//        SubMenu scanMenu = menu.findItem(R.id.action_scan).getSubMenu();
//            SubMenu.clear();

            String[] qrs = QRS.split(",");
            String[] parts;
            for (int i = 1; i < qrs.length; i++) {
                parts = qrs[i].split("/");
                menu.add(0, R.id.action_settings + i, Menu.NONE, parts[0]);
            }
        }
        return true;
    }
/*
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_ACTION_BAR && menu != null) {
            if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
                try {
                    Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                    m.setAccessible(true);
                    m.invoke(menu, true);
                } catch(NoSuchMethodException e) {
                    A.log(e);
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }
*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.action_settings:
                act.start(PrefsActivity.class, 0);
                return true;
            default:
                String[] qrs = QRS.split(",");
                String[] part = qrs[item.getItemId() - R.id.action_settings].split("/");
                String qr = String.format("HTTP://%s.RC4.ME/%s%s", part[1], part[2], part[3]);
                act.start(CustomerActivity.class, 0, "qr", qr);
                return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

//        if (menu != null) menu.setGroupVisible(1, A.signedIn() || A.fakeScan);
//        if (menu != null) menu.findItem(R.id.action_settings).setVisible(A.signedIn() || A.fakeScan);

        setLayout();

        Calendar now = Calendar.getInstance(); // catch dead clocks before trying to contact server
        if (now.get(Calendar.YEAR) < 2014) A.failMessage = t(R.string.clock_off);

        if (A.failMessage != null) {
            act.sayError(A.failMessage, null); // show failure message from previous (failed) activity
            A.failMessage = null;
        }
        if (A.serverMessage != null && A.serverMessage.equals("!update")) {
            act.askOk("Okay to update now? (it takes a few seconds)", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    A.db = null; // tell periodic thread to stop
                    Intent getApp = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.rcredits.pos"));
                    act.finish();
                    startActivity(getApp);
                    System.exit(0);
//                    act.progress(true);
//                    new UpdateApp().execute(A.update); // download the update in the background
                }
            });
            A.serverMessage = null;
        } else if (A.serverMessage != null) {
            act.sayOk("Note", A.serverMessage, null); // show a message the server wants the cashier to see
            A.serverMessage = null;
        }

        A.db.q("DELETE from log WHERE time<?", new String[]{"" + A.daysAgo(7)});
        A.db.q("DELETE from txs WHERE created<?", new String[]{"" + A.daysAgo(180)});
        String where = String.format("%s<>%s AND lastTx<?", DbHelper.AGT_FLAG, A.TX_AGENT); // don't delete agents
        A.db.q("DELETE from members WHERE " + where, new String[]{"" + A.daysAgo(180)});

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
        A.log(0);
        Button signedAs = (Button) findViewById(R.id.signed_as);
        TextView welcome = (TextView) findViewById(R.id.welcome);
        TextView version = (TextView) findViewById(R.id.version);
        if (version != null) version.setText("v. " + A.versionName);

        boolean showUndo = (A.can(A.CAN_UNDO) && A.undo != null && !A.selfhelping());
        boolean showBalance = (A.balance != null && !A.selfhelping());
        findViewById(R.id.undo_last).setVisibility(showUndo ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.show_balance).setVisibility(showBalance ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.test).setVisibility(A.testing ? View.VISIBLE : View.INVISIBLE);
//        if (A.demo) ((TextView) findViewById(R.id.test)).setText("DEMO");
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
        A.log(9);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (A.signedIn()) act.start(PrefsActivity.class, 0);
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

//    public void doScan(View v) {act.start(CaptureActivity.class, CAPTURE);} // user pressed the SCAN button
    public void doScan(View v) { // user pressed the SCAN button
        A.log(0);
        final boolean old = false;
        final String BOB = old ? "HTTP://NEW.RC4.ME/AAB-WeHlioM5JZv1O9G" : "HTTP://6VM.RC4.ME/H010WeHlioM5JZv1O9G";
        final String SUSAN = old ? "HTTP://NEW.RC4.ME/ABB.ZzhWMCq0zcBowqw" : "HTTP://6VM.RC4.ME/G0RZzhWMCq0zcBowqw";
        final String CURT = old ? "HTTP://NEW.RC4.ME/AAK.NyCBBlUF1qWNZ2k" : "HTTP://6VM.RC4.ME/G0ANyCBBlUF1qWNZ2k";
        final String HELGAS = old ? "HTTP://NEW.RC4.ME/AAQ-utbYceW3KLLCcaw" : "HTTP://6VM.RC4.ME/H0G0utbYceW3KLLCcaw";
        final String CATHY = old ? "HTTP://NEW.RC4.ME/ABJ-ME04nW44DHzxVDg" : "HTTP://6VM.RC4.ME/H011ME04nW44DHzxVDg";
        final String BAD = old ? "HTTP://NEW.RC4.ME/ABB.WrongCode4Susan" : "HTTP://6VM.RC4.ME/G0RWrongCode4Susan";

        if (A.fakeScan) { // debugging
            act.askYesNo("Scan BOB? (else Susan)",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        act.start(CustomerActivity.class, 0, "qr", BOB);
                    }
                },
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        act.start(CustomerActivity.class, 0, "qr", SUSAN);
                    }
                }
            );
        } else act.start(CaptureActivity.class, CAPTURE);
        A.log(9);
    }
    // NOT YET USED public void doPrefs(View v) {act.start(PrefsActivity.class, 0);} // user pressed the gear button

    /**
     * Handle result callbacks from activities launched (especially to capture a QR code)
     * @param requestCode
     * @param resultCode
     * @param data: data returned from the activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        A.log(0);
        if (requestCode == CAPTURE) {
            if(resultCode == RESULT_OK) {
//                act.sayOk("Scan succeeded.", data.getStringExtra("qr"), null);
                act.start(CustomerActivity.class, 0, "qr", data.getStringExtra("qr"));
            } else if (resultCode == RESULT_CANCELED) { // dunno how this happens, if ever
                A.log("scan canceled. Weird.");
//                 // do nothing
            } // else act.sayOk("Scan failed.", "", null); // sayFail seems to fail here (?)
        }
    }

    /**
     * Announce the customer's current balance, when the "Show Balance" button is pressed.
     * @param v
     */
    public void doShowBalance(View v) {
//        if (!oldTx())
        act.sayOk("Customer Balance", A.balance, null);
    }

    /**
     * Complain if the latest transaction is too old to risk remembering (cashiers mishandle old transactions).
     * @return <transaction is too old>
     *//*
    private boolean oldTx() {
        String created0 = A.db.getField("created", "txs", A.lastTxRow);
        int created = created0 == null ? 0 : Integer.parseInt(created0);
        if (created > A.now() - TX_OLD_INTERVAL) return false;
        A.undo = A.balance = null;
        act.sayFail(getString(R.string.old_tx));
        return true;
    }*/

    /**
     * Undo the last transaction after confirmation (when the user presses the "Undo" button).
     * @param v
     */
    public void doUndo(View v) {
//        if (!oldTx())
        A.log(0);
        act.askOk(A.undo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
                A.db.changeStatus(A.lastTxRow, A.TX_CANCEL, null);
                A.log("about to undo");
//                A.executeAsyncTask(new Act.Tx(), A.lastTxRow);
                new Thread(new Tx(A.lastTxRow, false, new handleTxResult())).start();
            }
        });
    }

    /**
     * Sign the cashier out after confirmation.
     */
    public void doSignOut(View v) {
        A.log(0);
        if (A.signedIn()) act.askOk("Sign out?", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                A.signOut();
                setLayout();
            }
        }); // (too unexpected) else doScan(v);
    }
}
