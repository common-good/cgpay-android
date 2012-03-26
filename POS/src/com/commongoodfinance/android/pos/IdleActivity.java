/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class IdleActivity extends Activity {

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.idle);

        findViewById(R.id.idle_view).setOnClickListener(
            new View.OnClickListener() {
                @Override public void onClick(View v) { startScanner(); }
        } );
    }

    void startScanner() {
        startActivity(new Intent(this, ScannerActivity.class));
    }
}
