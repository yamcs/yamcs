package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

public class CcsdsGreenBookTmTest {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void setupTimeencoding() {
        TimeEncoding.setUp();
        mdb = MdbFactory.createInstanceByConfig("ccsds-green-book");
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
    }

    @Test
    public void testIncludeCondition() throws Exception {
        byte[] buf = new byte[] { 24, (byte) 0x01, 0, 0, // Header1 SecH -> no secondary header
                0, 0, // PBATMTEMP
                0, 0 }; // PSWHLTIMFLG
        extractor.provideAll();
        ContainerProcessingResult cpr = processPacket(buf);
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(6, pvl.size());
        ParameterValue pvSech = pvl.getFirstInserted(mdb.getParameter("/SpaceVehicle/SecH"));
        assertEquals(0, pvSech.getRawValue().getUint32Value());
    }

    @Test
    public void test1() throws Exception {
        byte[] buf = new byte[] { 24, (byte) 0x81, 0, 12, // Header1
                0x16, (byte) 0x92, 0x5E, (byte) 0x80, // Seconds
                0, 50, // Milliseconds
                0, 0, // PBATMTEMP
                0, 0 }; // PSWHLTIMFLG
        extractor.provideAll();
        ContainerProcessingResult cpr = processPacket(buf);

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(8, pvl.size());
        ParameterValue pvSec = pvl.getFirstInserted(mdb.getParameter("/SpaceVehicle/Seconds"));
        assertEquals("1970-01-01T00:00:00.000Z", pvSec.getEngValue().toString());

        ParameterValue pvMillisec = pvl.getFirstInserted(mdb.getParameter("/SpaceVehicle/MilliSeconds"));
        assertEquals("1970-01-01T00:00:00.050Z", pvMillisec.getEngValue().toString());
    }

    @Test
    public void test2() throws Exception {
        Parameter psec = mdb.getParameter("/SpaceVehicle/MilliSeconds");

        byte[] buf = new byte[] { 24, (byte) 0x81, 0, 12, // Header1
                0x16, (byte) 0x92, 0x5E, (byte) 0x80, // Seconds
                0, 50, // Milliseconds
                0, 0, // PBATMTEMP
                0, 0 }; // PSWHLTIMFLG

        extractor.startProviding(psec);
        ContainerProcessingResult cpr = processPacket(buf);

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(6, pvl.size());
        ParameterValue pvSec = pvl.getFirstInserted(mdb.getParameter("/SpaceVehicle/Seconds"));
        assertEquals("1970-01-01T00:00:00.000Z", pvSec.getEngValue().toString());

        ParameterValue pvMillisec = pvl.getFirstInserted(psec);
        assertEquals("1970-01-01T00:00:00.050Z", pvMillisec.getEngValue().toString());
    }

    private ContainerProcessingResult processPacket(byte[] buf) {
        return extractor.processPacket(buf, now, now, 0);
    }
}
