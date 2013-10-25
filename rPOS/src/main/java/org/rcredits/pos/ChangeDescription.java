package org.rcredits.pos;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Change the description of the transaction.
 * @intent String description: the current transaction description
 * The description selected in this activity (and passed back to the Tx activity) cannot be "", "(other)", or "refund".
 */
public class ChangeDescription extends Activity {
//    Resources r = getResources();
//    final int sidePadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, r.getDisplayMetrics());
    final int sidePadding = 10;
    final Context context = this; // required for use in inner class

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
                //Toast.makeText(getApplicationContext(), "You chose " + descriptions[i], Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(context, Tx.class);
            A.putIntentString(intent, "description", A.descriptions[i]);
            startActivity(intent);
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
