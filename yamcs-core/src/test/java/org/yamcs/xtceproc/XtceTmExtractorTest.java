package org.yamcs.xtceproc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.yamcs.RefMdbPacketGenerator.pFixedStringPara1_3_1;
import static org.yamcs.RefMdbPacketGenerator.pFixedStringPara1_3_2;
import static org.yamcs.RefMdbPacketGenerator.pFixedStringPara1_3_7;
import static org.yamcs.RefMdbPacketGenerator.pIntegerPara2_1;
import static org.yamcs.RefMdbPacketGenerator.pIntegerPara2_2;
import static org.yamcs.RefMdbPacketGenerator.pPrependedSizeStringPara1_3_5;
import static org.yamcs.RefMdbPacketGenerator.pPrependedSizeStringPara1_3_6;
import static org.yamcs.RefMdbPacketGenerator.pStringFloatFSBPara1_4_4;
import static org.yamcs.RefMdbPacketGenerator.pStringFloatPSPara1_4_5;
import static org.yamcs.RefMdbPacketGenerator.pStringFloatTSSCPara1_4_3;
import static org.yamcs.RefMdbPacketGenerator.pStringIntFixedPara1_5_1;
import static org.yamcs.RefMdbPacketGenerator.pStringIntPrePara1_5_4;
import static org.yamcs.RefMdbPacketGenerator.pStringIntStrPara1_5_5;
import static org.yamcs.RefMdbPacketGenerator.pStringIntTermPara1_5_3;
import static org.yamcs.RefMdbPacketGenerator.pTerminatedStringPara1_3_3;
import static org.yamcs.RefMdbPacketGenerator.pTerminatedStringPara1_3_4;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

public class XtceTmExtractorTest {

