package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class CcsdsGreenBookTmTest {
    @BeforeClass
    public static void setupTimeencoding() {
        TimeEncoding.setUp();
    }
    @Test
    public void test1() throws Exception {
        org.yamcs.LoggingUtils.enableLogging();
        XtceDb db = XtceDbFactory.createInstanceByConfig("ccsds-green-book");
        XtceTmExtractor extractor = new XtceTmExtractor(db);
        extractor.provideAll();
        long now = TimeEncoding.getWallclockTime();
        byte[] buf = new byte[] {0, 0, 0, 0, //Header1
                        0x16, (byte) 0x92, 0x5E, (byte)0x80, //Seconds
                        0, 50, //Milliseconds
                        0, 0, //PBATMTEMP
                        0, 0}; //PSWHLTIMFLG
        extractor.processPacket(buf, now, now);
        ParameterValueList pvl = extractor.getParameterResult();
        ParameterValue pvSec = pvl.getFirstInserted(db.getParameter("/SpaceVehicle/Seconds"));
        assertEquals("1970-01-01T00:00:00.000Z", pvSec.getEngValue().toString());
        
        ParameterValue pvMillisec = pvl.getFirstInserted(db.getParameter("/SpaceVehicle/MilliSeconds"));
        assertEquals("1970-01-01T00:00:00.050Z", pvMillisec.getEngValue().toString());
    }
    
}
