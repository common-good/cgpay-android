/* $Id: $
 */
package com.commongoodfinance.android.pos.scan;

import android.os.Handler;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class Scanner {
    public static interface Callback { void onCapture(String code); }

    private Callback listener;

    /**
     * @param l
     */
    public void start(final Callback l) {
        this.listener = l;

        final Handler hdlr = new Handler();

        // purely temporary: wait 10 sec the signal scan complete
        new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(4000); }
                catch (InterruptedException e) { }
                hdlr.post( new Runnable() { @Override public void run() { capture(); } });
            }
        }).start();
    }

    /**
     *
     */
    public void stop() {
        listener = null;
    }

    void capture() {
        if (null == listener) { return; }
        listener.onCapture("3357842");
    }
}
