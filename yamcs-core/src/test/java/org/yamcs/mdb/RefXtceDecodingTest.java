package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SequenceContainer;

public class RefXtceDecodingTest {
    static Mdb mdb;
    static MetaCommandProcessor metaCommandProcessor;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("refxtce");
        metaCommandProcessor = new MetaCommandProcessor(new ProcessorData("test", mdb, new ProcessorConfig()));
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void testBinaryLeadingSize() {
        byte[] buf = new byte[] { 0x03, 0x01, 0x02, 0x03 };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/RefXtce/packet1"));
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(mdb.getParameter("/RefXtce/param1"));
        assertEquals("010203", StringConverter.arrayToHexString(pv.getEngValue().getBinaryValue()));
    }

    @Test
    public void testFixedSizeArray() {
        byte[] buf = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/RefXtce/packet3"));
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(1, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(mdb.getParameter("/RefXtce/param8"));
        ArrayValue av = (ArrayValue) pv.getEngValue();
        assertEquals(4, av.flatLength());
        assertEquals(0x0102, av.getElementValue(0).getUint32Value());
        assertEquals(0x0304, av.getElementValue(1).getUint32Value());
        assertEquals(0x0506, av.getElementValue(2).getUint32Value());
        assertEquals(0x0708, av.getElementValue(3).getUint32Value());
    }

    @Test
    public void testNumericStringEncoding() {
        byte[] buf = new byte[] { '1', '0', '0', 0, 0, 0,
                '-', '3', '.', '1', '4', 0 };
        ContainerProcessingResult cpr = processPacket(buf, mdb.getSequenceContainer("/RefXtce/packet4"));
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv9 = pvl.getFirstInserted(mdb.getParameter("/RefXtce/param9"));
        assertEquals("100", pv9.getRawValue().getStringValue());
        assertEquals(100, pv9.getEngValue().getUint32Value());
        ParameterValue pv10 = pvl.getFirstInserted(mdb.getParameter("/RefXtce/param10"));
        assertEquals("-3.14", pv10.getRawValue().getStringValue());
        assertEquals(-3.14, pv10.getEngValue().getFloatValue(), 1e-5);
    }

    private ContainerProcessingResult processPacket(byte[] buf, SequenceContainer sc) {
        return extractor.processPacket(buf, now, now, 0, sc);
    }
}
