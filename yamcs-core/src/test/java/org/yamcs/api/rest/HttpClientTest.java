package org.yamcs.api.rest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.AbstractIntegrationTest;
import org.yamcs.client.ClientException;
import org.yamcs.client.base.HttpClient;

import io.netty.handler.codec.http.HttpMethod;

public class HttpClientTest extends AbstractIntegrationTest {

    HttpClient client = new HttpClient();

    @Test
    public void testConnectionRefused() throws Exception {
        Throwable t = null;
        try {
            client.doAsyncRequest("http://localhost:32323/blaba", HttpMethod.GET, null).get(2,
                    TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            t = e.getCause();
        }

        assertNotNull(t);
        assertTrue(t instanceof ConnectException);
    }

    @Test
    public void test404NotFound() throws Exception {
        Throwable t = null;
        try {
            client.doAsyncRequest("http://localhost:9190/blaba", HttpMethod.GET, null)
                    .get(2, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            t = e.getCause();
        }

        assertNotNull(t);
        assertTrue(t instanceof ClientException);
        assertTrue(t.getMessage().contains("404"));
    }
}
