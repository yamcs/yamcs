package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.utils.StringConverter;

public class CucTimeEncoderTest {

    @Test
    public void testImplicit() {
        CucTimeEncoder cte = new CucTimeEncoder(0x2E, true);
        byte[] b = new byte[6];
        long expectedTimeMillis = Instant.parse("2018-07-06T11:41:18.284Z").toEpochMilli();
        cte.encode(expectedTimeMillis, b, 0);

        assertEquals("5B3F555E48B4", StringConverter.arrayToHexString(b));
    }

    @Test
    public void testExplicit() {
        CucTimeEncoder cte = new CucTimeEncoder(0x2E, false);
        byte[] b = new byte[7];
        long expectedTimeMillis = Instant.parse("2018-07-06T11:41:18.284Z").toEpochMilli();

        cte.encode(expectedTimeMillis, b, 0);

        assertEquals("2E5B3F555E48B4", StringConverter.arrayToHexString(b));

        long rawTime = 0x5B3F555E48B4L;
        byte[] bRaw = new byte[7];
        cte.encodeRaw(rawTime, bRaw, 0);

        assertEquals("2E5B3F555E48B4", StringConverter.arrayToHexString(bRaw));
    }
}