    private static XtceDb xtcedb;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        XtceDbFactory.reset();
        xtcedb = XtceDbFactory.createInstanceByConfig("refmdb");
    }

    @Test
    public void testPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        Parameter p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        ParameterValue pv = received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1, pv.getEngValue().getUint32Value());

        p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_6");
        pv = received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1_6, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_7"));
        assertEquals(tmGenerator.pIntegerPara1_1_7, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_8"));
        assertEquals(tmGenerator.pIntegerPara1_1_8, pv.getEngValue().getUint64Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringPara1_1_5"));
        assertEquals(tmGenerator.pStringPara1_1_5, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_2() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_2();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_1, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_2, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_3"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_3, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_1 * 0.0001672918, pv.getEngValue().getFloatValue(), 1e-8);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_2, pv.getEngValue().getFloatValue(), 0);

        assertEquals((long) (1500 * 1.9), pv.getExpireMills());
    }

    @Test
    public void testPKT1_3StringStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_3();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(13, received.size());

        // Fixed size strings
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_1"));
        assertEquals(pFixedStringPara1_3_1, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_2"));
        assertEquals(pFixedStringPara1_3_2, pv.getEngValue().getStringValue());

        // Terminated strings
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_3"));
        assertEquals(pTerminatedStringPara1_3_3, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_4"));
        assertEquals(pTerminatedStringPara1_3_4, pv.getEngValue().getStringValue());

        // Prepended size strings

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_5"));
        assertEquals(pPrependedSizeStringPara1_3_5, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_6"));
        assertEquals(pPrependedSizeStringPara1_3_6, pv.getEngValue().getStringValue());

        // Final fixed size string of large space
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_7"));
        assertEquals(pFixedStringPara1_3_7, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_4StringFloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        // Check all the parameters have been parsed
        assertEquals(12, received.size());
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSPara1_4_1"));
        assertEquals(Float.parseFloat(tmGenerator.pStringFloatFSPara1_4_1), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals(Float.parseFloat(tmGenerator.pStringFloatTSCPara1_4_2), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.removeLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals(Float.parseFloat(pStringFloatTSSCPara1_4_3), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals(Float.parseFloat(pStringFloatTSSCPara1_4_3), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatPSPara1_4_5"));
        assertEquals(Float.parseFloat(pStringFloatPSPara1_4_5), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSBPara1_4_4"));
        assertEquals(Float.parseFloat(pStringFloatFSBPara1_4_4), pv.getEngValue().getFloatValue(), 0.0001);
    }

    @Test
    public void testPKT1_4InvalidStringFloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        tmGenerator.pStringFloatTSCPara1_4_2 = "invalidfloat";
        byte[] bb = tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        // Check all the parameters have been parsed
        assertEquals(12, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    @Test
    public void testPKT1_5StringIntStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_5();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals(11, received.size());

        // Verify correct names
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntFixedPara1_5_1"));
        assertEquals(Integer.parseInt(pStringIntFixedPara1_5_1), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals(Integer.parseInt(tmGenerator.pStringIntTermPara1_5_2), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_3"));
        assertEquals(Integer.parseInt(pStringIntTermPara1_5_3), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntPrePara1_5_4"));
        assertEquals(Integer.parseInt(pStringIntPrePara1_5_4), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntStrPara1_5_5"));
        assertEquals(Integer.parseInt(pStringIntStrPara1_5_5), pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT1_5InvalidStringIntStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        tmGenerator.pStringIntTermPara1_5_2 = "invalidint";

        byte[] bb = tmGenerator.generate_PKT1_5();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals(11, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    @Test
    public void testPKT1_9BooleanValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_9();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        assertEquals(10, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_1"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_2"));
        assertEquals(0, pv.getRawValue().getUint32Value());
        assertEquals(false, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_3"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_4"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testContainerSubscriptionPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1"));

        byte[] bb = tmGenerator.generate_PKT1_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        List<ContainerExtractionResult> received = tmExtractor.getContainerResult();
        assertEquals(2, received.size());
        assertEquals("/REFMDB/ccsds-default", received.get(0).getContainer().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/PKT1", received.get(1).getContainer().getQualifiedName());
        assertEquals("description 1", received.get(1).getContainer().getLongDescription());

        String pkt11 = byteBufferToHexString(ByteBuffer.wrap(bb));

        // First example, access the received PKT1, as its PKT11 instantiation
        ContainerExtractionResult pkt1Result = received.get(1);
        ByteBuffer pkt1Buffer = ByteBuffer.wrap(pkt1Result.getContainerContent());
        assertEquals(0, pkt1Buffer.position());
        String pkt1 = byteBufferToHexString(pkt1Buffer);
        assertTrue(pkt11.equals(pkt1));

        // Second example, access only parameters in XTCE PKT1 definition
        pkt1Buffer.position(pkt1Result.getLocationInContainerInBits() / 8);
        pkt1Buffer.limit(pkt1Buffer.position() + tmGenerator.pkt1Length);
        String pkt1b = byteBufferToHexString(pkt1Buffer.slice());
        assertTrue(pkt11.contains(pkt1b));
        assertEquals(tmGenerator.headerLength, pkt11.indexOf(pkt1b) / 2);
    }

    @Test
    public void testPKT1_7FloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_7();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        assertEquals(11, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FloatPara1_7_1"));
        assertEquals(-14.928, pv.getEngValue().getFloatValue(), 1e-5);

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_2"));
        assertEquals(6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_3"));
        assertEquals(-6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_4"));
        assertEquals(6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_5"));
        assertEquals(-6, pv.getEngValue().getSint32Value());
    }

    @Test
    public void testPKT1_11_longuint32() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_11();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_11_1"));
        assertEquals(tmGenerator.pIntegerPara1_11_1_unsigned_value / 2,
                pv.getEngValue().getUint32Value() & 0xFFFFFFFFL);
    }

    @Test
    public void testPKT1_12_stringenum() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_12();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals("value1", pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_12_invalidstringenum() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        tmGenerator.pStringEnumPara1_12_1 = "invalidlong";
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT1_12();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    @Test
    public void testPKT3_dynamicSize() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT3();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(17, received.size());
        assertEquals(2, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1")).getRawValue()
                .getUint32Value());
        assertEquals(3, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue()
                .getUint32Value());
        assertEquals(4, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue()
                .getUint32Value());
        assertEquals(5, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(6, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(61, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2_1")).getRawValue()
                .getUint32Value());
        assertEquals(7, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(8, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(9, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(10, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(11, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue()
                .getUint32Value());
        assertEquals(12, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue()
                .getUint32Value());
        assertEquals(13, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue()
                .getUint32Value());
        assertEquals(14, received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue()
                .getUint32Value());
    }

    @Test
    public void testProcessPacket_startContainer() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT2();
        SequenceContainer startContainer = xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT2");
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime(),
                startContainer);

        ParameterValueList received = tmExtractor.getParameterResult();

        assertEquals(2, received.size());
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_1"));
        assertEquals(pIntegerPara2_1, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_2"));
        assertEquals(pIntegerPara2_2, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT1_List() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT1_List();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_List", container.getName());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_AND() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT1_AND();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND", container.getName());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_OR() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        // first condition
        ByteBuffer bb = tmGenerator.generate_PKT1_OR_1();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR", container.getName());
        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

    }

    @Test
    public void testPKT1_PKT1_AND_OR() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        // first condition
        ByteBuffer bb = tmGenerator.generate_PKT1_AND_OR_1();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // second condition
        bb = tmGenerator.generate_PKT1_AND_OR_2();
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_OR_AND() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        // or condition 1
        ByteBuffer bb = tmGenerator.generate_PKT1(0, 0, (short) 1);
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // or condition 3
        bb = tmGenerator.generate_PKT1(0, 0, (short) 3);
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // does not match
        bb = tmGenerator.generate_PKT1(0, 0, (short) 4);
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1", container.getName());
        received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_RANGE() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        // in range ]0xA, 0xC[
        ByteBuffer bb = tmGenerator.generate_PKT1(0, 0, (short) 11);
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        List<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_RANGE", container.getName());
        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // out of range
        bb = tmGenerator.generate_PKT1(0, 0, (short) 12);
        tmExtractor.processPacket(bb.array(), TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1", container.getName());
        received = tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT4_JavaAlgo() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT4();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(4, received.size());

        ParameterValue pv2 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FloatPara4_1"));
        assertNotNull(pv2);

        assertEquals(3.0, pv2.getEngValue().getFloatValue(), 1e-2);
    }

    @Test
    public void testPKT5BinaryRawValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT5();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(6, received.size());

        ParameterValue pv1 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedBinary1"));
        assertTrue(Arrays.equals(RefMdbPacketGenerator.pFixedBinary1, pv1.getRawValue().getBinaryValue()));

        ParameterValue pv2 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeBinary1"));
        byte[] psb = RefMdbPacketGenerator.pPrependedSizeBinary1;
        // the first byte is the size
        assertTrue(Arrays.equals(Arrays.copyOfRange(psb, 1, psb.length), pv2.getRawValue().getBinaryValue()));

        ParameterValue pv3 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/CustomBinaryEncoding1"));
        assertTrue(Arrays.equals(MyDecoder.fixedValue, pv3.getRawValue().getBinaryValue()));
    }

    @Test
    public void testPKT6_TimePara() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT6();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(5, received.size());

        ParameterValue pv1 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TimePara6_1"));
        assertEquals("1980-01-06T00:00:01.000Z", TimeEncoding.toString(pv1.getEngValue().getTimestampValue()));

        ParameterValue pv2 = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TimePara6_2"));
        assertEquals("1980-01-06T00:00:01.500Z", TimeEncoding.toString(pv2.getEngValue().getTimestampValue()));
    }

    @Test
    public void testPKT7_Aggrgate() throws ConfigurationException {

        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT7();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(4, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/aggregate_para1"));
        AggregateValue aggrv = (AggregateValue) pv.getEngValue();
        assertEquals(tmGenerator.paggr1_member1, aggrv.getMemberValue("member1").getUint32Value());
        assertEquals(tmGenerator.paggr1_member2, aggrv.getMemberValue("member2").getUint32Value());
        assertEquals(tmGenerator.paggr1_member3, aggrv.getMemberValue("member3").getFloatValue(), 1e-5);
    }

    @Test
    public void testPKT8_ArrayOfAggrgate() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT8();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();
        assertEquals(5, received.size());

        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/array_para1"));
        ArrayValue arrv = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { tmGenerator.para_pkt8_count }, arrv.getDimensions());
        for (int i = 0; i < tmGenerator.para_pkt8_count; i++) {
            AggregateValue aggrv = (AggregateValue) arrv.getElementValue(i);
            assertEquals(i, aggrv.getMemberValue("member1").getUint32Value());
            assertEquals(2 * i, aggrv.getMemberValue("member2").getUint32Value());
            assertEquals(i / 2.0, aggrv.getMemberValue("member3").getFloatValue(), 1e-5);
        }
        assertEquals(pv.getRawValue(), pv.getEngValue());
    }

    @Test
    public void testPKT1_10ContextCalibration() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        Parameter p10_3 = xtcedb.getParameter("/REFMDB/SUBSYS1/FloatPara1_10_3");

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(p10_3);

        byte[] bb = tmGenerator.generate_PKT1_10(0, 1, 30);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ParameterValue pv1 = tmExtractor.getParameterResult().getLastInserted(p10_3);
        assertEquals(30, pv1.getRawValue().getFloatValue(), 1E-10);
        assertEquals(30, pv1.getEngValue().getFloatValue(), 1E-10);

        // now with the spline calibrator
        bb = tmGenerator.generate_PKT1_10(0, 0, 30);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValue pv2 = tmExtractor.getParameterResult().getLastInserted(p10_3);
        assertEquals(30, pv2.getRawValue().getFloatValue(), 1E-10);
        assertEquals(3, pv2.getEngValue().getFloatValue(), 1E-10);
    }

    @Test
    public void testPKT6_TimeParaPartialExtraction() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        Parameter p6_2 = xtcedb.getParameter("/REFMDB/SUBSYS1/TimePara6_2");

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(p6_2);

        byte[] bb = tmGenerator.generate_PKT6();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received = tmExtractor.getParameterResult();

        ParameterValue pv2 = received.getLastInserted(p6_2);
        assertEquals("1980-01-06T00:00:01.500Z", TimeEncoding.toString(pv2.getEngValue().getTimestampValue()));
    }

    @Test
    public void testPKT9_IndirectPara() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT9((short) 1, 0x01020304);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ParameterValueList received = tmExtractor.getParameterResult();
        ParameterValue pv = received.getFirstInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/obpara1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
       
        bb = tmGenerator.generate_PKT9((short) 2, 0x02030405);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        received = tmExtractor.getParameterResult();
        assertNull(received.getFirstInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/obpara1")));
        pv = received.getFirstInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/obpara2"));
        assertEquals(0x02030405, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT9_IndirectParaPartialExtraction() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        Parameter p = xtcedb.getParameter("/REFMDB/SUBSYS1/obpara1");
        tmExtractor.startProviding(p);

        byte[] bb = tmGenerator.generate_PKT9((short) 1, 0x01020304);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ParameterValueList received = tmExtractor.getParameterResult();
        ParameterValue pv = received.getFirstInserted(p);
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }
    
    void printParaList(ParameterValueList pvl) {
        System.out.println(String.format("%-30s %10s %10s", "name", "eng", "raw"));
        System.out.println(String.format("----------------------------------------------------"));
        for (ParameterValue pv : pvl) {
            System.out.println(String.format("%-30s %10s %10s", pv.getParameter().getName(),
                    pv.getEngValue().toString(), pv.getRawValue().toString()));
        }
    }

    private String byteBufferToHexString(ByteBuffer bb) {
        bb.mark();
        StringBuilder sb = new StringBuilder();
        while (bb.hasRemaining()) {
            String s = Integer.toString(bb.get() & 0xFF, 16);
            if (s.length() == 1)
                sb.append("0");
            sb.append(s.toUpperCase());
        }
        bb.reset();
        return sb.toString();
    }

    public static class MyDecoder extends AbstractDataDecoder {
        static byte[] fixedValue = new byte[] { 0x02, 0x03, 0x1b };

        public MyDecoder(Algorithm a, AlgorithmExecutionContext c, Double x) {

        }

        @Override
        public Value extractRaw(DataEncoding de, BitBuffer buf) {
            buf.setPosition(buf.getPosition() + 32);
            return ValueUtility.getBinaryValue(fixedValue);
        }
    }
}
