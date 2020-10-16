package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class RefXtceDecodingTest {
    static XtceDb xtcedb;
    static MetaCommandProcessor metaCommandProcessor;
    long now = TimeEncoding.getWallclockTime();
    
    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        xtcedb = XtceDbFactory.createInstanceByConfig("refxtce");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", "test", xtcedb, new ProcessorConfig()));
    }
    
    @Test
    public void testBinaryLeadingSize() {
        XtceTmExtractor extractor = new XtceTmExtractor(xtcedb);
        extractor.provideAll();
        
        byte[] buf = new byte[] { 0x03, 0x01, 0x02, 0x03};
        extractor.processPacket(buf, now, now, xtcedb.getSequenceContainer("/RefXtce/packet1"));
        ParameterValueList pvl = extractor.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(xtcedb.getParameter("/RefXtce/param1"));
        assertEquals("010203", StringConverter.arrayToHexString(pv.getEngValue().getBinaryValue()));
        
    }
}
