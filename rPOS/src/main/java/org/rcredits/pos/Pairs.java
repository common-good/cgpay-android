package org.rcredits.pos;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage Lists of NameValuePairs.
 * Created by William on 7/24/14.
 */
public class Pairs {
    public List<NameValuePair> list;

    Pairs() {list = new ArrayList<NameValuePair>(1);}
    Pairs(String name, String value) {
        list = new ArrayList<NameValuePair>(1);
        add(name, value);
    }
    Pairs(Pairs old) {
        list = new ArrayList<NameValuePair>(1);
        for (NameValuePair pair : old.list) add(pair.getName(), pair.getValue());
    }

    public Pairs copy() {return new Pairs(this);}

    /**
     * Convert the name and value to a "pair" (a NameValuePair) and add it to the list.
     * @param name: name of parameter to add
     * @param value: value of parameter to add
     * @return: the modified list
     */
    public Pairs add(String name, String value) {
        list.add(new BasicNameValuePair(name, value));
        return this;
    }

    /**
     * Extract the named value from the array of name/value pairs.
     */
    public String get(String k) {
        for (NameValuePair pair : list) {
            if (pair.getName().equals(k)) return pair.getValue();
        }
        return null;
    }

    /**
     * Return a printable string displaying each name/value pair.
     * @param omit
     * @return
     */
    public String show(String omit) {
//        A.deb("showPairs pairs is null: " + (pairs == null ? "yes" : "no"));
        String result = "";
        for (NameValuePair pair : list) {
            if (!pair.getName().equals(omit)) result = result + " " + pair.getName() + "=" + pair.getValue();
        }
        return result;
    }

    public String show() {return show("");}
}
