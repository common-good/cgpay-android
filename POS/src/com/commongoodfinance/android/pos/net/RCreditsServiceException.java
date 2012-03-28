/* $Id: $
 */
package com.commongoodfinance.android.pos.net;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class RCreditsServiceException extends Exception {

    /**
     *
     */
    public RCreditsServiceException() { super(); }

    /**
     * @param throwable
     */
    public RCreditsServiceException(Throwable throwable) { super(throwable); }

    /**
     * @param detailMessage
     */
    public RCreditsServiceException(String detailMessage) { super(detailMessage); }

    /**
     * @param detailMessage
     * @param throwable
     */
    public RCreditsServiceException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public String getReason() { return null; }
}
