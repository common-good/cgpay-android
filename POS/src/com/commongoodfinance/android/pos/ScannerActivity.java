/* $Id: $
 */
package com.commongoodfinance.android.pos;

import java.io.IOException;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class ScannerActivity  extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private boolean hasSurface;

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan);

        hasSurface = false;
    }

    /**
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();

        camera = Camera.open();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();

        if (hasSurface) { initCamera(surfaceHolder); }
        else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    /**
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scan_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
          }

        camera.stopPreview();
        camera.release();
    }

    /**
     * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) { Log.e(RCreditPOS.TAG, "surface is null"); }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
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

    private void initCamera(SurfaceHolder surfaceHolder) {
        try { camera.setPreviewDisplay(surfaceHolder); }
        catch (IOException e) { Log.e(RCreditPOS.TAG, "failed to init camera"); }
        camera.startPreview();
    }
}
