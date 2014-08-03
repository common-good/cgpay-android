package org.rcredits.pos;

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
 * @intent description: the current transaction description
 * @intent goods: "1" if the transaction is for real goods and services, else "0"
 * Charges, "USD in", "USD out", and "refund" are all treated similarly.
 */
public class TxActivity extends Act {
    private final Act act = this;
    private final int maxDigits = 6; // maximum number of digits allowed
    private final int preCommaDigits = 5; // maximum number of digits before we need a comma
    private String customer; // qid of current customer
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
        description = A.getIntentString(this.getIntent(), "description");
        goods = A.getIntentString(this.getIntent(), "goods");
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

        if (amount != null) ((TextView) findViewById(R.id.amount)).setText("$" + amount);
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
     * Go back to Customer activity.
     */
    @Override
    public void onBackPressed() {act.finish();}
    public void onTxBack(View v) {onBackPressed();}

    /**
     * Handle a calculator button press.
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
        } else if (amount.length() < maxDigits) { // don't let the number get too big
            amount += c;
        } else {
            act.mention("You can have only up to " + maxDigits + " digits. Press clear (c) or backspace (\u25C0).");
        }

        int len = amount.length();
        amount = amount.substring(0, len - 2) + "." + amount.substring(len - 2);
        if (len > 3 && amount.substring(0, 1).equals("0")) amount = amount.substring(1);
        if (len < 3) amount = "0" + amount;
        if (len > preCommaDigits) amount = amount.substring(0, len - preCommaDigits) + "," + amount.substring(len - preCommaDigits);
        text.setText("$" + amount);
    }

    /**
     * Launch the ChangeDescription activity (when the change button is pressed).
     * @param button
     */
    public void onChangeDescriptionClick(View button) {
        Intent intent = new Intent(this, DescriptionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        A.putIntentString(intent, "description", description);
        startActivityForResult(intent, R.id.change_description);
    }

    /**
     * Handle a change of description
     * @param requestCode
     * @param resultCode
     * @param data: the new description
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == R.id.change_description) {
            if(resultCode == RESULT_OK) {
                description = data.getStringExtra("description");
                Button desc = (Button) findViewById(R.id.description);
                //desc.setText(A.ucFirst(description.toLowerCase()));
                desc.setText(description);
            }
            if (resultCode == RESULT_CANCELED) {} // do nothing if no result
        }
    }

    /**
     * Request a transaction on the rCredits server, with the amount entered by the user.
     * @param v
     */
    public void onGoClick(View v) {
        String amount = ((String) ((TextView) findViewById(R.id.amount)).getText()).substring(1); // no "$"
        if (amount.equals("0.00")) {
            sayError("You must enter an amount.", null);
            return;
        }
        if (description.equals(A.DESC_REFUND) || description.equals(A.DESC_USD_IN)) amount = "-" + amount; // a negative doTx
        String goods = (description.equals(A.DESC_USD_IN) || description.equals(A.DESC_USD_OUT)) ? "0" : "1";

        Pairs pairs = new Pairs("op", "charge");
        pairs.add("member", customer);
        pairs.add("amount", amount);
        pairs.add("goods", goods);
        pairs.add("description", description);
        act.progress(true); // this progress meter gets turned off in Tx's onPostExecute()
        new Act.Tx().execute(A.db.storeTx(pairs));
    }
}
