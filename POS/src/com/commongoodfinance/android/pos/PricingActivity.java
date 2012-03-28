/* $Id: $
 */
package com.commongoodfinance.android.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.commongoodfinance.android.pos.net.RCreditsService;
import com.commongoodfinance.android.pos.net.RCreditsServiceError;
import com.commongoodfinance.android.pos.net.RCreditsServiceException;


/**
 * IdleActivity
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class PricingActivity extends Activity {
    private final RCreditsService service = new RCreditsService(this);
    private String customer;

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        Bundle data = i.getExtras();
        customer = (null == data) ? null : data.getString(RCreditPOS.KEY_SCAN_CODE);

        setContentView(R.layout.pricing);

        ((ImageView) findViewById(R.id.customer_pic))
            .setImageDrawable(service.getCustomerImage(customer));

        ((ViewGroup) findViewById(R.id.pricing_view)).requestLayout();

        ((Button) findViewById(R.id.cancel_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { cancel(); }
            } );

        ((Button) findViewById(R.id.enter_button)).setOnClickListener(
            new Button.OnClickListener() {
                @Override public void onClick(View v) { doTransaction(); }
            } );

        ((TextView) findViewById(R.id.price_text)).setOnEditorActionListener(
            new TextView.OnEditorActionListener() {
                @Override public boolean onEditorAction(TextView v, int act, KeyEvent evt) {
                    if (null == evt) { return false; }
                    doTransaction();
                    return true;
                } });
    }

    void cancel() {
        getAmount();
        nextPage(R.string.xact_cancelled);
    }

    void doTransaction() {
        String sVal = getAmount();
        try {
            RCreditsService.Transaction xact
                = service.postTransaction(customer, verifyAmount(sVal));
            nextPage(
                "RECEIVED: " + xact.amount
                + " from " + xact.customer
                + ". Transaction ID: " + xact.id);
        }
        catch (NumberFormatException e) {
            postToast(getResources().getString(R.string.bad_amount) + sVal, Toast.LENGTH_SHORT);
        }
        catch (RCreditsServiceError e) {
            nextPage(R.string.xact_failed);
        }
        catch (RCreditsServiceException e) {
            nextPage(getResources().getString(R.string.xact_rejected) + e.getReason());
        }
    }

    private String getAmount() {
        TextView tv = (TextView) findViewById(R.id.price_text);
        String amount = tv.getText().toString();
        tv.setText("");
        return amount;
    }

    private int verifyAmount(String sVal) throws NumberFormatException {
        double val = Double.parseDouble(sVal);

        val = val * 100;
        if (0.1 <= Math.abs(val - Math.round(val))) {
            throw new NumberFormatException("Too many digits right of decimal point");
        }

        return (int) val;
    }

    private void nextPage(int message) {
        nextPage(getResources().getString(message));
    }

    private void nextPage(String message) {
        postToast(message, Toast.LENGTH_LONG);
        Intent intent = new Intent(this, IdleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void postToast(String message, int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(
            R.layout.toast,
            (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = (TextView) layout.findViewById(R.id.toast_text);
        text.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT, 5, 110);
        toast.setDuration(duration);
        toast.setView(layout);

        toast.show();
    }
}
