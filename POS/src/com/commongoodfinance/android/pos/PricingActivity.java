/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.commongoodfinance.android.pos.net.RCreditsService;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class PricingActivity extends Activity {
    private final RCreditsService service = new RCreditsService(this);
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

        ((ImageView) findViewById(R.id.customer_pic)).setImageDrawable(service.getCustomerImage(qrcode));

        ((ViewGroup) findViewById(R.id.pricing_view)).requestLayout();

        ((Button) findViewById(R.id.cancel_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { complete(false); }
            } );

        ((Button) findViewById(R.id.enter_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { complete(true); }
            } );

        ((TextView) findViewById(R.id.price_text)).setOnEditorActionListener(
            new TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(TextView v, int act, KeyEvent evt) {
                    if (null == evt) { return false; }
                    complete(true);
                    return true;
                } });
    }

    void complete(boolean succeeded) {
        int msg = R.string.cancelled;
        TextView tv = (TextView) findViewById(R.id.price_text);

        if (succeeded) {
            if (service.commit(qrcode, tv.getText().toString())) {
                msg = R.string.confirmed;
            }
        }

        tv.setText("");

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, IdleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
