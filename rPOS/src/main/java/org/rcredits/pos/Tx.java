package org.rcredits.pos;

        import android.content.Context;
        import android.content.DialogInterface;
        import android.content.Intent;
        import android.os.Bundle;
        import android.app.Activity;
        import android.view.Menu;
        import android.view.View;
        import android.widget.Button;
        import android.widget.ImageButton;
        import android.widget.TextView;
        import android.widget.Toast;

/**
 * Let the user type an amount and choose whether the charge is for cash, the default description, or something else.
 * @intent String description: the current transaction description (if none, assume it's a refund)
 */
public class Tx extends Activity {
    public static final int maxDigits = 6; // maximum number of digits allowed
    public static final int preCommaDigits = 5; // maximum number of digits before we need a comma

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tx);
        Button goods = (Button) findViewById(R.id.goods);
        String description = A.getIntentString(this.getIntent(), "description");
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
            Toast.makeText(this, "You can have only up to " + maxDigits + " digits. Press clear (c) or backspace (\u25C0).", Toast.LENGTH_SHORT).show();
        }

        Integer len = amount.length();
        amount = amount.substring(0, len - 2) + "." + amount.substring(len - 2);
        if (len > 3 && amount.substring(0, 1).equals("0")) amount = amount.substring(1);
        if (len < 3) amount = "0" + amount;
        if (len > preCommaDigits) amount = amount.substring(0, len - preCommaDigits) + "," + amount.substring(len - preCommaDigits);
        text.setText("$" + amount);
    }

    public void onChangeDescriptionClick(View button) {
        Intent intent = new Intent(this, ChangeDescription.class);
        startActivity(intent);
    }

    public void onGoodsClick(View button) {
        A.amount = (String) ((TextView) findViewById(R.id.amount)).getText();
        // submit tx to server, get result
        if (true) { // error
            A.sayError(this, "Transaction FAILED for whatever reason", null);
        } else {
            A.balance = "3"; // get from server
            A.lastTx = "234"; // get from server
            Intent intent = new Intent(this, CaptureActivity.class);
            startActivity(intent);
        }
    }
}
