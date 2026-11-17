package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Checks the CDS time decoder configured in a packet preprocessor, including the conversion from UTC (CDS does not
 * count the leap seconds) to Yamcs time.
 */
public class CdsTimePreprocessorTest {

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(false);
    }

    static GenericPacketPreprocessor preprocessor(Map<String, Object> timeEncoding) {
        Map<String, Object> args = new HashMap<>();
        args.put("timestampOffset", 0);
        args.put("seqCountOffset", -1);
        args.put("timeEncoding", timeEncoding);
        return new GenericPacketPreprocessor("test", YConfiguration.wrap(args));
    }

    @Test
    public void test1958Epoch() {
        var pp = preprocessor(Map.of("type", "CDS", "implicitPField", 0x40,
                "epoch", "CUSTOM", "epochUTC", "1958-01-01T00:00:00Z", "timeIncludesLeapSeconds", false));

        // day 22101, ms of day 42078284 (see CdsTimeDecoderTest)
        byte[] packet = StringConverter.hexStringToArray("56550282104C00000000");
        TmPacket tmPacket = pp.process(new TmPacket(TimeEncoding.getWallclockTime(), packet));

        assertFalse(tmPacket.isInvalid());
        assertEquals(TimeEncoding.parse("2018-07-06T11:41:18.284Z"), tmPacket.getGenerationTime());
        assertEquals("2018-07-06T11:41:18.284Z", TimeEncoding.toString(tmPacket.getGenerationTime()));
    }

    @Test
    public void testUnixEpoch() {
        // agency defined epoch 1970-01-01 (pfield 0x48), explicit pfield in the packet
        var pp = preprocessor(Map.of("type", "CDS", "epoch", "UNIX"));

        // 1970-01-01 to 2018-07-06 is 17718 = 0x4536 days
        byte[] packet = StringConverter.hexStringToArray("4845360282104C00000000");
        TmPacket tmPacket = pp.process(new TmPacket(TimeEncoding.getWallclockTime(), packet));

        assertFalse(tmPacket.isInvalid());
        assertEquals("2018-07-06T11:41:18.284Z", TimeEncoding.toString(tmPacket.getGenerationTime()));
    }
}
