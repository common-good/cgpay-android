/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class PricingActivity extends Activity {

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pricing);

        ((Button) findViewById(R.id.enter_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { confirmSale(); }

        } );
    }

    void confirmSale() {
        Toast.makeText(this, R.string.confirmed, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, IdleActivity.class));
    }
}
