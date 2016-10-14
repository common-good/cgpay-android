package org.rcredits.pos;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Created by William on 10/3/2016.
 * Whether to "B" real or just testing.
 */
public class B {
    private final B b = this;
    public Db db; // real or test db (should be always open when needed)
    public boolean test;
    public boolean periodic = false; // <the periodic activity for this B has begun>
    public Json defaults = null; // parameters to use when no agent is signed in (empty if not allowed)
    public Long lastGetTime = 0L;
    public boolean doReport = false; // tells Periodic to send report to server

    public B (boolean testing) {
        b.test = testing;
        b.db = new Db(testing);
        b.defaults = Json.make(A.getStored(defaultsName())); // null if none
    }

    public String defaultsName() {return defaultsName(b.test);}
    public static String defaultsName(boolean testing) {return testing ? "defaults_test" : "defaults";}

    public void setDefaults(Json json, String ks) {
        A.log(0);
        if (json == null) return;

        if (ks != null) {
            for (String k : ks.split(" ")) b.defaults.put(k, json.get(k));
        } else b.defaults = json.copy();
        A.setStored(b.defaultsName(), b.defaults.toString());
        A.log(9);
    }
    public void setDefaults(Json json) {setDefaults(json, null);}

    // device company's rCredits region (based on defaults)
    public String region() {return b.defaults == null ? null : rCard.qidRegion(b.defaults.get("default"));}

    public void launchPeriodic() {
        if (!b.periodic && !A.empty(b.region())) {
            new Thread(new Periodic(b)).start();
            b.periodic = true;
        }
    }

    /**
     * Get the server's clock time
     * @param data: whatever data the server just requested (null if no request)
     * @return the server's unixtime, as a string (null if not available)
     */
    public String getTime(String data) {
        A.log(0);
        if (b.region() == null) return null; // time is tied to region
        b.lastGetTime = A.now();
        Pairs pairs = new Pairs("op", "time");
        if (data != null) pairs.add("data", data);
        HttpResponse response = A.post(b.region(), pairs);
        if (response == null) return null;

        try {
            Json json = Json.make(EntityUtils.toString(response.getEntity()));
            if (json == null) return null;
            String msg = json.get("message");
            if (A.empty(msg)) return json.get("time");

            for (String k : DbSetup.TABLES.split(" ")) {
                if (msg.equals("!" + k)) return getTime(A.b.db.dump(k)); // send db data to server
            }

            if (msg.equals("!device")) { // send device data to server
                return getTime(A.getDeviceData());
            } else if (msg.equals("!report")) {
                return b.report("report requested from server");
            } else if (msg.length() > 8 && msg.substring(0, 8).equals("!delete:")) {
                String[] parts = msg.split("[:,]");
                int count = A.b.db.delete(parts[1], Long.valueOf(parts[2]));
                return getTime("deleted:" + count);
            } // all other messages require the UI, so are handled in MainActivity

            if (A.empty(A.sysMessage)) A.sysMessage = msg; // don't overwrite
            A.log(9);
            return json.get("time");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String signinPath() {
        String path = (b.test ? A.TEST_PATH : A.REAL_PATH).replace("<region>", b.region());
        return path + "/signin" + (A.proSe() ? "?name=" + A.agent : "");
    }

    /**
     * Report something to the server on our own initiative
     * @param data
     */
    public String report(String data) {
        A.log(data, 2);
        b.doReport = true;
        A.log(9); // search for .report in logcat
        return null;
    }

}
