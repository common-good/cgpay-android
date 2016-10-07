package org.rcredits.pos;

import android.content.ContentValues;
// import org.apache.http.ContentValues;
//import org.apache.http.message.BasicContentValues;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manage Lists of ContentValues.
 * Created by William on 7/24/14.
 */
public class Pairs {
    public ContentValues list;

    Pairs () {list = new ContentValues();}
    public Pairs (String name, String value) {
        list = new ContentValues();
        add(name, value);
    }
    Pairs (Pairs old) {list = new ContentValues(old.list);}

    public Pairs copy() {return new Pairs(this);}

    /**
     * Convert the name and value to a "pair" (a ContentValues) and add it to the list.
     * @param name: name of parameter to add
     * @param value: value of parameter to add
     * @return: the modified list
     */
    public Pairs add(String name, String value) {
        list.put(name, value);
        return this;
    }

    /**
     * Extract the named value from the array of name/value pairs (null if not found).
     */
    public String get(String k) {return list.getAsString(k);}

    /**
     * Return a printable string displaying each name/value pair.
     * @param omit
     * @return
     */
    public String show(String omit) {
//        A.log("showPairs pairs is null: " + (pairs == null ? "yes" : "no"));
        if (omit.equals("")) return list.toString();
        Pairs pairs = new Pairs(this);
        pairs.list.remove(omit);
        return pairs.list.toString();
    }

    public String show() {return show("");}

    public List<NameValuePair> toPost() {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>(2);
        for (Map.Entry<String, Object> e : list.valueSet()) {
            pairs.add(new BasicNameValuePair(e.getKey(), list.getAsString(e.getKey())));
        }
        return pairs;
    }
}
