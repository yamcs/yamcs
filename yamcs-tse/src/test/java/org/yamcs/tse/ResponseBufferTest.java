package org.yamcs.tse;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ResponseBufferTest {

    @Test
    public void testMultipleResponses() {
        ResponseBuffer responseBuffer = new ResponseBuffer(StandardCharsets.US_ASCII, "\r\n");
        responseBuffer.append("ABC\r\nDEF\r\nGHI\r\nJKL".getBytes());

        assertEquals("ABC", responseBuffer.readSingleResponse());
        assertEquals("DEF", responseBuffer.readSingleResponse());
        assertEquals("GHI", responseBuffer.readSingleResponse());
        assertEquals(null, responseBuffer.readSingleResponse());

        assertEquals("JKL", responseBuffer.readSingleResponse(true));
        assertEquals(null, responseBuffer.readSingleResponse());
        assertEquals(null, responseBuffer.readSingleResponse(true));

        responseBuffer.append("MN".getBytes());
        assertEquals(null, responseBuffer.readSingleResponse());
        responseBuffer.append("O\r\n".getBytes());
        assertEquals("MNO", responseBuffer.readSingleResponse());
    }
}
