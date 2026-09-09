package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;

public class Ccsds131TmChannelDecoderTest {
    private Ccsds131TmChannelDecoder decoder;

    @BeforeEach
    public void setUp() {
        decoder = new Ccsds131TmChannelDecoder();
        decoder.init("test", "tm", YConfiguration.wrap(Map.of("codec", "NONE")));
    }

    @Test
    public void testDecodeUsesRemainingRegion() throws TcTmException {
        byte[] data = { 0, 1, 2, 3, 4, 5 };
        ByteBuffer input = ByteBuffer.wrap(data, 2, 3);

        ByteBuffer decoded = decoder.decode(input);

        assertEquals(2, input.position());
        assertEquals(5, input.limit());
        assertEquals(0, decoded.position());
        assertEquals(3, decoded.remaining());
        byte[] actual = new byte[decoded.remaining()];
        decoded.duplicate().get(actual);
        assertArrayEquals(new byte[] { 2, 3, 4 }, actual);
    }

    @Test
    public void testRejectsDirectBuffer() {
        assertThrows(TcTmException.class, () -> decoder.decode(ByteBuffer.allocateDirect(3)));
    }

    @Test
    public void testRejectsReadOnlyBuffer() {
        assertThrows(TcTmException.class, () -> decoder.decode(ByteBuffer.wrap(new byte[3]).asReadOnlyBuffer()));
    }
}
