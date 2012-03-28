/* $Id: $
 */
package com.commongoodfinance.android.pos.net;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.commongoodfinance.android.pos.R;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class RCreditsService {
    public static final class Transaction {
        public final String id;
        public final String customer;
        public final String amount;
        Transaction (String id, String customer, String amount) {
            this.id = id;
            this.customer = customer;
            this.amount = amount;
        }
    }


    private final Context context;


    /**
     * @param context
     */
    public RCreditsService(Context context) { this.context = context; }

    /**
     * @param qrcode
     * @return a picture of the customer
     */
    public Drawable getCustomerImage(String qrcode) {
        Drawable cust = context.getResources().getDrawable(R.drawable.jane);
        System.out.println(cust);
        return cust;
    }

    /**
     * @param customerId
     * @param price
     * @return true if commit succeeded
     * @throws RCreditsServiceException
     */
    public Transaction postTransaction(String customerId, int price)
        throws RCreditsServiceException, RCreditsServiceError
    {
        double v = price;
        v = v / 100;
        return new Transaction(customerId, "CY-745236981-1", String.valueOf(v));
    }
}
