package org.yamcs.client.base;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.yamcs.client.ClientException;
import org.yamcs.tests.AbstractIntegrationTest;

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
