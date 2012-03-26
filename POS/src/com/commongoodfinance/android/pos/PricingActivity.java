/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class PricingActivity extends Activity {
    private String qrcode;

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        Bundle data = i.getExtras();
        qrcode = (null == data) ? null : data.getString(RCreditPOS.KEY_SCAN_CODE);

        setContentView(R.layout.pricing);

        ((Button) findViewById(R.id.enter_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { setPrice(); }

        } );

        ((TextView) findViewById(R.id.price_text)).setOnEditorActionListener(
            new TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(TextView v, int act, KeyEvent evt) {
                    if (null == evt) { return false; }
                    setPrice();
                    return true;
                } });
    }

    void setPrice() {
        TextView tv = (TextView) findViewById(R.id.price_text);

        String price = tv.getText().toString();
        tv.setText("");

        Intent i = new Intent(this, ConfirmationActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        i.putExtra(RCreditPOS.KEY_SCAN_CODE, qrcode);
        i.putExtra(RCreditPOS.KEY_PRICE, price);

        startActivity(i);
    }
}
