/* $Id: $
 */
package com.commongoodfinance.android.pos.test;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.RelativeLayout;

import com.commongoodfinance.android.pos.IdleActivity;
import com.commongoodfinance.android.pos.R;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class IdleActivityTest extends
    ActivityInstrumentationTestCase2<IdleActivity>
{

    private IdleActivity idleActivity;
    private RelativeLayout idleView;

    /**
     *
     */
    public IdleActivityTest() {
        super("POS.Idle", IdleActivity.class);
    }

    /**
     * @see android.test.ActivityInstrumentationTestCase2#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        //setActivityContext( );
        idleActivity = this.getActivity();
        idleView = (RelativeLayout) idleActivity.findViewById(R.id.scan_button);
    }

    public void testPreconditions() {
        assertNotNull(idleView);
    }

    public void testText() {
//        idleActivity.startScanner();
//        assertEquals(resourceString,(String)mView.getText());
    }
}
