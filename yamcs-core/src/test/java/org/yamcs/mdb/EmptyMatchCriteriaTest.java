package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class EmptyMatchCriteriaTest {
    @Test
    public void test() {
        YConfiguration.setupTest(null);
        XtceDb db = XtceDbFactory.createInstanceByConfig("empty-match-criteria");
        XtceTmExtractor extractor = new XtceTmExtractor(db);
        long now = TimeEncoding.getWallclockTime();

        extractor.startProviding(db.getParameter("/EMC/para2"));
        byte[] buf = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(buf);
        bb.putDouble(1.0);
        bb.putDouble(2.0);
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now, 0);
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(db.getParameter("/EMC/para2"));
        assertEquals(2.0, pv.getEngValue().getDoubleValue(), 1e-5);
    }
}
