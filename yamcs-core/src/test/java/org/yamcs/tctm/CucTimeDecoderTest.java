package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.utils.StringConverter;

public class CucTimeDecoderTest {

    @Test
    public void testImplicit() {
        CucTimeDecoder ctd = new CucTimeDecoder(0x2E);
        // byte[] b = StringConverter.hexStringToArray("5B3F555E48B4");
        byte[] b = StringConverter.hexStringToArray("5B3F555E48B4");
        long t = ctd.decode(b, 0);
        assertEquals("2018-07-06T11:41:18.283Z", Instant.ofEpochMilli(t).toString());
    }

    @Test
    public void testExplicit() {
        CucTimeDecoder ctd = new CucTimeDecoder(-1);
        // byte[] b = StringConverter.hexStringToArray("5B3F555E48B4");
        byte[] b = StringConverter.hexStringToArray("2E5B3F555E48B4");
        long t = ctd.decode(b, 0);
        assertEquals("2018-07-06T11:41:18.283Z", Instant.ofEpochMilli(t).toString());

        long rt = ctd.decodeRaw(b, 0);
        assertEquals(0x5B3F555E48B4l, rt);
    }
}
