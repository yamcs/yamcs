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
}
