package org.rcredits;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;


public class MainActivity extends Activity {
    private static final String TAG = "SCAN";

    private final String SCHEME_HTTP = "http";
    private final String SCHEME_HTTPS = "https";

    private static final int REQ_SCAN = 7;

    @Override
    protected void onResume() {
        super.onResume();
        startScan();
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent intent) {
        if (REQ_SCAN != reqCode) {
            super.onActivityResult(reqCode, resCode, intent);
            return;
        }

        switch (resCode) {
            case RESULT_OK:
                if (getString(R.string.format_qr_code)
                    .equals(intent.getStringExtra(getString(R.string.key_scan_format))))
                {
                    startBrowser(intent.getStringExtra(getString(R.string.key_scan_contents)));
                }
                break;
            default:
                Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startBrowser(String uriString) {
        Uri uri = parseUri(uriString);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.setData(uri);

        try { startActivity(intent); }
        catch (ActivityNotFoundException e) {
            StringBuilder buf = new StringBuilder(getString(R.string.bad_uri));
            buf.append(uri.toString());
            Toast.makeText(this, buf.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void startScan() {
        String zxPkg = getString(R.string.zxing_pkg);

        Intent intent = new Intent();
        intent.setPackage(zxPkg);
        intent.setAction(zxPkg + getString(R.string.action_scan));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(getString(R.string.mode_scan), R.string.mode_qr_code);

        startActivityForResult(intent, REQ_SCAN);
    }

    private Uri parseUri(String uriString) {
        Uri uri = Uri.parse(uriString);

        String scheme = uri.getScheme();
        if (SCHEME_HTTP.equals(scheme.toLowerCase()) && !scheme.equals(SCHEME_HTTP)) {
            uri = uri.buildUpon().scheme(SCHEME_HTTP).build();
        }
        else if (SCHEME_HTTPS.equals(scheme.toLowerCase()) && !scheme.equals(SCHEME_HTTPS)) {
            uri = uri.buildUpon().scheme(SCHEME_HTTPS).build();
        }

        return uri;
    }
}
