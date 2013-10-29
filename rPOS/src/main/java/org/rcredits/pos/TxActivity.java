package org.rcredits.pos;

        import android.content.Intent;
        import android.os.Bundle;
        import android.view.Menu;
        import android.view.View;
        import android.widget.Button;
        import android.widget.TextView;

        import org.apache.http.NameValuePair;

        import java.util.List;

/**
 * Let the user type an amount and choose whether the charge is for cash, the default description, or something else.
 * @intent String description: the current transaction description (if none, assume it's a refund)
 */
public class TxActivity extends Act {
    private final Act act = this;
    private static final int maxDigits = 6; // maximum number of digits allowed
    private static final int preCommaDigits = 5; // maximum number of digits before we need a comma
    private static String customer; // qid of current customer
    private static String description;
    private Button goods;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tx);
        goods = (Button) findViewById(R.id.goods);

        customer = A.getIntentString(this.getIntent(), "customer");
        description = A.getIntentString(this.getIntent(), "description").toLowerCase();
        if (description.equals("")) { // refunding
            goods.setText("Refund");
            findViewById(R.id.cash).setVisibility(View.GONE);
            findViewById(R.id.change_description).setVisibility(View.GONE);
        } else { // charging
            goods.setText(A.ucFirst(description));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onCalcClick(View button) {
        TextView text = (TextView) findViewById(R.id.amount);
        String amount = text.getText().toString().replaceAll("[,\\.\\$]", "");
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

        Integer len = amount.length();
        amount = amount.substring(0, len - 2) + "." + amount.substring(len - 2);
        if (len > 3 && amount.substring(0, 1).equals("0")) amount = amount.substring(1);
        if (len < 3) amount = "0" + amount;
        if (len > preCommaDigits) amount = amount.substring(0, len - preCommaDigits) + "," + amount.substring(len - preCommaDigits);
        text.setText("$" + amount);
    }

    public void onChangeDescriptionClick(View button) {
        Intent intent = new Intent(this, DescriptionActivity.class);
        A.putIntentString(intent, "description", description);
        startActivityForResult(intent, R.id.change_description);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == R.id.change_description) {
            if(resultCode == RESULT_OK) {
                description = data.getStringExtra("description");
                goods.setText(A.ucFirst(description));
            }
            if (resultCode == RESULT_CANCELED) {} // do nothing if no result
        }
    }

    public void onGoodsClick(View v) {
        List<NameValuePair> pairs = A.auPair(null, "member", customer);
        A.auPair(pairs, "amount", ((String) ((TextView) findViewById(R.id.amount)).getText()).substring(1)); // no "$"
        A.auPair(pairs, "description", v.getId() == R.id.cash ? "" : (String) goods.getText());
        A.doTx(act, "charge", pairs);
    }
}
