package org.yamcs.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HttpUtilsTest {

    @Test
    public void testRedactUri() {
        assertEquals("/cb", HttpUtils.redactUri("/cb"));
        assertEquals("/cb?code=***&state=abc", HttpUtils.redactUri("/cb?code=secret&state=abc"));
        assertEquals("/cb?state=abc&code=***", HttpUtils.redactUri("/cb?state=abc&code=secret"));
        assertEquals("/x?access_token=***&id_token=***&refresh_token=***",
                HttpUtils.redactUri("/x?access_token=a&id_token=b&refresh_token=c"));
        assertEquals("/api/foo?zipcode=1000&code", HttpUtils.redactUri("/api/foo?zipcode=1000&code"));
    }

    @Test
    public void testAttachmentDisposition() {
        assertEquals("attachment; filename=\"a.html\"; filename*=UTF-8''a.html",
                HttpUtils.attachmentDisposition("a.html"));
        assertEquals("attachment; filename=\"my _x_.txt\"; filename*=UTF-8''my%20%22x%22.txt",
                HttpUtils.attachmentDisposition("my \"x\".txt"));
        assertEquals("attachment; filename=\"__.txt\"; filename*=UTF-8''%C3%A9%0D.txt",
                HttpUtils.attachmentDisposition("é\r.txt"));
    }
}
