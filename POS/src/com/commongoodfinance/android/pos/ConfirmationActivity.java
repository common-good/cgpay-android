/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class ConfirmationActivity extends Activity {

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        Bundle data = i.getExtras();
        String qrcode = (null == data) ? null : data.getString(RCreditPOS.KEY_SCAN_CODE);
        String price = (null == data) ? null : data.getString(RCreditPOS.KEY_PRICE);

        setContentView(R.layout.confirm);

        ((TextView) findViewById(R.id.confirm_price)).setText(qrcode);
        ((TextView) findViewById(R.id.confirm_id)).setText(price);

        ((Button) findViewById(R.id.confirm_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { complete(R.string.confirmed); }

        } );

        ((Button) findViewById(R.id.cancel_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { complete(R.string.cancelled); }

        } );
    }

    void complete(int msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, IdleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
