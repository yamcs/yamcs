package org.yamcs.tse;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ResponseBufferTest {

    @Test
    public void testMultipleResponses() {
        ResponseBuffer responseBuffer = new ResponseBuffer("\r\n");
        responseBuffer.append("ABC\r\nDEF\r\nGHI\r\nJKL".getBytes());

        assertArrayEquals("ABC".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals("DEF".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals("GHI".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse());

        assertArrayEquals("JKL".getBytes(), responseBuffer.readSingleResponse(true));
        assertArrayEquals(null, responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse(true));

        responseBuffer.append("MN".getBytes());
        assertArrayEquals(null, responseBuffer.readSingleResponse());
        responseBuffer.append("O\r\n".getBytes());
        assertArrayEquals("MNO".getBytes(), responseBuffer.readSingleResponse());
    }
}
