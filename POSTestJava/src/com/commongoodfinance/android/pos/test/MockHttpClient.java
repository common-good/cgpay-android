/* $Id: $
 */
package com.commongoodfinance.android.pos.test;

import static junit.framework.Assert.fail;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class MockHttpClient implements HttpClient {

    @Override
    public HttpResponse execute(HttpUriRequest req) {
        return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
    }

    @Override
    public HttpResponse execute(HttpUriRequest req, HttpContext context) {
        fail("unimplemented");
        return null;
    }

    @Override
    public HttpResponse execute(HttpHost target, HttpRequest req) {
        fail("unimplemented");
        return null;
    }

    @Override
    public <T> T execute(HttpUriRequest arg0, ResponseHandler<? extends T> arg1) {
        fail("unimplemented");
        return null;
    }

    @Override
    public HttpResponse execute(HttpHost target, HttpRequest req, HttpContext context) {
        fail("unimplemented");
        return null;
    }

    @Override
    public <T> T execute(HttpUriRequest arg0, ResponseHandler<? extends T> arg1, HttpContext arg2) {
        fail("unimplemented");
        return null;
    }

    @Override
    public <T> T execute(HttpHost arg0, HttpRequest arg1, ResponseHandler<? extends T> arg2) {
        fail("unimplemented");
        return null;
    }

    @Override
    public <T> T execute(
        HttpHost arg0, HttpRequest arg1,
        ResponseHandler<? extends T> arg2,
        HttpContext arg3)
    {
        fail("unimplemented");
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        fail("unimplemented");
        return null;
    }

    @Override
    public HttpParams getParams() {
        fail("unimplemented");
        return null;
    }

}
