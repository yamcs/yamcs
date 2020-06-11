package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.base.HttpClient;
import org.yamcs.http.HttpServer;
import org.yamcs.http.StaticFileHandler;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

public class HttpServerTest extends AbstractIntegrationTest {

    @Test
    public void testStaticFile() throws Exception {
        YamcsServer.getServer().getGlobalServices(HttpServer.class).get(0).addStaticRoot(Paths.get("/tmp/yamcs-web"));

        HttpClient httpClient = new HttpClient();
        File dir = new File("/tmp/yamcs-web/");
        dir.mkdirs();

        File file1 = File.createTempFile("test1_", null, dir);
        FileOutputStream file1Out = new FileOutputStream(file1);
        Random rand = new Random();
        byte[] b = new byte[1932];
        for (int i = 0; i < 20; i++) {
            rand.nextBytes(b);
            file1Out.write(b);
        }
        file1Out.close();

        File file2 = File.createTempFile("test2_", null, dir);
        FileOutputStream file2Out = new FileOutputStream(file2);

        httpClient
                .doBulkReceiveRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null, data -> {
                    try {
                        file2Out.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).get();
        file2Out.close();
        assertTrue(com.google.common.io.Files.equal(file1, file2));

        // test if not modified since
        SimpleDateFormat dateFormatter = new SimpleDateFormat(StaticFileHandler.HTTP_DATE_FORMAT);

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified()));
        ClientException e1 = null;
        try {
            httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                    httpHeaders).get();
        } catch (ExecutionException e) {
            e1 = (ClientException) e.getCause();
        }
        assertNotNull(e1);
        assertTrue(e1.toString().contains("304"));

        httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified() - 1000));
        byte[] b1 = httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), HttpMethod.GET, null,
                httpHeaders).get();
        assertEquals(file1.length(), b1.length);

        file1.delete();
        file2.delete();
    }
}
