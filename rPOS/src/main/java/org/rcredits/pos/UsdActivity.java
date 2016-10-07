package org.rcredits.pos;

import android.os.Bundle;
import android.view.View;

/**
 * Change the description of the transaction.
 * returns the customer's payment method (USD_CASH, USD_CHECK, or USD_CARD_
 */
public class UsdActivity extends Act {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usd);
    }

    public void setUsdType(View v) {
        act.returnIntentString(new Pairs("type", String.valueOf(v.getId())));
    }
}
