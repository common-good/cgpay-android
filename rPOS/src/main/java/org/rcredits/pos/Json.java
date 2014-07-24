package org.rcredits.pos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Handle JSON encoded data.
 * Created by William on 7/9/14.
 */
public class Json {
    public String s;

    Json(String s) {this.s = s;} // externally use Json.make(s) instead
    public static Json make(String s) {return (s == null || s.equals("")) ? null : new Json(s);}

    /**
     * Return a keyed value from an api response array (json-encoded).
     * @param key: key to the value wanted
     * @return the value for that key
     */
    public String get(String key) {
        try {
            JSONObject jsonObj = new JSONObject(s);
            return String.valueOf(jsonObj.get(key));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Return a keyed array from an api response array (json-encoded).
     * @param key: key to the array wanted
     * @return: the array for that key
     */
    public ArrayList<String> getArray(String key) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            JSONObject jsonObj = new JSONObject(s);
            JSONArray jsonArray = jsonObj.getJSONArray(key);
            for (int i = 0; i < jsonArray.length(); i++) list.add(i, jsonArray.get(i).toString());
        } catch (JSONException e) {e.printStackTrace();}
        return list;
    }
}
