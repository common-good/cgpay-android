/* $Id: $
 */
package com.commongoodfinance.android.pos.net;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import com.commongoodfinance.android.pos.RCreditPOS;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class RESTProcessor {
    /** */
    public static final String LOG_TAG = RCreditPOS.LOG_TAG + ".NET";

    /**
     * HttpClientFactory
     */
    public static interface HttpClientFactory {
        /**
         * @return an interwebs connection
         */
        HttpClient getClient();
    }

    /**
     * ResponseHandler
     */
    public static interface ResponseHandler {
        /**
         * @param req
         * @param resp
         */
        void handleResponse(HttpUriRequest req, HttpResponse resp);

        /**
         * @param req
         * @param err
         */
        void handleFailure(HttpUriRequest req, IOException err);
    }

    private class RequestTask implements Runnable {
        private final HttpUriRequest req;
        private final ResponseHandler hdlr;

        /**
         * @param req
         * @param hdlr
         */
        public RequestTask(HttpUriRequest req, ResponseHandler hdlr) {
            this.req = req;
            this.hdlr = hdlr;
        }

        @Override public void run() {
            try { hdlr.handleResponse(req, execute(req)); }
            catch (IOException e) {
                Log.w(LOG_TAG, "REST req failed", e);
                hdlr.handleFailure(req, e);
            }
        }
    }


    private static final ExecutorService executor = Executors.newSingleThreadExecutor();


    private final HttpClientFactory connectionFactory;

    /**
     * @param connectionFactory
     */
    public RESTProcessor(HttpClientFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * @param request
     * @param hdlr
     */
    public void submit(HttpUriRequest request, ResponseHandler hdlr) {
        executor.execute(new RequestTask(request, hdlr));
    }

    HttpResponse execute(HttpUriRequest req) throws IOException {
        return connectionFactory.getClient().execute(req);
    }
}
