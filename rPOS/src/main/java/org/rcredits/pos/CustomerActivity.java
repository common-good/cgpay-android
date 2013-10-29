package org.rcredits.pos;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Show the name, location, and photo of the customer.
 * @intent customer: qid of customer being identified
 * @intent customerRegion: customer's region code
 * @intent json: identifying information about the customer (name, company, place), json-encoded
 */
public class CustomerActivity extends Act {
    private final Act act = this;
    public static String customer; // qid of current customer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        customer = A.getIntentString(this.getIntent(), "customer");
        String customerRegion = A.getIntentString(this.getIntent(), "customerRegion");
        String json = A.getIntentString(this.getIntent(), "json");

        TextView customerName = (TextView) findViewById(R.id.customer_name);
        customerName.setText(A.jsonString(json, "name"));

        String company = A.jsonString(json, "company");
        TextView customerCompany = (TextView) findViewById(R.id.customer_company);
        customerCompany.setText(company);
        customerCompany.setVisibility(company == null ? View.VISIBLE : View.GONE);

        TextView customerPlace = (TextView) findViewById(R.id.customer_place);
        customerPlace.setText(A.jsonString(json, "place"));

        if (!A.canRefund) findViewById(R.id.refund).setVisibility(View.INVISIBLE);

        ImageView photo = (ImageView) findViewById(R.id.photo);
        final byte[] image = A.apiGetPhoto(act, customerRegion, customer);
        photo.setImageBitmap(BitmapFactory.decodeByteArray(image, 0, image.length));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.customer, menu);
        return true;
    }

    public void onRClick(View button) {
        Intent intent = new Intent(this, Tx.class);
        if (button.getId() == R.id.charge) A.putIntentString(intent, "description", A.descriptions[1]);
        A.putIntentString(intent, "customer", customer);
        startActivity(intent);
    }
    
}
