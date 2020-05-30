package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class ParameterRangesTest {
    static XtceDb db;
    long now = TimeEncoding.getWallclockTime();

    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        db = XtceDbFactory.createInstanceByConfig("ranges-test");
    }

    @Test
    public void test1() {
        XtceTmExtractor extractor = new XtceTmExtractor(db);
        extractor.provideAll();
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).putDouble(90);
        extractor.processPacket(buf, now, now);
        ParameterValue pv = extractor.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.ACQUIRED, pv.getAcquisitionStatus());

        ByteBuffer.wrap(buf).putDouble(90.01);
        extractor.processPacket(buf, now, now);
        ParameterValue pv1 = extractor.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.INVALID, pv1.getAcquisitionStatus());
        

        ByteBuffer.wrap(buf).putDouble(-90.01);
        extractor.processPacket(buf, now, now);
        ParameterValue pv2 = extractor.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.INVALID, pv2.getAcquisitionStatus());
    }
    
    
}
