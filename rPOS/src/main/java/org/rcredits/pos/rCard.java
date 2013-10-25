package org.rcredits.pos;

/**
 * Created by William on 10/24/13.
 */
public class rCard {
    public String region;
    public String qid;
    public String code;
    public Boolean isAgent;

    /**
     * Extract the member's QID from a scanned URL
     * @param qrUrl (eg HTTP://NEW.RC2.ME/I/NEW.AAA-4Dpu1m54k3T)
     *              (or HTTP://NEW.RC2.ME/I/AAA.4Dpu1m54k3T)
     */
    public rCard(String qrUrl) throws Exception {
        String account, mark;
        String[] parts = qrUrl.split("[/\\.-]");
        Integer count = parts.length;
        if (count != 9 && count != 8) throw new Exception("That is not a valid rCard.");

        region = parts[2];
        code = parts[count - 1];
        account = parts[count - 2];

        Integer markPos = qrUrl.length() - code.length() - (count == 9 ? account.length() + 1 : 0);
        isAgent = qrUrl.substring(markPos, markPos + 1).equals("-");
        qid = region + (isAgent ? ":" : ".") + account;
    }
}
