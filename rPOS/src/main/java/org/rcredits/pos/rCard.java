package org.rcredits.pos;

/**
 * Hold the information gleaned from scanning an rCard.
 */
public class rCard {
    public String region; // the account's region code (usually the first 3 characters of the account ID)
    public String qid; // the account ID (for example, NEW.AAA)
    public String code; // a card security code associated with the account
    public boolean isAgent; // is this a company agent card as opposed to an individual account card
    public class OddCard extends Exception {
        public int type;
        OddCard(int type) {this.type = type;}
    }
    public static int CARD_INVALID = 0;
    public static int CHANGE_MODE = 1;

    /**
     * Extract the member's QID and security code from a scanned URL
     * @param qrUrl eg HTTP://NEW.RC2.ME/I/NEW.AAA-4Dpu1m54k3T
     *              or HTTP://NEW.RC2.ME/I/AAA.4Dpu1m54k3T
     *              (RC2.ME for real cards, RC4.ME for test cards)
     */
    public rCard(String qrUrl) throws OddCard {
        String account;
        String[] parts = qrUrl.split("[/\\.-]");
        int count = parts.length;
        //A.deb("count=" + count);
        if (count != 9 && count != 7) throw new OddCard(CARD_INVALID);

        region = parts[2];
        code = parts[count - 1];
        account = parts[count - 2];
        boolean isTestCard = parts[3].toUpperCase().equals("RC4");
        //boolean proSe = (A.nn(A.agent).indexOf('.') > 0);
        //A.deb("isTestCard=" + String.valueOf(isTestCard));

        int markPos = qrUrl.length() - code.length() - (count == 9 ? account.length() + 2 : 1);
        isAgent = qrUrl.substring(markPos, markPos + 1).equals("-");
        qid = region + (isAgent ? ":" : ".") + account;

        if (A.testing ^ isTestCard) {
            A.setMode(isTestCard);
            throw new OddCard(CHANGE_MODE);
        }
    }

    public static String qidRegion(String qid) {
        String[] parts = qid.split("[\\.-]");
        return parts[0];
    }
}
