package earth.commongood.cgpay;

import android.graphics.Bitmap;
import android.os.Looper;

/**
 * Identify the member whose card was just scanned.
 * Created by William on 9/23/2016.
 */
public class Identify implements Runnable {
    private rCard rcard;
    private final ResultHandler handle;
	private byte[] image; // photo of customer
    private Json json; // json-encoded response from server

    // return values
    public final static int FAIL = 0;
    public final static int CUSTOMER = 1;
    public final static int AGENT = 2;
    public final static int NO_WIFI = 3;
    public final static int PHOTOID = 4; // scan customer's license/photo ID before permitting transaction
    public final static int DIE = 5;

    public interface ResultHandler {boolean done(int action, String msg, Json json, Bitmap image);}

    public Identify (rCard rcard, ResultHandler handle){
        this.rcard = rcard;
        this.handle = handle;
    }

    @Override
    public void run() {
        A.log(0);
        Looper.prepare();

        try {
            onScan();
        } catch (Db.NoRoom e) {
            handle.done(FAIL, A.t(R.string.no_room), null, null);
        } catch (Exception e) {
	        A.log(e);
            handle.done(NO_WIFI, "", null, null);
        }
        Looper.loop();
        A.log(9);
    }

    /**
     * Process the result from QR scan:
     * Get agent, agentName, customer (etc), device, success, message from server, and handle it.
     * @return result true
     */
    public boolean onScan() throws Db.NoRoom {
        A.log(0);
        image = null; // no image yet
        Pairs pairs = new Pairs("op", "identify");
        pairs.add("member", rcard.qid);
        pairs.add("code", A.hash(rcard.code));

	    String co = A.b.defaults == null ? null : A.b.defaults.get("default");
        boolean isAgent = (co == null || (rcard.isAgent && rCard.co(co).equals(rcard.co)));
//        boolean isAgent = (co == null || rCard.co(co).equals(rcard.co));
        pairs.add("signin", isAgent ? "1" : "0");

        return isAgent ? doAgent(pairs) : doCustomer(pairs);
    }

    /**
     * Handle a scanned customer card.
     */
    private boolean doCustomer(Pairs pairs) throws Db.NoRoom {
	    json = A.apiGetJson(rcard.region, pairs); // get json-encoded info from server
	    if (json == null) {
		    Q q = A.b.db.oldCustomer(rcard.qid);
		    if (q != null) {image = q.getBlob("photo"); q.close();}
		    return handle.done(NO_WIFI, "", null, A.empty(image) ? null : scaledPhoto(image));
	    }
	    if (!json.get("ok").equals("1")) return handle.done(FAIL, json.get("message"), json, null);
	    A.descriptions = json.getArray("descriptions");
	    if (A.descriptions.isEmpty()) return handle.done(FAIL, A.t(R.string.no_descriptions), json, null);
	    A.can = Integer.parseInt(json.get("can")); // stay up-to-date on the signed-out permissions
	    A.b.setDefaults(json, "can descriptions");
	    if (A.b.defaults.get("descriptions").isEmpty()) return handle.done(DIE, "setDefaults description error", json, null);
	    if (doBads()) return handle.done(FAIL, A.t(R.string.fraudulent_rcard), null, null);

	    image = A.apiGetPhoto(rcard.qid, pairs.get("code"));
	    if (image == null || image.length < 100) image = A.b.db.custPhoto(rcard.qid);
	    A.b.db.saveCustomer(rcard.qid, image, pairs.get("code"), json); // might be saving non-photo, which needs updating next time
	    int result = (json.get("first").equals("0") || A.selfhelping() || A.neverAskForId) ? CUSTOMER : PHOTOID;
	    return handle.done(result, "", json, scaledPhoto(image));
    }

    public static Bitmap scaledPhoto(byte[] image) {
        A.log("scaledPhoto|"+image);
        return A.scale(A.bray2bm(image), A.PIC_H);
    }

    /**
     * Handle agent card.
     */
    private boolean doAgent(Pairs pairs) throws Db.NoRoom {
        if (rcard.qid.equals(A.agent)) return handle.done(FAIL, A.t(R.string.already_in), null, null);
        json = A.apiGetJson(rcard.region, pairs); // get json-encoded info from server
	    if (json == null) { //return handle.done(NO_WIFI, "", null, null); // assume it's a customer (since we can't tell)
		    Q q = A.b.db.oldCustomer(rcard.qid);
		    if (q == null) return handle.done(FAIL, A.t(R.string.wifi_for_setup), null, null);
		    if (!q.isAgent()) A.b.report("non-agent");
		    if (A.b.db.badAgent(rcard.qid, rcard.code)) {q.close(); return handle.done(FAIL, "That Company Common Good Card is not valid.", null, null);}
		    gotAgent(q.getString("name"), q.getInt(DbSetup.AGT_CAN));
		    q.close();
	    } else {
		    A.log("id msg: " + json.get("message"));
		    if (!json.get("ok").equals("1")) return handle.done(FAIL, json.get("message"), json, null);
		    if (A.empty(A.deviceId)) A.setStored("deviceId", A.deviceId = json.get("device"));
		    image = A.apiGetPhoto(rcard.qid, pairs.get("code"));
		    if (image == null || image.length < 100) return handle.done(FAIL, "Card validation failed. Try again?", null, null);
		    if (doBads()) return handle.done(FAIL, A.t(R.string.invalid_rcard), null, null);

		    A.b.setDefaults(json);
		    gotAgent(json.get("name"), A.n(json.get("can")).intValue());
		    A.b.db.saveAgent(rcard.qid, rcard.abbrev, rcard.code, image, json); // save or update manager info
	    }
	    return handle.done(AGENT, "", null, null);
    }

    /**
     * Handle successful scan of company agent's rCard: remember who.
     */
    private void gotAgent(String name, int can) {
        A.log("got agent: " + name);
        A.agent = rcard.qid;
        A.agentName = name;
        A.can = can;
        A.signedIn = true;
    }

    /**
     * Remember and handle any new bad customers.
     * @return <the card is invalid>
     */
    private boolean doBads() throws Db.NoRoom {
        String[] newBads = new String[0];
        newBads = json.getArray("bad").toArray(newBads);
        String[] ray, params;
        String qid, code, hashCode;
        boolean bad = false;

        for (String k : newBads) {
            ray = k.split(",");
            code = ray.length < 2 ? "" : ray[1];
            params = new String[] {qid = ray[0], code, hashCode = A.hash(code)};
	        A.b.db.q("DELETE FROM members WHERE qid=? AND code IN (?,?)", params);
	        if (rcard.qid.equals(qid) && (rcard.code.equals(code) || rcard.code.equals(hashCode))) bad = true;
	        if (!A.b.db.enoughRoom()) throw new Db.NoRoom();
	        A.b.db.q("INSERT INTO bad (qid, code) VALUES (?, ?)", new String[]{qid, code}); // A.b.db.insert fails with no e. (Android bug)
        }
        return bad;
    }
}

