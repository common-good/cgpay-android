package org.rcredits.pos;

        import android.content.DialogInterface;
        import android.content.Intent;
        import android.content.res.Configuration;
        import android.os.Bundle;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageButton;
        import android.widget.TextView;

        import java.util.Arrays;

        import static org.rcredits.pos.R.*;

/**
 * Let the user type an amount and say go, sometimes with an option to change the charge description.
 * @intent customer: customer's account ID
 * @intent code: customer's rCard security code
 * @intent description: the current transaction description
 * @intent goods: "1" if the transaction is for real goods and services, "0" for USD, "2" for self-service, "3" non-goods
 * @intent counter: (optional) a transaction counter for the customer
 * @intent photoId: the customer's photo ID number, if appropriate
 * Charges, "USD in", "USD out", and "refund" are all treated similarly.
 */
public class TxActivity extends Act {
    private final int MAX_DIGITS = 6; // maximum number of digits allowed
    private final int PRECOMMA_DIGITS = 5; // maximum number of digits before we need a comma
    private final int CHANGE_DESC = 1; // change-description activity
    private final int GET_USD_TYPE = 2; // usd-type activity
    private final int WHAT_FOR = 3; // activity to let the proSe device owner say what the tx is for
    private final String USD_CHECK_FEE = "$3";
    private final String USD_CARD_FEE = "3%";

    private String customer; // qid of current customer
    private String code; // current customer's rCard security code
    private String description; // transaction description
    private String desc0; // description originally passed to us
    private String amount; // the transaction amount
    private String goods; // is this a purchase/refund of real goods & services (or an exchange for cash)
    private String counter;

    /**
     * Show the appropriate options.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customer = A.getIntentString(this.getIntent(), "customer");
        code = A.getIntentString(this.getIntent(), "code");
        desc0 = description = A.getIntentString(this.getIntent(), "description");
        goods = A.getIntentString(this.getIntent(), "goods");
        counter = A.getIntentString(this.getIntent(), "counter");
        photoId = A.getIntentString(this.getIntent(), "photoId");
        amount = "0.00";
        setLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (A.goingHome) return;
    }

    /**
     * Do what needs doing on creation and orientation change.
     */
    private void setLayout() {
        A.log(0);
        setContentView(layout.activity_tx);
        Button desc = (Button) findViewById(id.description);
        ImageButton changeDesc = (ImageButton) findViewById(id.change_description);

        if (description.equals(A.DESC_USD_IN) || description.equals(A.DESC_USD_OUT)) {
            desc.setText(description);
            changeDesc.setVisibility(View.GONE);
        } else if (description.equals(A.DESC_REFUND) || description.equals(A.DESC_PAY)) {
            desc.setText(description);
            changeDesc.setVisibility(View.GONE);
        } else { // charging
            if (A.descriptions.size() < 2) {
                if (description.equals("")) description = A.DESC_CHARGE; // don't let it be blank
                changeDesc.setVisibility(View.GONE);
            }
            //desc.setText(A.ucFirst(description.toLowerCase()));
            desc.setText(description.toLowerCase());
        }

        ((TextView) findViewById(id.amount)).setText("$" + amount);
        A.log(9);
    }

