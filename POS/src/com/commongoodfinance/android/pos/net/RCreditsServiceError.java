/* $Id: $
 */
package com.commongoodfinance.android.pos.net;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class RCreditsServiceError extends Exception {

    /**
     *
     */
    public RCreditsServiceError() { super(); }

    /**
     * @param throwable
     */
    public RCreditsServiceError(Throwable throwable) { super(throwable); }

    /**
     * @param detailMessage
     */
    public RCreditsServiceError(String detailMessage) { super(detailMessage); }

    /**
     * @param detailMessage
     * @param throwable
     */
    public RCreditsServiceError(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

}
