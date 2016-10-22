/*
 * Offer the user a button to use to scan a customer's rCard. And some menu options.
 */

package org.rcredits.pos;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.rcredits.zxing.client.android.CaptureActivity;

import java.lang.reflect.Method;
import java.util.Calendar;

/**
 * Give the user (usually a cashier) a button to press, to scan rCards at POS.
 * When appropriate, also offer a button to show the customer's balance and a button to undo the last transaction.
 * This activity restarts before each new scan. See Act.restart().
 * @todo: rework deprecated KeyguardManager lines with WindowManager.FLAG_DISMISS_KEYGUARD (see zxing code for tips)
 */
public final class MainActivity extends Act {
    private final int CAPTURE = 1; // the capture activity
    private final static int TX_OLD_INTERVAL = 15 * 60; // number of seconds Undo and Balance buttons last
    private final String QRS = "," +
//            "OLD Curt/NEW/AAK./NyCBBlUF1qWNZ2k," +
            "Curt/6VM/G0A/NyCBBlUF1qWNZ2k," +
            "Bob short/?," +
            "Susan short/?," +
//            "OLD Helga's/NEW/AAD-/utbYceW3KLLCcaw," +
            "Helga's/6VM/H0G0/utbYceW3KLLCcaw," +
//            "OLD Cathy Cashier/NEW/ABJ-/ME04nW44DHzxVDg," +
            "Cathy Cashier/6VM/H011/ME04nW44DHzxVDg," +
//            "OLD P Honey/NEW/ABB./WrongCode4Susan," +
            "P Honey/6VM/G0R/WrongCode4Susan," +
            "OLD Bob/NEW/AAB-/WeHlioM5JZv1O9G," +
            "Bob/6VM/H010/WeHlioM5JZv1O9G," +
            "OLD Susan/NEW/ABB./ZzhWMCq0zcBowqw," +
            "Susan/6VM/G0R/ZzhWMCq0zcBowqw";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (A.hasMenu) getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_main);

        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        lock.disableKeyguard();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!A.hasMenu) return false;

        act.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        if (A.fakeScan) {
            String[] qrs = QRS.split(",");
            String[] parts;
            for (int i = 1; i < qrs.length; i++) {
                parts = qrs[i].split("/");
                menu.add(0, R.id.action_signout+ i, 200, parts[0]);
            }
        }
        menu.setGroupVisible(R.id.group_all, false);
        return true;
    }

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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        int id = item.getItemId();
        A.log("menu id=" + id);

        switch (id) {
            case R.id.action_settings:
                act.start(PrefsActivity.class, 0); return true;
            case R.id.action_qr:
                act.start(ShowQrActivity.class, 0); return true;
            case R.id.action_account:
                return act.browseTo(A.b.signinPath());
            case R.id.action_promo:
                return act.browseTo(A.PROMO_SITE);
            case R.id.action_signout:
                return act.askSignout();
            default:
                String[] qrs = QRS.split(",");
                String[] part = qrs[item.getItemId() - R.id.action_signout].split("/");
                String qr = part[0].equals("Bob short")
                ? "H6VM010WeHlioM5JZv1O9G.B"
                : (part[0].equals("Susan short")
                ? "G6VM0RZzhWMCq0zcBowqw.C"
                : String.format("HTTP://%s.RC4.ME/%s%s", part[1], part[2], part[3]));
                act.start(CustomerActivity.class, 0, "qr", qr);
                return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (menu != null) {
            menu.setGroupVisible(R.id.group_all, A.signedIn);
            menu.findItem(R.id.action_signout).setVisible(A.signedIn && !A.proSe());
            menu.findItem(R.id.action_qr).setVisible(A.proSe() || A.can(A.CAN_BUY));
        }

        setLayout();

        Calendar now = Calendar.getInstance(); // catch dead clocks before trying to contact server
        if (now.get(Calendar.YEAR) < 2014) A.failMessage = t(R.string.clock_off);

        if (A.failMessage != null) {
            act.sayError(A.failMessage, null); // show failure message from previous (failed) activity
            A.failMessage = null;
        }
        if (A.nn(A.sysMessage).equals("!update")) {
            act.askYesNo(A.t(R.string.update_needed), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    updateNow();
                }
            });
            A.sysMessage = null;
        } else if (A.sysMessage != null) {
            act.sayOk("Note", A.sysMessage, null); // show a message the system wants the cashier to see
            A.sysMessage = null;
        }

        A.b.db.q("DELETE from log WHERE time<?", new String[]{"" + A.daysAgo(7)});
        A.b.db.q("DELETE from txs WHERE created<?", new String[]{"" + A.daysAgo(180)});
        String where = String.format("%s<>%s AND lastTx<?", DbSetup.AGT_FLAG, A.TX_AGENT); // don't delete agents
        A.b.db.q("DELETE from members WHERE " + where, new String[]{"" + A.daysAgo(180)});

    }

    private void updateNow() {
        A.stop = true; // tell periodic thread to stop
        Intent getApp = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.rcredits.pos"));
        if (act.activityExists(getApp)) {
            startActivity(getApp);
            System.exit(0);
            act.finish();
        } else {
            act.sayOk("Get Update", t(R.string.get_update), null);
        }
//                    act.progress(true);
//                    new UpdateApp().execute(A.update); // download the update in the background
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
        if (A.proSe()) findViewById(R.id.show_balance).setBackgroundResource(R.drawable.show_my_balance);
        findViewById(R.id.show_balance).setVisibility(showBalance ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.settings).setVisibility(A.can(A.CAN_MANAGE) ? View.INVISIBLE : View.INVISIBLE); // not used yet
        setMode();

        if (A.empty(A.agent)) {
            welcome.setText(R.string.no_company);
            signedAs.setText(R.string.not_signed_in);
        } else {
            welcome.setText((showUndo || showBalance) ? "" : "Ready for customers...");
// annoying            if (A.empty(A.failMessage) && A.empty(A.balance) && A.empty(A.undo)) act.mention(R.string.mention_menu);
            signedAs.setText(((A.signedIn && !A.proSe()) ? "Signed in as: " : "") + A.agentName);
        }
        A.log(9);
    }

    @Override
    public void setWifi(boolean wifi) {
        super.setWifi(wifi);
        setMode();
    }

    private void setMode() {
        TextView modeText = (TextView) findViewById(R.id.test);
        boolean clickable = false;
        boolean visible = true;
        if (A.selfhelping()) {
            modeText.setText(A.wifiOff ? "SELF-SERVE\nWifi OFF" : "SELF-SERVE");
        } else if (A.wifiOff) {
            modeText.setText("Wifi OFF");
            clickable = true;
        } else if (A.b.test) {
            modeText.setText("TEST");
            clickable = true;
        } else visible = false;
        modeText.setClickable(clickable);
        modeText.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (A.signedIn) act.start(PrefsActivity.class, 0);
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
                        act.start(CustomerActivity.class, 0, "qr", BOB);
                    }
                },
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        act.start(CustomerActivity.class, 0, "qr", SUSAN);
                    }
                }
            );
        } else act.start(CaptureActivity.class, CAPTURE);
        A.log(9);
    }

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
                act.start(CustomerActivity.class, 0, "qr", data.getStringExtra("qr"));
            } else { // if (resultCode == RESULT_CANCELED) { // dunno how this happens, if ever
                A.log("scan failed or canceled. Weird."); // do nothing
            }
        }
    }

    /**
     * Announce the customer's current balance, when the "Show Balance" button is pressed.
     * @param v
     */
    public void doShowBalance(View v) {
        act.sayOk(A.proSe() ? "My Balance" : "Customer Balance", A.balance, null);
    }

    /**
     * Undo the last transaction after confirmation (when the user presses the "Undo" button).
     * @param v
     */
    public void doUndo(View v) {
        A.log(0);
        act.askOk(A.undo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
                A.b.db.changeStatus(A.undoRow, A.TX_CANCEL, null);
                A.log("about to undo");
                new Thread(new Tx(A.undoRow, false, new handleTxResult())).start();
            }
        });
    }
}
