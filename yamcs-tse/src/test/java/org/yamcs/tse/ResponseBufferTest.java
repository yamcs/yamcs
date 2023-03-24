package org.yamcs.tse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class ResponseBufferTest {

    @Test
    public void testMultipleResponses() {
        var responseBuffer = new ResponseBuffer("\r\n", true);
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

    @Test
    public void testUnfragmented() {
        var responseBuffer = new ResponseBuffer(null, false);
        responseBuffer.append("ABC\r\nDEF\r\nGHI\r\nJKL".getBytes());
        assertArrayEquals("ABC\r\nDEF\r\nGHI\r\nJKL".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse(true));
    }

    @Test
    public void testUnfragmentedWithTerminationCharacter() {
        // If a termination character is defined, then we will still reassemble fragments.
        var responseBuffer = new ResponseBuffer("\r\n", false);
        responseBuffer.append("ABC\r\nDEF\r\nGHI\r\nJKL".getBytes());
        assertArrayEquals("ABC".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals("DEF".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals("GHI".getBytes(), responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse());

        assertArrayEquals("JKL".getBytes(), responseBuffer.readSingleResponse(true));
        assertArrayEquals(null, responseBuffer.readSingleResponse());
        assertArrayEquals(null, responseBuffer.readSingleResponse(true));
    }
}
