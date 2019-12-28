package earth.commongood.cgpay;

import android.content.ContentValues;

/**
 * Hold the information gleaned from scanning an rCard.
 */
public class rCard {
    private Db db;
    public String region; // the account's region code (usually the first 3 characters of the account ID)
    public String qid; // the account ID (for example, NEW.AAA)
    public String code; // a card security code associated with the account
    public String co; // the company account ID for an agent qid (same as qid for an individual account)
    public String counter; // optional transaction counter for abbreviated new-format codes
    public String abbrev; // abbreviated QR for agents
    public boolean isAgent; // is this a company agent card as opposed to an individual account card
    public boolean isOdd; // card is a test card when expecting real, or vice-versa
    public class BadCard extends Exception {
        public int type;
        BadCard(int type) {this.type = type;}
    }
    public final static int CARD_NOT = 0; // QR is not for an rCard
    public final static int CARD_FRAUD = 1; // card has been invalidated (lost, stolen, or attempted fake)
    private final static int CODE_LEN_MIN = 11; // some old cards have codes this short
    private final static int CODE_LEN_MAX = 64; // head off db errors
    private final static String oldAgentQids = "INAAAA/INAAAF-A,MIWAAC/MIWAAK-A,MIWAAD/MIWAAY-A,MIWAAE/MIWAAY-B," +
            "MIWAAF/MIWAAY-C,MIWAAH/MIWACA-A,MIWAAJ/MIWACE-A,MIWAAL/MIWADZ-A,MIWAAM/MIWAAY-D," +
            "MIWAAP/MIWADO-A,MIWAAQ/MIWADR-A,MIWAAV/MIWADQ-A,MIWAAY/MIWAEP-A,MIWABB/MIWAER-A," +
            "MIWABC/MIWAER-B,MIWABD/MIWAFH-A,NEVAAA/NEVAAP-A,NEVAAB/NEVAAW-A,NEVAAC/NEVAAZ-A," +
            "NEWAAB/NEWAAB-A,NEWAAD/NEWAAQ-A,NEWAAF/NEWAAQ-B,NEWAAG/NEWAIL-A,NEWAAI/NEWAAR-A," +
            "NEWAAJ/NEWAEY-A,NEWAAK/NEWAEY-B,NEWAAT/NEWAHA-A,NEWAAU/NEWAGV-A,NEWAAV/NEWAHH-A," +
            "NEWAAY/NEWABC-A,NEWAAZ/NEWABC-B,NEWABA/NEWABD-A,NEWABC/NEWAHS-A,NEWABF/NEWABE-A," +
            "NEWABJ/NEWAEY-C,NEWABK/NEWABM-A,NEWABL/NEWABI-A,NEWABN/NEWAIL-B,NEWABP/NEWABM-B," +
            "NEWABS/NEWABM-C,NEWABT/NEWAJW-A,NEWABW/NEWABC-C,NEWACC/NEWABC-D,NEWACG/NEWACJ-A," +
            "NEWACM/NEWAIX-A,NEWACN/NEWABC-E,NEWACT/NEWAHY-A,NEWACV/NEWAJQ-A,NEWACX/NEWAJX-A," +
            "NEWADC/!NEWAAC-A,NEWADD/NEWAKC-A,NEWADE/NEWAAS-A,NEWADH/NEWAAS-B,NEWADM/NEWAEG-A";
    // eventually we will want to replace these old cards (once they have all signed in once)
    private final static String regionLens = "112233344"; // field lengths implied by format character divided by 4
    private final static String acctLens = "232323445";

    /**
     * Extract the member's QID and security code from a scanned URL
     * @param qrUrl eg HTTP://NEW.RC2.ME/I/NEW.AAA-4Dpu1m54k3T (deprecated, but some early cards use it)
     *              or HTTP://NEW.RC2.ME/AAA.4Dpu1m54k3T
     *              or HTTP://6VM.RC2.ME/CAAAW4Dpu1m54k3T
     *              or C6VMAAAW4Dpu1m54k3T-whatever (. instead of -, for test QRs)
     *              (RC2.ME for real cards, RC4.ME for test cards)
     */
    rCard (String qrUrl) throws BadCard {
        A.log(0);
        try { //code is simpler if we can ignore string index errors until here
            parse(qrUrl);
        } catch (StringIndexOutOfBoundsException  e) {throw new BadCard(CARD_NOT);}
    }

