package org.rcredits.pos;

        import android.content.DialogInterface;
        import android.content.Intent;
        import android.content.res.Configuration;
        import android.os.Bundle;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageButton;
        import android.widget.TextView;

/**
 * Let the user type an amount and say go, sometimes with an option to change the charge description.
 * @intent customer: customer's account ID
 * @intent code: customer's rCard security code
 * @intent description: the current transaction description
 * @intent goods: "1" if the transaction is for real goods and services, else "0"
 * @intent photoId: the customer's photo ID number, if appropriate
 * Charges, "USD in", "USD out", and "refund" are all treated similarly.
 */
public class TxActivity extends Act {
    private final int MAX_DIGITS = 6; // maximum number of digits allowed
    private final int PRECOMMA_DIGITS = 5; // maximum number of digits before we need a comma
    private final int CHANGE_DESC = 1; // change-description activity
    private final int GET_USD_TYPE = 2; // usd-type activity
    private final String USD_CHECK_FEE = "$3";
    private final String USD_CARD_FEE = "3%";

    private String customer; // qid of current customer
    private String code; // current customer's rCard security code
    private String description; // transaction description
    private String amount; // the transaction amount
    private String goods; // is this a purchase/refund of real goods & services (or an exchange for cash)

    /**
     * Show the appropriate options.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        customer = A.getIntentString(this.getIntent(), "customer");
        code = A.getIntentString(this.getIntent(), "code");
        description = A.getIntentString(this.getIntent(), "description");
        goods = A.getIntentString(this.getIntent(), "goods");
        photoId = A.getIntentString(this.getIntent(), "photoId");
        amount = "0.00";
        setLayout();
    }

    /**
     * Do what needs doing on creation and orientation change.
     */
    private void setLayout() {
        setContentView(R.layout.activity_tx);
        Button desc = (Button) findViewById(R.id.description);
        ImageButton changeDesc = (ImageButton) findViewById(R.id.change_description);

        if (description.equals(A.DESC_USD_IN) || description.equals(A.DESC_USD_OUT)) {
            desc.setText(description);
            changeDesc.setVisibility(View.GONE);
        } else if (description.equals(A.DESC_REFUND)) {
            desc.setText(description);
            changeDesc.setVisibility(View.GONE);
        } else { // charging
            if (A.descriptions.size() < 2) {
                if (description.equals("")) description = "charge"; // don't let it be blank
                changeDesc.setVisibility(View.GONE);
            }
            //desc.setText(A.ucFirst(description.toLowerCase()));
            desc.setText(description.toLowerCase());
        }

        ((TextView) findViewById(R.id.amount)).setText("$" + amount);
    }

    /**
     * Adjust the layout according to the device's orientation.
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) { // cgf (this whole method)
        super.onConfigurationChanged(newConfig);
        setLayout();
    }

    /**
     * Handle a number pad button press.
     * @param button: which button was pressed (c = clear, b = backspace)
     */
    public void onCalcClick(View button) {
        TextView text = (TextView) findViewById(R.id.amount);
        amount = text.getText().toString().replaceAll("[,\\.\\$]", "");
        String c = (String) button.getContentDescription();
        if (c.equals("c")) {
            amount = "000";
        } else if (c.equals("b")) {
            amount = amount.substring(0, amount.length() - 1);
            if (amount.length() < 3) amount = "0" + amount;
        } else if (amount.length() < MAX_DIGITS) { // don't let the number get too big
            amount += c;
        } else {
            act.mention("You can have only up to " + MAX_DIGITS + " digits. Press clear (c) or backspace (\u25C0).");
        }

        int len = amount.length();
        amount = amount.substring(0, len - 2) + "." + amount.substring(len - 2);
        if (len > 3 && amount.substring(0, 1).equals("0")) amount = amount.substring(1);
        if (len < 3) amount = "0" + amount;
        if (len > PRECOMMA_DIGITS) amount = amount.substring(0, len - PRECOMMA_DIGITS) + "," + amount.substring(len - PRECOMMA_DIGITS);
        text.setText("$" + amount);
    }

    /**
     * Launch the ChangeDescription activity (when the change button is pressed).
     * @param button
     */
    public void onChangeDescriptionClick(View button) {
        act.start(DescriptionActivity.class, CHANGE_DESC, "description", description);
    }

    /**
     * For USD in, find out what kind of USD payment customer prefers.
     */
    public void getUsdType() {act.start(UsdActivity.class, GET_USD_TYPE);}

    /**
     * Handle a change of description
     * @param requestCode
     * @param resultCode
     * @param data: the new description
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHANGE_DESC) {
            if(resultCode == RESULT_OK) {
                description = data.getStringExtra("description");
                Button desc = (Button) findViewById(R.id.description);
                //desc.setText(A.ucFirst(description.toLowerCase()));
                desc.setText(description);
            } else if (resultCode == RESULT_CANCELED) {} // do nothing if no result
        } else if (requestCode == GET_USD_TYPE) {
            if(resultCode == RESULT_OK) {
                final int usdType = Integer.valueOf(data.getStringExtra("type"));
                if (usdType != R.id.cash) {
                    String fee = usdType == R.id.check ? (USD_CHECK_FEE + " check fee.") : (USD_CARD_FEE + " card fee.");
                    act.askOk("The customer will be charged a " + fee, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            finishTx(usdType);
                        }
                    }); // otherwise do nothing (stay in this Tx Activity)
                } else finishTx(usdType);
            } else if (resultCode == RESULT_CANCELED) {} // do nothing if no result
        }
    }

    /**
     * Request a transaction on the rCredits server, with the amount entered by the user.
     * @param v
     */
    public void onGoClick(View v) {
//        String amount = A.nn(((TextView) findViewById(R.id.amount)).getText()).substring(1); // no "$"
        if (amount.equals("0.00")) {
            sayError("You must enter an amount.", null);
            return;
        }
        if (description.equals(A.DESC_USD_IN)) getUsdType(); else finishTx(null);
    }

    /**
     * Complete the transaction, as appropriate.
     * @param usdType: type cash-in payment type (null if not applicable)
     */
    public void finishTx(Integer usdType) {
        if (description.equals(A.DESC_REFUND) || description.equals(A.DESC_USD_IN)) amount = "-" + amount; // a negative tx
        String goods = (description.equals(A.DESC_USD_IN) || description.equals(A.DESC_USD_OUT)) ? "0" : "1";
        String desc = description; // copy, because we might come here again if tx fails
        if (desc.equals(A.DESC_USD_IN)) {
            desc += usdType == R.id.cash ? " (cash)" : (usdType == R.id.check ? " (check)" : " (card)");
        }

        Pairs pairs = new Pairs("op", "charge");
        pairs.add("member", customer);
        pairs.add("code", code);
        pairs.add("amount", amount.replace(",", ""));
        pairs.add("goods", goods);
        pairs.add("description", desc);
        if (A.db.similarTx(pairs)) {
            act.sayFail("You already just completed a transaction for that amount with this member.");
            return;
        }
// Can't add photoId to pairs until pairs stored in db and retrieved (see Act.Tx)
        act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()

        try {
            A.executeAsyncTask(new Act.Tx(), A.db.storeTx(pairs));
        } catch (Db.NoRoom e) {
            act.sayFail(R.string.no_room);
            return;
        }
    }
}
