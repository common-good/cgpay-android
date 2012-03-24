/* $Id: $
 */
package com.commongoodfinance.android.pos.camera;

import java.io.IOException;

import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

import com.commongoodfinance.android.pos.RCreditPOS;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class CameraManager {
    private Camera camera;

    /**
     * @param surfaceHolder
     */
    public void start(SurfaceHolder surfaceHolder) {
        if (null == camera) { camera = Camera.open(); }
        try { camera.setPreviewDisplay(surfaceHolder); }
        catch (IOException e) { Log.e(RCreditPOS.LOG_TAG, "failed to init camera"); }
        camera.startPreview();
    }

    /**
     *
     */
    public void stop() {
        camera.stopPreview();
        camera.release();
        camera = null;
    }
}
