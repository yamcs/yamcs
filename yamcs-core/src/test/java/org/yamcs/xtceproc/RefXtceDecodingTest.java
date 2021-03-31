package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class RefXtceDecodingTest {
    static XtceDb xtcedb;
    static MetaCommandProcessor metaCommandProcessor;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;
    
    @BeforeClass
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        xtcedb = XtceDbFactory.createInstanceByConfig("refxtce");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", "test", xtcedb, new ProcessorConfig()));
    }
    
    @Before
    public void before() {
        extractor = new XtceTmExtractor(xtcedb);
        extractor.provideAll();
    }

    @Test
    public void testBinaryLeadingSize() {
        byte[] buf = new byte[] { 0x03, 0x01, 0x02, 0x03};
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/RefXtce/packet1"));
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(xtcedb.getParameter("/RefXtce/param1"));
        assertEquals("010203", StringConverter.arrayToHexString(pv.getEngValue().getBinaryValue()));
        
    }

    @Test
    public void testFixedSizeArray() {
        byte[] buf = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/RefXtce/packet3"));
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(xtcedb.getParameter("/RefXtce/param8"));
        ArrayValue av = (ArrayValue) pv.getEngValue();
        assertEquals(4, av.flatLength());
        assertEquals(0x0102, av.getElementValue(0).getUint32Value());
        assertEquals(0x0304, av.getElementValue(1).getUint32Value());
        assertEquals(0x0506, av.getElementValue(2).getUint32Value());
        assertEquals(0x0708, av.getElementValue(3).getUint32Value());

    }
}
