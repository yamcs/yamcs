package org.yamcs.tests;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.yamcs.YamcsServer;
import org.yamcs.client.ClientException;
import org.yamcs.client.base.HttpClient;
import org.yamcs.http.HttpServer;
import org.yamcs.http.StaticFileHandler;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;

public class HttpServerTest extends AbstractIntegrationTest {

    @Test
    public void testStaticFile() throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-web");

        var staticFileHandler = new StaticFileHandler("/static", dir);
        YamcsServer.getServer().getGlobalService(HttpServer.class).addRoute("static", () -> staticFileHandler);

        HttpClient httpClient = new HttpClient();
        Files.createDirectories(dir);

        File file1 = File.createTempFile("test1_", null, dir.toFile());
        FileOutputStream file1Out = new FileOutputStream(file1);
        Random rand = new Random();
        byte[] b = new byte[1932];
        for (int i = 0; i < 20; i++) {
            rand.nextBytes(b);
            file1Out.write(b);
        }
        file1Out.close();

        File file2 = File.createTempFile("test2_", null, dir.toFile());
        try (var file2Out = new FileOutputStream(file2)) {
            httpClient.doBulkReceiveRequest("http://localhost:9190/static/" + file1.getName(), GET, null, data -> {
                try {
                    file2Out.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).get();
        }
        assertTrue(com.google.common.io.Files.equal(file1, file2));

        // test if not modified since
        SimpleDateFormat dateFormatter = new SimpleDateFormat(StaticFileHandler.HTTP_DATE_FORMAT);

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified()));
        ClientException e1 = null;
        try {
            httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), GET, null,
                    httpHeaders).get();
        } catch (ExecutionException e) {
            e1 = (ClientException) e.getCause();
        }
        assertNotNull(e1);
        assertTrue(e1.toString().contains("304"));

        httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(IF_MODIFIED_SINCE, dateFormatter.format(file1.lastModified() - 1000));
        byte[] b1 = httpClient.doAsyncRequest("http://localhost:9190/static/" + file1.getName(), GET, null,
                httpHeaders).get();
        assertEquals(file1.length(), b1.length);

        file1.delete();
        file2.delete();
    }
}
