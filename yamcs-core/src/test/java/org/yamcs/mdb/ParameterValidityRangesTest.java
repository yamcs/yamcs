package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.TimeEncoding;

public class ParameterValidityRangesTest {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("ranges-test");
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void test1() {
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).putDouble(90);
        ContainerProcessingResult cpr = processPacket(buf);
        ParameterValue pv = cpr.getParameterResult()
                .getFirstInserted(mdb.getParameter("/Example/latitude"));
        assertTrue(pv.isNominal());

        ByteBuffer.wrap(buf).putDouble(90.01);
        cpr = processPacket(buf);
        ParameterValue pv1 = cpr.getParameterResult()
                .getFirstInserted(mdb.getParameter("/Example/latitude"));
        assertTrue(pv1.isInvalid());

        ByteBuffer.wrap(buf).putDouble(-90.01);
        cpr = processPacket(buf);
        ParameterValue pv2 = cpr.getParameterResult()
                .getFirstInserted(mdb.getParameter("/Example/latitude"));
        assertTrue(pv2.isInvalid());
    }

    private ContainerProcessingResult processPacket(byte[] buf) {
        return extractor.processPacket(buf, now, now, 0);
    }
}
