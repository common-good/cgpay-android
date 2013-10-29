package org.rcredits.pos;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Change the description of the transaction.
 * @intent String description: the current transaction description
 * The description selected in this activity (and passed back to the Tx activity) cannot be "", "(other)", or "refund".
 */
public class DescriptionActivity extends Act {
    private final Act act = this;
//    Resources r = getResources();
//    final int sidePadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, r.getDisplayMetrics());
    final int sidePadding = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_description);
        final String description = A.getIntentString(this.getIntent(), "description");

        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, A.descriptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                if (v.getText().equals(description)) v.setBackgroundColor(Color.GREEN);
                v.setPadding(sidePadding, 0, sidePadding, 0);
                return v;
            }
        };
        final ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int i, long id) {
/*                if (i == 0) { // chose "(other)"
                    findViewById(R.id.list).setVisibility(View.GONE);
                    EditText other = (EditText) findViewById(R.id.other);
                    other.setVisibility(View.VISIBLE);
                    other.requestFocus();
                    other.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN
                                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                    || actionId == EditorInfo.IME_ACTION_DONE)) {
                                A.returnIntentString(act, "description", v.getText().toString());
                            }
                            return false;
                        }
                    });
                } else { */
                    act.returnIntentString("description", A.descriptions[i]);
                //}
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.change_description, menu);
        return true;
    }

}
