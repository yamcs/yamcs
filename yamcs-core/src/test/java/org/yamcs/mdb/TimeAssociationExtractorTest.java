package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.Parameter;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;

public class TimeAssociationExtractorTest {
    static Mdb mdb;
    XtceTmExtractor extractor;
    long now = TimeEncoding.getWallclockTime();

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        TimeEncoding.setUp();
        mdb = MdbFactory.createInstanceByConfig("time-association");
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void testTimeAssociationAppliedToEntries() throws IOException {
        var packet = createPacket(1000L, 0x11223344, 0x55667788);
        ParameterValueList pvl = extractor.processPacket(packet, now, now, 0,
                mdb.getSequenceContainer("/Example/packet")).getParameterResult();

        Parameter packetTimeParam = getParameterByName("packet_time");
        Parameter valueParam = getParameterByName("value");

        var packetTime = pvl.get(packetTimeParam, 0);
        var value = pvl.get(valueParam, 0);
        var embeddedValue = pvl.get(valueParam, 1);

        assertEquals(TimeEncoding.fromUnixMillisec(1000), packetTime.getEngValue().getTimestampValue());
        assertEquals(now, packetTime.getGenerationTime());
        assertEquals(TimeEncoding.fromUnixMillisec(200), value.getGenerationTime());
        assertEquals(TimeEncoding.fromUnixMillisec(400), embeddedValue.getGenerationTime());
    }

    private Parameter getParameterByName(String name) {
        Parameter parameter = mdb.getParameters().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(parameter, "Missing parameter " + name);
        return parameter;
    }

    private byte[] createPacket(long packetTime, int value, int embeddedValue) throws IOException {
        ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(arrayStream);
        out.writeLong(packetTime);
        out.writeInt(value);
        out.writeInt(embeddedValue);
        return arrayStream.toByteArray();
    }
}
