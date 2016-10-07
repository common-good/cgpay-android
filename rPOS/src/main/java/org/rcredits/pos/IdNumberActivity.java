package org.rcredits.pos;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Get the customer's driver's license or state ID (just the digits).
 * @return idNumber: the ID number
 */
public class IdNumberActivity extends Act {
    private String idNumber = ""; // the state ID number
    private final int MAX_DIGITS = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photoid2);
    }

    /**
     * Adjust the layout according to the device's orientation.
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_photoid2);
    }

    /**
     * Handle a number pad button press.
     * @param button: which button was pressed (c = clear, b = backspace)
     */
    public void onCalcClick(View button) {
        TextView text = (TextView) findViewById(R.id.id_number);
        idNumber = text.getText().toString();
        String c = (String) button.getContentDescription();
        if (c.equals("c")) {
            idNumber = "";
        } else if (c.equals("b")) {
            if (idNumber.length() > 0) idNumber = idNumber.substring(0, idNumber.length() - 1);
        } else if (idNumber.length() < MAX_DIGITS) { // don't let the number get too big
            idNumber += c;
        } else {
            act.mention("You can have only up to " + MAX_DIGITS + " digits. Press clear (c) or backspace (\u25C0).");
        }

        text.setText(idNumber);
    }

    /**
     * Return the number entered.
     * @param v
     */
    public void onGoClick(View v) {
        if (idNumber.equals("")) {
          sayError("You must enter a number or press the Back key (not to be confused with the red backspace key).", null);
        } else act.returnIntentString(new Pairs("idNumber", idNumber));
    }
}
