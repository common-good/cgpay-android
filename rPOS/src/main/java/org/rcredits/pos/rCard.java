package org.rcredits.pos;

/**
 * Hold the information gleaned from scanning an rCard.
 */
public class rCard {
    public String region; // the account's region code (usually the first 3 characters of the account ID)
    public String qid; // the account ID (for example, NEW.AAA)
    public String code; // a card security code associated with the account
    public boolean isAgent; // is this a company agent card as opposed to an individual account card

    /**
     * Extract the member's QID and security code from a scanned URL
     * @param qrUrl eg HTTP://NEW.RC2.ME/I/NEW.AAA-4Dpu1m54k3T
     *              or HTTP://NEW.RC2.ME/I/AAA.4Dpu1m54k3T
     *              (RC2.ME for real cards, RC4.ME for test cards)
     */
    public rCard(String qrUrl) throws Exception {
        String account, mark;
        String[] parts = qrUrl.split("[/\\.-]");
        int count = parts.length;
        if (count != 9 && count != 7) throw new Exception("That is not a valid rCard.");

        region = parts[2];
        code = parts[count - 1];
        account = parts[count - 2];
        boolean isTestCard = parts[3].toUpperCase().equals("RC4");

        if (A.testing == null) {
            A.testing = isTestCard;
        } else {
            if (A.testing && !isTestCard) throw new Exception("That is not a test card. Tap the Agent's name to sign out, then scan your Company Agent card to sign in for REAL.");
            if (!A.testing && isTestCard) throw new Exception("That is not a real rCard. Tap your name to sign out, then scan a test agent card to sign in for TESTING.");
        }

        int markPos = qrUrl.length() - code.length() - (count == 9 ? account.length() + 2 : 1);
        isAgent = qrUrl.substring(markPos, markPos + 1).equals("-");
        qid = region + (isAgent ? ":" : ".") + account;
    }
}
