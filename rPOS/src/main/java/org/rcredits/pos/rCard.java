package org.rcredits.pos;

/**
 * Hold the information gleaned from scanning an rCard.
 */
public class rCard {
    public String region; // the account's region code (usually the first 3 characters of the account ID)
    public String qid; // the account ID (for example, NEW.AAA)
    public String code; // a card security code associated with the account
    public String co; // the company account ID for an agent qid (same as qid for an individual account)
    public boolean isAgent; // is this a company agent card as opposed to an individual account card
    public class OddCard extends Exception {
        public int type;
        OddCard(int type) {this.type = type;}
    }
    public static int CARD_INVALID = 0;
    public static int CHANGE_MODE = 1;
    private static int CODE_LEN_MIN = 11; // some old cards have codes this short

    /**
     * Extract the member's QID and security code from a scanned URL
     * @param qrUrl eg HTTP://NEW.RC2.ME/I/NEW.AAA-4Dpu1m54k3T (deprecated, but some early cards use it)
     *              or HTTP://NEW.RC2.ME/AAA.4Dpu1m54k3T
     *              or HTTP://NEW.RC2.ME/CAAAW4Dpu1m54k3T
     *              (RC2.ME for real cards, RC4.ME for test cards)
     */
    public rCard(String qrUrl) throws OddCard {
        String account;
        String[] parts = qrUrl.split("[/\\.-]");
        int count = parts.length;
        //A.deb("count=" + count);
        if (count != 9 && count != 7 && count != 6) throw new OddCard(CARD_INVALID);

        region = parts[2];
        boolean isTestCard = parts[3].toUpperCase().equals("RC4");
        if (count == 6) { // new format
            String tail = parts[5];
            char fmt = tail.charAt(0);
            String[] fmts = {"", "", "012389ABGHIJ", "4567CDEFKLMN", "OPQRSTUV", "WXYZ"};
            int acctLen, i; i = 0;
            for (acctLen = 2; acctLen < 6; acctLen++) {
                i = fmts[acctLen].indexOf(fmt);
                if (i != -1) break;
            }
            int agentLen = i % 4;
            if (acctLen == 6 || tail.length() < 1 + acctLen + agentLen) throw new OddCard(CARD_INVALID);
            account = tail.substring(1, 1 + acctLen);
            String agent = tail.substring(1 + acctLen, 1 + acctLen + agentLen);
            code = tail.substring(1 + acctLen + agentLen);

            region = n2a(a2n(region));
            account = n2a(a2n(account));
            agent = agentLen == 0 ? "" : ("-" + n2a(a2n(agent), -1, 26));

//            co = region + "." + account;
            co = region + account;
            qid = co + agent;
//            if (!qid.matches("^[A-Z0-9]{3,13}$")) throw new OddCard(CARD_INVALID);
//            if (!qid.matches("^[A-Z]{3,4}(:|\\.)[A-Z]{3,5}(-[A-Z]{1,5})?")) throw new OddCard(CARD_INVALID);
            if (!qid.matches("^[A-Z]{3,4}[A-Z]{3,5}(-[A-Z]{1,5})?")) throw new OddCard(CARD_INVALID);
        } else { // old formats
            code = parts[count - 1];
            account = parts[count - 2];
            int markPos = qrUrl.length() - code.length() - (count == 9 ? account.length() + 2 : 1);
            isAgent = qrUrl.substring(markPos, markPos + 1).equals("-");
            co = qid = region + (isAgent ? ":" : "") + account;
            if (!qid.matches("^[A-Z]{3}:?[A-Z]{3}")) throw new OddCard(CARD_INVALID);
        }

        if (code.length() < CODE_LEN_MIN || !code.matches("^[A-Za-z0-9]+")) throw new OddCard(CARD_INVALID);

        //boolean proSe = (A.nn(A.agent).indexOf('.') > 0);
        //A.deb("isTestCard=" + String.valueOf(isTestCard));

        if (A.testing ^ isTestCard) {
            A.setMode(isTestCard);
            throw new OddCard(CHANGE_MODE);
        }
    }

    public static String qidRegion(String qid) {
        String[] parts = qid.split("[\\.:\\-]");
        String part0 = parts[0];
        return part0.length() > 3 ? part0.substring(0, part0.length() / 2) : part0;
    }

    public static String co(String qid) {
        String[] parts = qid.split("-");
        return parts[0];
    }

    /**
     * Return an alphabetic representation of the given non-negative integer.
     * A is the zero digit, B is 1, etc.
     * @param n: the integer to represent
     * @param len: the length of the string to return
     *   <=0 means length >=-len
     * @param base: the radix (2 to 26)
     */
    private String n2a(int n, int len, int base) {
        final int A = (int) 'A';
        String result = "";
        int digit;
        for (int i = 0; (len > 0 ? (i < len) : (n > 0 || i < -len)); i++) {
            digit = n % base;
//            result = Character.toString((char) (digit < 10 ? digit : (A + digit - 10))) + result;
            result = ((char) (A + digit)) + result;
//            result = Character.toString((char) (A + digit)) + result;
            n /= base;
        }
        return result;
    }
    private String n2a(int n) {return n2a(n, -3, 26);}

    /**
     * Return the numeric equivalent of the given number expressed in an arbitrary base (2 to 36).
     * @see n2a()
     */
    private int a2n(String s, int base) {
        final int A = (int) 'A';
        final int zero = (int) '0';
        int result = 0;
        int n;
        for (int i = 0; i < s.length(); i++) {
            n = s.charAt(i);
            result = result * base + n - (n >= A ? A - 10 : zero);
        }
        return result;
    }
    private int a2n(String s) {return a2n(s, 36);}


}

