package earth.commongood.cgpay;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Change the description of the transaction.
 * @intent String description: the current transaction description
 * The description selected in this activity (and passed back to the TxActivity activity) cannot be "", "(other)", or "refund".
 */
public class DescriptionActivity extends Act {
    final int sidePadding = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_description);
        final String description = A.getIntentString(this.getIntent(), "description");

        /**
         * List the choices. Return the one chosen.
         */
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
            act.returnIntentString(new Pairs("description", A.descriptions.get(i)));
            }
        });

    }
}
