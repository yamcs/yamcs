package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.utils.TimeEncoding;

public class ContainerEntryTest {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;
    MetaCommandProcessor metaCommandProcessor;
    static ProcessorData pdata;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("container-entry");
        pdata = new ProcessorData("test", mdb, new ProcessorConfig());
    }

    @Test
    public void testPartialSubscription() {
        extractor = new XtceTmExtractor(mdb);
        var p2 = mdb.getParameter("/ce/p2");
        extractor.startProviding(p2);
        byte[] buf = new byte[] { 0, 1 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now, 0, mdb.getSequenceContainer("/ce/sc2"));
        assertEquals(1, cpr.getParameterResult().getFirstInserted(p2).getEngValue().getUint32Value());
    }
}