    private void parse(String qrUrl) throws BadCard {
        String account;
        String[] parts = qrUrl.split("[/\\.-]");
        int count = parts.length;
        boolean isTestCard;

        if (count > 2) {
            region = parts[2];
            isTestCard = parts[3].toUpperCase().equals("RC4");
        } else isTestCard = (qrUrl.indexOf("-") < 0);
        db = isTestCard ? A.bTest.db : A.bReal.db; // might be different from A.b.db

        if (count == 2) { // abbreviated new format (for QRs displayed by app)
            String fmt = qrUrl.charAt(0) + ""; // one radix 36 digit representing format (field lengths)
            if (!fmt.matches("[0-9A-Z]")) throw new BadCard(CARD_NOT);
            int i = Integer.parseInt(fmt, 36) / 4;
            int regionLen = Integer.parseInt(regionLens.charAt(i) + "");

            region = A.substr(qrUrl, 1, regionLen);
            counter = parts[1];
            newFormat(fmt + parts[0].substring(1 + regionLen), isTestCard); // pretend it was the long new format to get the rest
        } else if (count == 6) { // new format
            newFormat(parts[5], isTestCard);
        } else if (count == 7 || count == 9){ // old formats
            A.log("old fmt");
            code = parts[count - 1];
            account = parts[count - 2];
            int markPos = qrUrl.length() - code.length() - (count == 9 ? account.length() + 2 : 1);
            qid = region + account;
            if (isAgent = A.substr(qrUrl, markPos, 1).equals("-")) {
                int i = oldAgentQids.indexOf(qid + "/");
                if (i < 0) throw new BadCard(CARD_NOT);
                oldAgent(region + ":" + account, A.substr(oldAgentQids, i + 7, 8), isTestCard);
            } else co = qid;
            if (!qid.matches("^[A-Z]{6}(-[A-Z])?")) throw new BadCard(CARD_NOT);
            abbrev = region + "/" + account + (isAgent ? "-" : ".");
        } else throw new BadCard(CARD_NOT);

        if (code.length() < CODE_LEN_MIN || code.length() > CODE_LEN_MAX || !code.matches("^[A-Za-z0-9]+")) throw new BadCard(CARD_NOT);
        if (db.exists("bad", "qid=? AND code IN (?,?)", new String[]{qid, code, A.hash(code)})) throw new BadCard(CARD_FRAUD);

        if (isOdd = (A.b.test ^ isTestCard)) A.setMode(isTestCard);
    }
    
    private void newFormat(String tail, boolean isTestCard) throws BadCard {
        A.log("new fmt");
        String fmt = tail.charAt(0) + ""; // one radix 36 digit representing format (field lengths)
        if (!fmt.matches("[0-9A-Z]")) throw new BadCard(CARD_NOT);

        int i = Integer.parseInt(fmt, 36);
        int agentLen = i % 4;
        int acctLen = Integer.parseInt(acctLens.charAt(i / 4) + "");

        if (acctLen == 6 || tail.length() < 1 + acctLen + agentLen) throw new BadCard(CARD_NOT);
        String account = A.substr(tail, 1, acctLen);
        String agent = A.substr(tail, 1 + acctLen, agentLen);
        code = tail.substring(1 + acctLen + agentLen);
        abbrev = fmt + region + account + agent;
        isAgent = (agentLen > 0);

        try {
            region = base36to26AZ_3(region);
            account = base36to26AZ_3(account);
            agent = isAgent ? ("-" + base36to26AZ(agent)) : "";
        } catch (NumberFormatException e) {throw new BadCard(CARD_NOT);}

        co = region + account;
        qid = co + agent;

        if (!qid.matches("^[A-Z]{3,4}[A-Z]{3,5}(-[A-Z]{1,5})?")) throw new BadCard(CARD_NOT);
    }

    /**
     * Handle an old-format agent card, in case an agent is signing in
     * @param oldQid: something like XXX:YYY
     * @param newQid: something like XXXYYY-W
     */
    private void oldAgent(String oldQid, String newQid, boolean isTestCard) {
        A.log(String.format("convert old agent %s to %s", oldQid, newQid));
        qid = newQid;
        co = A.substr(qid, 0, 6);
        String where = String.format("%s=%s AND qid=?", DbSetup.AGT_FLAG, A.TX_AGENT);
        Long rowid = db.rowid("members", where, new String[]{oldQid});
        if (rowid != null) { // this agent needs updating
            ContentValues fix = new ContentValues();
            fix.put("qid", qid);
            db.update("members", fix, rowid);
            A.log("converted");
        }
    }

    public static String qidRegion(String qid) {
        if (qid == null) return null;
        String[] parts = qid.split("[\\.:\\-]");
        String part0 = parts[0];
        return part0.length() > 3 ? part0.substring(0, part0.length() / 2) : part0;
    }

    /**
     * Return the given qid's company (same as qid if the qid is for an individual account)
     * @param qid
     * @return
     */
    public static String co(String qid) {return qid == null ? null : qid.split("-")[0];}

    public static boolean isAgent(String qid) {return qid == null ? false : (qid.indexOf("-") != -1);}

    /**
     * Convert the integer to base 26 using just capital letters
     * @param n
     * @return n in base 26 A-Z
     */
    public static String base26AZ(int n) {
        String s = Integer.toString(n, 26);
        String s2 = "";
        for (int i = 0; i < s.length(); i++) s2 += (char) ('A' + Integer.parseInt(s.charAt(i) + "", 26));
        return s2;
    }
    
    private static String base36to26AZ(String n) {return base26AZ(Integer.parseInt(n, 36));}

    /**
     * Convert base 36 to base 26, as above, but with at least three letters (A-Z)
     * @param n
     * @return n in base 26 A-Z, left-filled with "A"s
     */
    private static String base36to26AZ_3(String n) {
        String s = "AAA" + base36to26AZ(n); // pad with "zero"s on the left
        return s.substring(s.length() - 3);
    }
}

