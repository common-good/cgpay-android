package org.rcredits.pos;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Ask the customer for a photo ID, record the state and number.
 * @intent place: the customer's city and state
 * @return photoId: the state and number of the customer's driver's license or state ID
 *
 * Maybe show easy arrays of the customer's surrounding states for state input (see below). Or maybe
 * just give two choices: the customer's state and other. The "other" choice would then give a list
 * of all state choices, to choose from. The "?" choice below would similarly lead to that list.
 * VT NH ME     MN  ? NY
 * NY MA  ?     WI MI PA
 * NJ CT RI     IL IN OH
 */
public class PhotoIdActivity extends Act {
    private String stateId; // the state ID number
    private String state; // the state of the state ID
    private Spinner stateSpinner;
    private static final int ID_NUM = 1; // activity return ID for IdNumberActivity
    private static final int MAX_DIGITS = 20;
    private static final int sidePadding = 10;
    private static final List<String> stateList = new ArrayList<String>();
    private static final Map<String, String> states = new TreeMap<String, String>();
    static {
        states.put("AL", "Alabama");
        states.put("AK", "Alaska");
        states.put("AB", "Alberta");
        states.put("AZ", "Arizona");
        states.put("AR", "Arkansas");
        states.put("AS", "American Samoa");
        states.put("AA", "Armed Forces Americas");
        states.put("AE", "Armed Forces (AE)");
        states.put("AP", "Armed Forces Pacific");
        states.put("BC", "British Columbia");
        states.put("CA", "California");
        states.put("CO", "Colorado");
        states.put("CT", "Connecticut");
        states.put("DE", "Delaware");
        states.put("DC", "District Of Columbia");
        states.put("FL", "Florida");
        states.put("GA", "Georgia");
        states.put("GU", "Guam");
        states.put("HI", "Hawaii");
        states.put("ID", "Idaho");
        states.put("IL", "Illinois");
        states.put("IN", "Indiana");
        states.put("IA", "Iowa");
        states.put("KS", "Kansas");
        states.put("KY", "Kentucky");
        states.put("LA", "Louisiana");
        states.put("ME", "Maine");
        states.put("MB", "Manitoba");
        states.put("MD", "Maryland");
        states.put("MA", "Massachusetts");
        states.put("MI", "Michigan");
        states.put("MN", "Minnesota");
        states.put("MS", "Mississippi");
        states.put("MO", "Missouri");
        states.put("MT", "Montana");
        states.put("NE", "Nebraska");
        states.put("NV", "Nevada");
        states.put("NB", "New Brunswick");
        states.put("NH", "New Hampshire");
        states.put("NJ", "New Jersey");
        states.put("NM", "New Mexico");
        states.put("NY", "New York");
        states.put("NF", "Newfoundland");
        states.put("NC", "North Carolina");
        states.put("ND", "North Dakota");
        states.put("NT", "Northwest Territories");
        states.put("NS", "Nova Scotia");
        states.put("NU", "Nunavut");
        states.put("OH", "Ohio");
        states.put("OK", "Oklahoma");
        states.put("ON", "Ontario");
        states.put("OR", "Oregon");
        states.put("PA", "Pennsylvania");
        states.put("PE", "Prince Edward Island");
        states.put("PR", "Puerto Rico");
        states.put("QC", "Quebec");
        states.put("RI", "Rhode Island");
        states.put("SK", "Saskatchewan");
        states.put("SC", "South Carolina");
        states.put("SD", "South Dakota");
        states.put("TN", "Tennessee");
        states.put("TX", "Texas");
        states.put("UT", "Utah");
        states.put("VT", "Vermont");
        states.put("VI", "Virgin Islands");
        states.put("VA", "Virginia");
        states.put("WA", "Washington");
        states.put("WV", "West Virginia");
        states.put("WI", "Wisconsin");
        states.put("WY", "Wyoming");
        states.put("YT", "Yukon Territory");
        for (Map.Entry<String, String> entry : states.entrySet()) stateList.add(entry.getValue());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photoid);

        if (A.selfhelping()) {act.sayFail(R.string.self_help_photo); return;}
        act.askYesNo(A.t(R.string.ask_for_id), null, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                act.sayFail(R.string.need_photo_id);
                return;
            }
        });

        String[] placeParts = A.getIntentString(this.getIntent(), "place").split(", "); // eg Detroit, MI
        state = (placeParts.length == 2) ? states.get(placeParts[1]) : null; // default to the customer's home state

        ArrayAdapter adapter = new ArrayAdapter<String>(this, R.layout.state_spinner, stateList);
        stateSpinner = (Spinner) findViewById(R.id.state);
        stateSpinner.setAdapter(adapter);
        int spinnerPosition = adapter.getPosition(state);
        if (spinnerPosition >= 0) stateSpinner.setSelection(spinnerPosition);
    }


    /**
     * Go all the way back to Main screen on Back Button press.
     */
    @Override
    public void onBackPressed() {act.restart();}

    @Override
    public void goBack(View v) {onBackPressed();}

    /**
     * Handle the ID number returned from IdNumberActivity
     * @param requestCode
     * @param resultCode
     * @param data: the ID number
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ID_NUM) {
            if(resultCode == RESULT_OK) {
                for (Map.Entry<String, String> entry : states.entrySet()) {
                    if (entry.getValue().equals(state)) {state = entry.getKey(); break;}
                }
                act.returnIntentString("photoId", state + "-" + data.getStringExtra("idNumber"));
            } else if (resultCode == RESULT_CANCELED) {}; // do nothing if no number (user must press Back)
        }
    }

    /**
     * Remember the chosen state and go get the number.
     * @param v
     */
    public void onGoClick(View v) {
        state = stateSpinner.getSelectedItem().toString();
        if (state == null) {
            sayError("You must enter a state or press the Back key.", null);
        } else act.start(IdNumberActivity.class, ID_NUM);
    }
}
