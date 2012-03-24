/* $Id: $
 */
package com.commongoodfinance.android.pos.test;

import java.io.IOException;
import java.util.concurrent.CyclicBarrier;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import com.commongoodfinance.android.pos.net.RESTProcessor;
import com.commongoodfinance.android.pos.net.RESTProcessor.HttpClientFactory;
import com.commongoodfinance.android.pos.net.RESTProcessor.ResponseHandler;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class RESTProcessorTest extends TestCase {

    static class MockHttpClientFactory implements HttpClientFactory {
        @Override
        public HttpClient getClient() { return new MockHttpClient(); }
    }

    boolean succeeded;
    private RESTProcessor processor;

    /**
     * @throws java.lang.Exception
     */
    @Override
    @Before
    public void setUp() throws Exception {
        processor = new RESTProcessor(new MockHttpClientFactory());
    }

    /**
     *
     */
    @Test
    public void testProcessor() {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        succeeded = false;

        processor.submit(
            new HttpGet("http://commongoodfinance.com/pos?woot"),
            new ResponseHandler() {
                @Override
                public void handleFailure(HttpUriRequest req, IOException err) { }
                @Override
                public void handleResponse(HttpUriRequest req, HttpResponse resp) {
                    try {
                        succeeded = true;
                        barrier.await();
                    }
                    catch (Exception e) { succeeded = false; }
                }
            });

        try { barrier.await(); }
        catch (Exception e) { fail("interrupted: " + e); }

        assertTrue(succeeded);
    }
}
