/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.commongoodfinance.android.pos.scan.CameraManager;
import com.commongoodfinance.android.pos.scan.Scanner;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class ScannerActivity  extends Activity
    implements SurfaceHolder.Callback, Scanner.Callback
{
    private CameraManager camera;
    private Scanner scanner;
    private volatile boolean hasSurface;

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanner = new Scanner();
        camera = new CameraManager();
        hasSurface = false;

        setContentView(R.layout.scan);
    }

    /**
     * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(RCreditPOS.LOG_TAG, "surface is null");
            return;
        }

        if (!hasSurface) {
            hasSurface = true;
            camera.start(holder);
        }
    }

    /**
     * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) { hasSurface = false; }

    /**
     * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
     */
    @Override
    public void surfaceChanged(SurfaceHolder a0, int a1, int a2, int ign3) { }

    /**
     * @see com.commongoodfinance.android.pos.scan.Scanner.Callback#onCapture(java.lang.String)
     */
    @Override
    public void onCapture(String code) {
        if ((null == code) || (0 >= code.length())) { return; }

        Intent i = new Intent(this, PricingActivity.class);
        i.putExtra(RCreditPOS.KEY_SCAN_CODE, code);
        startActivity(i);
    }

    /**
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();

        if (hasSurface) { camera.start(surfaceHolder); }
        else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        scanner.start(this);
    }

    /**
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();

        camera.stop();
        scanner.stop();

        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
          }
    }
}