    /**
     * Adjust the layout according to the device's orientation.
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) { // cgf (this whole method)
        A.log(0);
        super.onConfigurationChanged(newConfig);
        setLayout();
    }

    /**
     * Handle a number pad button press.
     * @param button: which button was pressed (c = clear, b = backspace)
     */
    public void onCalcClick(View button) {
        TextView text = (TextView) findViewById(id.amount);
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
            if (amount.equals("800000") && c.equals("8")) A.b.report("user-initiated report");
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
     * Handle a change of description
     * @param requestCode
     * @param resultCode
     * @param data: the new description
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        A.log(0);
        if (resultCode == RESULT_CANCELED) return;
        if (resultCode != RESULT_OK) {A.b.report("weird result: " + resultCode); return;}

        if (requestCode == CHANGE_DESC) {
            A.log("changing description");
            description = data.getStringExtra("description");
            Button desc = (Button) findViewById(id.description);
            //desc.setText(A.ucFirst(description.toLowerCase()));
            desc.setText(description);
        } else if (requestCode == GET_USD_TYPE) {
            final int usdType = Integer.valueOf(data.getStringExtra("type"));
            A.log("got usdType=" + usdType);
            if (usdType != id.cash) {
                String fee = usdType == id.check ? (USD_CHECK_FEE + " check fee.") : (USD_CARD_FEE + " card fee.");
                act.askOk("The customer will be charged a " + fee, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        finishTx(usdType);
                    }
                }); // otherwise do nothing (stay in this Tx Activity)
            } else finishTx(usdType);
        } else if (requestCode == WHAT_FOR) {
            goods = data.getStringExtra("goods");
            description = data.getStringExtra("description");
            if (A.empty(description)) description = desc0.equals(A.DESC_PAY) ? A.DESC_USD_IN : A.DESC_USD_OUT; // goods.equals("0")
            finishTx(null);
        }
        A.log(9);
    }

    /**
     * Request a transaction on the rCredits server, with the amount entered by the user.
     * @param v
     */
    public void onGoClick(View v) {
        A.log(0);
        //        String amount = A.nn(((TextView) findViewById(R.id.amount)).getText()).substring(1); // no "$"
        if (progressing()) return; // ignore if already processing a (foreground) transaction
        if (amount.equals("0.00")) {sayError("You must enter an amount.", null); return;}
        if (A.proSe()) {
            act.start(ForActivity.class, WHAT_FOR);
        } else {
            if (description.equals(A.DESC_USD_IN)) act.start(UsdActivity.class, GET_USD_TYPE); else finishTx(null);
        }
    }

    /**
     * Complete the transaction, as appropriate.
     * @param usdType: type cash-in payment type (null if not applicable)
     */
    public void finishTx(Integer usdType) {
        A.log(0);
        String created = String.valueOf(A.now());
        String amountPlain = A.fmtAmt(amount, false);
        if (Arrays.asList(new String[]{A.DESC_REFUND, A.DESC_USD_IN, A.DESC_PAY}).contains(desc0)) amountPlain = "-" + amountPlain; // a negative tx
        if (A.empty(goods)) goods = (desc0.equals(A.DESC_USD_IN) || desc0.equals(A.DESC_USD_OUT)) ? "0" : "1";
        String desc = description; // copy, because we might come here again if tx fails
        if (desc0.equals(A.DESC_USD_IN)) desc += usdType == id.cash ? " (cash)" : (usdType == id.check ? " (check)" : " (card)");

        Pairs pairs = new Pairs("op", "charge");
        pairs.add("member", customer);
        pairs.add(DbSetup.TXS_CARDCODE, A.hash(code)); // store hashed code temporarily for delayed identification
        pairs.add("created", created);
        pairs.add("amount", amountPlain);
        pairs.add("proof", A.hash(rCard.co(A.agent) + amountPlain + customer + code + created));
        pairs.add("goods", A.selfhelping() ? "2" : goods);
        pairs.add("description", desc);
        pairs.add("counter", A.nn(counter));
        if (A.db.similarTx(pairs)) {act.sayFail("You already just completed a transaction for that amount with this member."); return;}
// Can't add photoId to pairs until pairs stored in db and retrieved (see Tx)
        act.progress(true); // this progress meter gets turned off in Tx

        try {
            A.log("about to tx");
            new Thread(new Tx(A.db.storeTx(pairs), photoId != null, new handleTxResult())).start();
//            A.executeAsyncTask(new Act.Tx(), A.db.storeTx(pairs));
        } catch (Db.NoRoom e) {act.sayFail(string.no_room); return;}
        A.log(9);
    }
}
