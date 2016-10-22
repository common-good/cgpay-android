package org.rcredits.pos;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;


/**
 * Change the description of the transaction.
 * returns the type of transaction (goods, usd, or nongoods) and the description (if any)
 */
public class ForActivity extends Act {
    private static EditText forWhat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_for);
        forWhat = (EditText) findViewById(R.id.for_what);
        forWhat.setVisibility(View.INVISIBLE);

        forWhat.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onGoClick(v);
                    return true;
                } else return false;
            }
        });
    }

    public void onGoodsClick(View v) {
        if (v.getId() == R.id.goods_usd) {onGoClick(v); return;} // no description needed for USD
        forWhat.setVisibility(View.VISIBLE);
        if(forWhat.requestFocus()) {
//            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(forWhat, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onGoClick(View v) {
        act.progress(true);
        RadioGroup group = (RadioGroup) findViewById(R.id.goods);
        int i = group.getCheckedRadioButtonId();
        if (i < 0) return; // no button clicked yet

        i = group.indexOfChild(findViewById(i)); // convert to 0,1,2
        String goods = i == 1 ? "1" : (i == 2 ? "0" : "3");
        String description = ((TextView) findViewById(R.id.for_what)).getText().toString();
        act.returnIntentString(new Pairs("goods", goods).add("description", description));
    }
}
