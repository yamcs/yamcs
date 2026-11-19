package org.yamcs.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteOrder;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.TimeEncoding;

class TimeBinaryDecoderTest {
    @Test
    void decodesCucAtAnUnalignedPosition() {
        TimeEncoding.setUp();
        var decoder = new TimeBinaryDecoder(null, null, Map.of("pField", 46, "epoch", "UNIX"));
        var buffer = new BitBuffer(new byte[7]);
        buffer.putBits(1, 1);
        buffer.putBits(42, 32);
        buffer.putBits(0x8000, 16);
        buffer.setPosition(1);
        buffer.setByteOrder(ByteOrder.LITTLE_ENDIAN);

        var value = decoder.extractRaw(null, null, buffer);

        assertEquals(42500L, TimeEncoding.toUnixMillisec(value.getTimestampValue()));
        assertEquals(49, buffer.getPosition());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.getByteOrder());
    }
}
