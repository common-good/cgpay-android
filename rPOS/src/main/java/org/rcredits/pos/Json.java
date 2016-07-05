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
    private JSONObject j;

    Json(String s) { // externally use Json.make(s) instead
        try {
            this.j = new JSONObject(s);
         } catch (JSONException e) {this.j = null;}
    }
    Json() {new Json("{}");}

    Json(JSONObject j) {this.j = j;}

    public static Json make(String s) {
        if (s == null || s.equals("")) return null;
        Json res = new Json(s);
        return (res.j == null) ? null : res;
    }

    public Json copy() {return new Json(this.j);}
    public String toString() {return this.j.toString();}

    /**
     * Return a keyed value from an api response array (json-encoded).
     * @param key: key to the value wanted
     * @return the value for that key
     */
    public String get(String key) {
        try {
            return this.j.get(key).toString();
        } catch (JSONException e) {return null;}
    }

    /**
     * Return a keyed array from an api response array (json-encoded).
     * @param key: key to the array wanted
     * @return: the array for that key
     */
    public ArrayList<String> getArray(String key) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            JSONArray jsonArray = this.j.getJSONArray(key);
            for (int i = 0; i < jsonArray.length(); i++) list.add(i, jsonArray.get(i).toString());
        } catch (JSONException e) {e.printStackTrace();}
        return list;
    }

    public Json put(String key, String value) {
        try {
            this.j.put(key, value);
            return this;
        } catch (JSONException e) {
            return null;
        }
    }
}
