package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AncillaryData;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.ValueEnumeration;

public class BogusSat2Test {
    static Mdb mdb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.createInstanceByConfig("BogusSAT2");
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
    }

    @Test
    public void test1() {
        byte[] buf = new byte[] { 0x08, 0x23, // CCSDS_Packet_ID {version=0, type = 0, SecHdrFlag = 1, apid=0x23
                (byte) 0xC0, 0x56, // CCSDS_Packet_Sequence {GroupFlags=3, count = 0x56}
                0, 5, // length 5
                0x35, 0x10, 0x20, 0x03, 0x05, // PUS_Data_Field_Header {Spare1 = 0, Version=3, Spare4=5, Service = 0x10,
                                              // Subservice=0x20, SeqCount = 3, Destination=5}
                0, 0 };
        ContainerProcessingResult cpr = processPacket(buf);
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(4, pvl.size());
        ParameterValue pushdr = pvl.getFirstInserted(mdb.getParameter("/BogusSAT/PUS_Data_Field_Header"));
        assertTrue(pushdr.getEngValue() instanceof AggregateValue);
        AggregateValue v = (AggregateValue) pushdr.getEngValue();
        assertEquals(3, v.getMemberValue("SeqCount").getUint32Value());
    }

    @Test
    public void test2() {
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        byte[] x = new byte[4];
        ByteBuffer.wrap(x).putFloat(15);
        byte[] buf = new byte[] { 0x00, 0x02, // CCSDS_Packet_ID {version=0, type = 0, SecHdrFlag = 0, apid=2
                (byte) 0xC0, 0x56, // CCSDS_Packet_Sequence {GroupFlags=3, count = 0x56}
                0, 5, // length 5
                0x0, 0x1, 0x1, 0x1, 0x0, // Solar_Array_Voltage_1_State=OFF, Voltage_1(not present), Voltage_2_State=1,
                                         // Voltage_2=16
                x[0], x[1], x[2], x[3], // Battery_Voltage
                x[0], x[1], x[2], x[3] }; // Battery Current
        ContainerProcessingResult cpr = processPacket(buf);
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(8, pvl.size());
        assertNull(pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/BusElectronics/Solar_Array_Voltage_1")));
        assertNotNull(pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/BusElectronics/Solar_Array_Voltage_2")));
        ParameterValue pv = pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/BusElectronics/Battery_Current"));
        assertEquals(15.0, pv.getEngValue().getFloatValue(), 1e-5);
    }

    @Test
    public void test6() {
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        byte[] x = new byte[4];
        float xfloat = 16;
        ByteBuffer.wrap(x).putFloat(xfloat);
        byte[] buf = new byte[] { 0x00, 0x06, // CCSDS_Packet_ID {version=0, type = 0, SecHdrFlag = 0, apid=6
                (byte) 0xC0, 0x56, // CCSDS_Packet_Sequence {GroupFlags=3, count = 0x56}
                0, 5, // length 5
                0x1, // Payload_1_State
                0x1, // Solar_Array_Voltage_1_State
                // Container3
                0x0, 0x0, 0x0, 0x5, // enum_binary
                x[0], x[1], x[2], x[3], // enum_float32
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0, // enum_float64
                0x1, 0x2, // enum_int16_twoscomp
                0x1, 0x2, // enum_int16_onescomp

                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0, // PAYLOAD_ANTENNA_POINTING_ARRAY1 (array of 9 doubles)
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0, 0, 0, 0, 1,
                0x0,
                (byte) 0xC0, 0x0, 0, 0, // PAYLOAD_ANTENNA_POINTING_ARRAY2 (array of 9 floats)
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,

                (byte) 0xC0, 0x0, 0, 0, // PAYLOAD_ANTENNA_POINTING_ARRAY2 (array of 9 doubles) repeated
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) 0xC0, 0x0, 0, 0,
                (byte) x[0], x[1], x[2], x[3],
                // IncludedContainer1
                (byte) 0xC0, 0x0, 0, 1, // Basic_uint32
                (byte) 0x80, 0x0, 0, 6, // Basic_int32_signmag
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFA, // Basic_int32_twoscomp
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF9, // Basic_int32_onescomp
        };

        ContainerProcessingResult cpr = processPacket(buf);
        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(17, pvl.size());

        ParameterValue pv = pvl
                .getFirstInserted(mdb.getParameter("/BogusSAT/SC001/Payload1/PAYLOAD_ANTENNA_POINTING_ARRAY1"));
        ArrayValue v = (ArrayValue) pv.getEngValue();
        assertEquals(9, v.flatLength());
        assertEquals(-2.0, v.getElementValue(0).getDoubleValue(), 1E-5);

        pv = pvl.getLastInserted(mdb.getParameter("/BogusSAT/SC001/Payload1/PAYLOAD_ANTENNA_POINTING_ARRAY2"));
        v = (ArrayValue) pv.getEngValue();
        assertEquals(9, v.flatLength());
        assertEquals(xfloat, v.getElementValue(8).getFloatValue(), 1E-5);

        pv = pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/Payload1/Basic_int32_signmag"));
        assertEquals(-6, pv.getRawValue().getSint32Value());

        pv = pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/Payload1/Basic_int32_twoscomp"));
        assertEquals(-6, pv.getRawValue().getSint32Value());

        pv = pvl.getFirstInserted(mdb.getParameter("/BogusSAT/SC001/Payload1/Basic_int32_onescomp"));
        assertEquals(-6, pv.getRawValue().getSint32Value());

    }

    @Test
    public void testAbstractMetaCommand() {
        MetaCommand mc = mdb.getMetaCommand("/BogusSAT/CCSDSTelecommand");
        assertTrue(mc.isAbstract());
    }

    @Test
    public void testAggregatePtypeNameDescription() {
        AggregateParameterType pt = (AggregateParameterType) mdb
                .getParameterType("/BogusSAT/SC001/Onboard_Processor_Config/Config_Log_Levels_Type");

        assertEquals("Test Long Description", pt.getLongDescription());
        List<AncillaryData> l = pt.getAncillaryData();
        assertEquals(1, l.size());
        AncillaryData ad = l.get(0);
        assertEquals("TEST_ANC_DATA", ad.getName());
        assertEquals("1", ad.getValue());
    }

    @Test
    public void testEnumDescription() {
        EnumeratedParameterType ept = (EnumeratedParameterType) mdb
                .getParameterType("/BogusSAT/LOG_MSGS/ERRORCODE_Type");
        ValueEnumeration ve = ept.enumValue(123l);
        assertEquals("Detailed description of error case A", ve.getDescription());
    }

    @Test
    public void testArchivePartition() {
        SequenceContainer seq = mdb.getSequenceContainer("/BogusSAT/SC001/Payload1/IncludedContainer1");
        assertFalse(seq.useAsArchivePartition());
    }

    private ContainerProcessingResult processPacket(byte[] buf) {
        return extractor.processPacket(buf, now, now, 0);
    }
}
