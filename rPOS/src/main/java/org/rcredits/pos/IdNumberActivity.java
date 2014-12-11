package org.rcredits.pos;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Ask the customer for a photo ID, record the state and number.
 * @intent place: the customer's city and state
 * @return photoid: the state and number of the customer's driver's license or state ID
 *
 * Maybe show easy arrays of the customer's surrounding states for state input (see below). Or maybe
 * just give two choices: the customer's state and other. The "other" choice would then give a list
 * of all state choices, to choose from. The "?" choice below would similarly lead to that list.
 * VT NH ME     MN  ? NY
 * NY MA  ?     WI MI PA
 * NJ CT RI     IL IN OH
 */
public class IdNumberActivity extends Act {
    private final Act act = this;
    private String stateId; // the state ID number
    private String state; // the state of the state ID
    private final int MAX_DIGITS = 20;
    private final int sidePadding = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photoid);

        act.askYesNo(A.t(R.string.ask_for_id), null, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.sayFail("I'm sorry, rCredits members must show a valid photo ID before making their first purchase.");
            }
        });

        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, A.descriptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setBackgroundColor(v.getText().toString().equals(description) ? Color.CYAN : Color.WHITE);
                v.setPadding(sidePadding, 0, sidePadding, 0);
                return v;
            }
        };
        final ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int i, long id) {
                act.returnIntentString("description", A.descriptions.get(i));
            }
        });

        final ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int i, long id) {
            act.returnIntentString("description", A.descriptions.get(i));
            }
        });

    }

    /**
     * Handle a number pad button press.
     * @param button: which button was pressed (c = clear, b = backspace)
     */
    public void onCalcClick(View button) {
        TextView text = (TextView) findViewById(R.id.stateid);
        stateId = text.getText().toString();
        String c = (String) button.getContentDescription();
        if (c.equals("c")) {
            stateId = "";
        } else if (c.equals("b")) {
            stateId = stateId.substring(0, stateId.length() - 1);
        } else if (stateId.length() < MAX_DIGITS) { // don't let the number get too big
            stateId += c;
        } else {
            act.mention("You can have only up to " + MAX_DIGITS + " digits. Press clear (c) or backspace (\u25C0).");
        }

        text.setText(stateId);
    }
}
