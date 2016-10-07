/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rcredits.pos;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

/**
 * The main settings activity.
 * @author William Spademan 7/27/2014
 */
public final class PrefsActivity extends Act {
    // OLD constants
    public static final String KEY_PLAY_BEEP = "preferences_play_beep";
    public static final String KEY_VIBRATE = "preferences_vibrate";
    public static final String KEY_FRONT_LIGHT_MODE = "preferences_front_light_mode";
    public static final String KEY_AUTO_FOCUS = "preferences_auto_focus";
    public static final String KEY_INVERT_SCAN = "preferences_invert_scan";
    public static final String KEY_SEARCH_COUNTRY = "preferences_search_country";
    public static final String KEY_DISABLE_CONTINUOUS_FOCUS = "preferences_disable_continuous_focus";
    //public static final String KEY_DISABLE_EXPOSURE = "preferences_disable_exposure";
    private int[] canButtons = {R.id.can_charge, R.id.can_undo, R.id.can_usdin, R.id.can_usdout, R.id.can_refund, R.id.can_pay};
    private int[] agtButtons = {R.id.agt_charge, R.id.agt_undo, R.id.agt_usdin, R.id.agt_usdout, R.id.agt_refund, R.id.agt_pay};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prefs);

        findViewById(R.id.emptyTestDb).setVisibility((A.b.test && A.fakeScan && A.agent != null) ? View.VISIBLE : View.INVISIBLE);
        ((CheckBox) findViewById(R.id.wifi)).setChecked(!A.wifiOff);
        ((CheckBox) findViewById(R.id.selfhelp)).setChecked(A.selfhelp);
        findViewById(R.id.selfhelp_row).setVisibility(A.proSe() ? View.GONE : View.VISIBLE);
//        ((CheckBox) findViewById(R.id.demo)).setChecked(A.demo);
//        findViewById(i).setVisibility(A.b.test ? View.VISIBLE : View.GONE);

        /*
        for (int i = 0; i < A.CAN_AGENT - 2; i++) { // -2: ignore CAN_U6 and CAN_MANAGE permission
            ((CheckBox) findViewById(canButtons[i])).setChecked(A.can(i + A.CAN_CASHIER));
            ((CheckBox) findViewById(agtButtons[i])).setChecked(A.can(i + A.CAN_AGENT));
        }
        */
    }

    @Override
    public void onBackPressed() { // not onDestroy, which happens AFTER MainActivity resumes
        if (A.selfhelp) A.signOut(); // must precede super...
        super.onBackPressed();
    }

    /*    public void onPrefsBoxClick(CheckBox v) {
        int i = find(v.getId(), canButtons) + A.CAN_CASHIER;
        if (i < 0) i = find(v.getId(), agtButtons) + A.CAN_AGENT;
        A.setCan(i, v.isChecked());
    }*/

    public void emptyTestDb(View v) {
        if (!A.fakeScan) {act.sayFail("You can do this only when debugging, to be safe."); return;}
        for (String table : "members txs log".split(" ")) A.bTest.db.q("DELETE FROM " + table);
        A.bTest.db.q("VACUUM");
        A.settings.edit().clear().commit(); //remove all
        A.signOut();
        A.agent = A.xagent = A.agentName = null;
        A.descriptions = null;
        A.can = 0;
        act.goHome();
    }

    public void onWifiToggle(View v) {
        boolean wifi = ((CheckBox) findViewById(R.id.wifi)).isChecked();
        if (wifi) {
            A.wifiOff = false; // avoid giving a message
        } else act.setWifi(wifi); // warn about re-connecting soon
    }

    public void onSelfHelpToggle(View v) {
        A.selfhelp = ((CheckBox) findViewById(R.id.selfhelp)).isChecked();
        if (A.selfhelp) act.sayOk("Self-Help Mode", R.string.self_help_signout, null);
    }

/*    public void onDemoToggle(View v) {
        A.demo = ((CheckBox) findViewById(R.id.demo)).isChecked();
        A.setStored("demo", A.demo ? "1" : "0");
    }
*/
    private int find(int needle, int[] hay) {
        for (int i = 0; i < hay.length; i++) if (hay[i] == needle) return i;
        return -1;
    }
}
