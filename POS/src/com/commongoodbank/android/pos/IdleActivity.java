/* $Id: $
 */
package com.commongoodbank.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class IdleActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.idle);

        ((Button) findViewById(R.id.scan)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { startScanner(); }

        } );
    }

    void startScanner() {
        startActivity(new Intent(this, ScannerActivity.class));
    }
}