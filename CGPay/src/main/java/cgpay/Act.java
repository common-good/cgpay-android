package earth.commongood.cgpay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/**
 * An Activity class extension that includes some utility methods.
 * The convention in this project is to define act = this in each Activity that extends Act, then
 *   call these methods using "act." rather than "this.".
 */
public class Act extends Activity {
    protected final Act act = this;
    protected ProgressDialog progressDlg; // for standard progress spinner
    private AlertDialog alertDialog;
    protected String photoId; // got customer's photo ID number (null or "1", used in TxActivity and Act.Tx)
    private String name;
    private CountDownTimer timer = null; // timeout timer
    private boolean onTop = false; // activity is visible
    public Menu menu = null;
    private final String YES_OR_NO = "Yes or No";
    private final static int TIMEOUT = 2; // number of minutes before activity times out
    private final static int PERMISSIONS_OK = 1;
    private String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act.name = act.getLocalClassName();
        A.log(act.name + " onCreate");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (!granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        || !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        || !granted(Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(act, permissions, PERMISSIONS_OK);
        }
    }

    public boolean granted(String permission) {
        return (ContextCompat.checkSelfPermission(act, permission) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (results.length > 0) for (int r: results) if (r != PackageManager.PERMISSION_GRANTED) {
            sayFail("CGPay cannot run without these permissions.");
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        A.log(act.name + " onPause");
        onTop = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        A.log(act.name + " onResume");
        if (A.goingHome) {
            if (isMain()) {
                A.goingHome = false;
            } else {goHome(A.sysMessage); return;}
        }

        onTop = true;

        if (timer == null) timer = new CountDownTimer(TIMEOUT * 60 * 1000, 1000) {

            public void onTick(long millisUntilFinished) {}

            public void onFinish() {
                timer.cancel();
                if (onTop) {
                    A.balance = null; // don't show these too long
                    A.noUndo();
                    if (isMain()) {
                        onResume();
                    } else {
                        A.log("Common Good activity timed out.");
                        goHome(t(R.string.timed_out));
                    }
                }
            }
        };
        timer.start();
    }

    /**
     * End all processes in this thread and go back to scanning cards.
     * (note that getApplicationContext() and startActivity() are activity methods)
     */
    public void goHome(String msg) {
        A.log(0);
        if (msg != null) A.sysMessage = msg;
        progress(false);
        if (timer != null) timer.cancel();

        if (A.goingHome = !isMain()) act.finish(); else onResume();
    }
    public void goHome() {goHome(null);}

    /**
     * Terminate activity on Back Button press.
     */
    @Override
    public void onBackPressed() {
        if (act.name.equals("TxActivity") && A.selfhelping()) act.goHome(); else act.finish();
    }

    public void goBack(View v) {onBackPressed();}

    public String t(int resource) {return A.t(resource);}

    private boolean isMain() {return act.name.equals("MainActivity");}

    /**
     * Show a short message briefly (2 secs) -- or longer (3.5 secs) for longer messages
     * @param message: the message to show
     */
    public void mention(String message) {
//        int duration = (message.length() < 30) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(act, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    public void mention(int res) {mention(t(res));}

    private class doNothing implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
        }
    }

    /**
     * Show a customized dialog with an OK button, and handle it.
     * @param title: large, very short title to show to the right of the icon
     * @param icon: conceptual graphic to show at the top of the message
     * @param message: what to say
     * @param ok: callback to do when user presses OK (null if nothing)
     * @param cancelable: <include a Cancel button>
     * @param cancel: callback to do when user presses Cancel (null if nothing)
     */
    private void say(String title, int icon, String message, DialogInterface.OnClickListener ok,
                     boolean cancelable, DialogInterface.OnClickListener cancel) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(act);

        if (ok == null) ok = new doNothing();
        if (cancel == null) cancel = new doNothing();
        A.log("SAY " + title + ": " + message);

        alertDialogBuilder
                .setTitle(title)
                .setMessage(message)
                .setIcon(icon)
                .setCancelable(cancelable)
                .setPositiveButton(title.equals(YES_OR_NO) ? "Yes" : "OK", ok);
        if (cancelable) alertDialogBuilder.setNegativeButton(title.equals(YES_OR_NO) ? "No" : "Cancel", cancel);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void say(String title, int icon, String message, DialogInterface.OnClickListener ok) {
        say(title, icon, message, ok, false, null);
    }

    public void sayFail(String message) {
        A.log("FAIL: " + message);
        A.failMessage = message;
        goHome();
    }

    public void sayFail(int res) {sayFail(t(res));}

    public void sayError(String message,  DialogInterface.OnClickListener ok) {
        say("Error", R.drawable.alert_red, message, ok);
    }
    public void sayError(int res,  DialogInterface.OnClickListener ok) {
        sayError(t(res), ok);}

    public void sayOk(String title, String message,  DialogInterface.OnClickListener ok) {
        say(title, R.drawable.smile_icon, message, ok);
    }
    public void sayOk(String title, int res,  DialogInterface.OnClickListener ok) {sayOk(title, t(res), ok);
    }

    public void askOk(String title, String message, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener cancel) {
        say(title, R.drawable.question_icon, message, ok, true, cancel);
    }
    public void askOk(String message,  DialogInterface.OnClickListener ok) {askOk("Confirm", message, ok, null);}
    public void askOk(int res, DialogInterface.OnClickListener ok) {
        askOk(t(res), ok);
    }

    public void askYesNo(String message, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener cancel) {
        askOk(YES_OR_NO, message, ok, cancel);
    }
    public void askYesNo(String message, DialogInterface.OnClickListener ok) {askYesNo(message, ok, null);}

    /**
     * Show a standard "in progress" message.
     * @param go: true to start, false to stop
     */
    public void progress(boolean go) {
	    if (progressing()) try {
            progressDlg.dismiss(); // use .cancel() instead?
	    } catch (final IllegalArgumentException e) {
		    A.log("dismissing - activity vanished"); // ignore (work around Android bug)
		}
		
        if (go) {
            progressDlg = ProgressDialog.show(act, "In Progress", "Contacting server...");
        } else progressDlg = null;
    }
    
    public boolean progressing() {
        try {
            return (progressDlg != null && progressDlg.isShowing());
        } catch (final IllegalArgumentException e) {return false;} // work around Android bug
    }

    public boolean browseTo(String path) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(path));
        startActivity(browserIntent);
        return true;
    }

    public boolean askSignout() {
        if (A.signedIn) act.askOk("Sign out?", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                A.signOut();
                goHome();
            }
        });
        return true;
    }

    /**
     * Launch a new activity.
     * @param cls: the activity to launch
     * @param id: identifier for the activity when it returns a result (0 if no value returned)
     * @param k: name of value to pass to the activity (null if none)
     * @param v: value to pass to the activity (null if none)
     */
    public void start(Class cls, int id, String k, String v) {
        A.log("starting " + cls.getName());
        Intent intent = new Intent(act, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        if (k != null) A.putIntentString(intent, k, v);
        if (id == 0) startActivity(intent); else startActivityForResult(intent, id);
    }

    public void start(Class cls, int id) {act.start(cls, id, null, null);}

    public boolean activityExists(Intent intent) {
        return (intent.resolveActivityInfo(getPackageManager(), 0) != null);
    }

    /**
     * Return a string to the parent activity.
     * @param pairs: key/value pairs to return
     */
    public void returnIntentString(Pairs pairs) {
        Intent returnIntent = new Intent();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            for (Map.Entry<String, Object> item : pairs.list.valueSet()) {
                String k = item.getKey(); // getting key
                returnIntent.putExtra(k, pairs.get(k));
            }
        } else for (String k : pairs.list.keySet()) {
            returnIntent.putExtra(k, pairs.get(k));
        }
        act.setResult(Activity.RESULT_OK, returnIntent);
        act.finish();
    }

    /**
     * Turn wifi on or off for this app (useful for saving time when out of range for a long time).
     */
    public void setWifi(boolean wifi) {
        A.wifiOff = !wifi;
        int msg = wifi ? (A.connected() ? R.string.wifi_on : R.string.wifi_unavailable) : R.string.wifi_off;
        act.sayOk("Wifi", msg, null);
    }

    public void showTables(View v) {if (A.b.test) sayOk("Records", A.b.db.showCust() + "\n\n" + A.b.db.showTxs(), null);}

    /**
     * Provide wifi toggle shortcuts when testing (clicking +id/test, +id/customer_place, or +id/amount).
     * @param v
     */
    public void setWifi(View v) {if (A.b.test) setWifi(A.wifiOff);}

    public class handleTxResult implements Tx.ResultHandler {
        public handleTxResult() {}

        @Override
        public boolean done(int action0, String msg0) {
            final int action = action0;
            final String msg = msg0;

            act.runOnUiThread(new Runnable() {
                public void run() {
                    A.log(0);
                    act.progress(false);
                    if (action == Tx.OK) {
                        act.sayOk("Success!", msg, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                           act.goHome();
                            }
                        });
                    } else if (action == Tx.FAIL || act.isMain()) {
                        act.sayFail(msg);
                    } else act.sayError(msg, null);
                }
            });
            return true;
        }
    }

    /**
     * Set a text field to the appropriate text.
     * @param id: field identifier
     * @param v: the value to set
     * @return: the field
     */
    public TextView setView(int id, String v) {
        TextView view = (TextView) findViewById(id);
        view.setText(v);
        return view;
    }
}
