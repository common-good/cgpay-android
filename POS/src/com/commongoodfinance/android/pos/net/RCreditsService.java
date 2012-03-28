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
     * @param qrcode
     * @param price
     * @return true if commit succeeded
     */
    public boolean commit(String qrcode, String price) {
        double val = 0;
        try { val = Double.parseDouble(price); }
        catch (NumberFormatException e) { return false; }

        val = val * 100;
        double ival = Math.floor(val);

        if (val != ival) { return false; }

        return true;
    }
}
