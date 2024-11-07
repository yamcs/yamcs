package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.tests.RefMdbPacketGenerator.pFixedStringPara1_3_1;
import static org.yamcs.tests.RefMdbPacketGenerator.pFixedStringPara1_3_2;
import static org.yamcs.tests.RefMdbPacketGenerator.pFixedStringPara1_3_7;
import static org.yamcs.tests.RefMdbPacketGenerator.pIntegerPara2_1;
import static org.yamcs.tests.RefMdbPacketGenerator.pIntegerPara2_2;
import static org.yamcs.tests.RefMdbPacketGenerator.pPrependedSizeStringPara1_3_5;
import static org.yamcs.tests.RefMdbPacketGenerator.pPrependedSizeStringPara1_3_6;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringFloatFSBPara1_4_4;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringFloatPSPara1_4_5;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringFloatTSSCPara1_4_3;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringIntFixedPara1_5_1;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringIntPrePara1_5_4;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringIntStrPara1_5_5;
import static org.yamcs.tests.RefMdbPacketGenerator.pStringIntTermPara1_5_3;
import static org.yamcs.tests.RefMdbPacketGenerator.pTerminatedStringPara1_3_3;
import static org.yamcs.tests.RefMdbPacketGenerator.pTerminatedStringPara1_3_4;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.FloatValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.tests.RefMdbPacketGenerator;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;

public class XtceTmExtractorTest {

    private static Mdb mdb;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        mdb = MdbFactory.createInstanceByConfig("refmdb");
    }

    @Test
    public void testPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_1());
        Parameter p = mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        ParameterValue pv = received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1, pv.getEngValue().getUint32Value());

        p = mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_6");
        pv = received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1_6, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_7"));
        assertEquals(tmGenerator.pIntegerPara1_1_7, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_8"));
        assertEquals(tmGenerator.pIntegerPara1_1_8, pv.getEngValue().getUint64Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringPara1_1_5"));
        assertEquals(tmGenerator.pStringPara1_1_5, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_2() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_2());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_1, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_2, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_3"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_3, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_1 * 0.0001672918, pv.getEngValue().getFloatValue(), 1e-8);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_2, pv.getEngValue().getFloatValue(), 0);

        assertEquals((long) (1500 * 1.9), pv.getExpireMills());
    }

    @Test
    public void testPKT1_3StringStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_3());
        assertEquals(13, received.size());

        // Fixed size strings
        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_1"));
        assertEquals(pFixedStringPara1_3_1, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_2"));
        assertEquals(pFixedStringPara1_3_2, pv.getEngValue().getStringValue());

        // Terminated strings
        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_3"));
        assertEquals(pTerminatedStringPara1_3_3, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_4"));
        assertEquals(pTerminatedStringPara1_3_4, pv.getEngValue().getStringValue());

        // Prepended size strings

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_5"));
        assertEquals(pPrependedSizeStringPara1_3_5, pv.getEngValue().getStringValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_6"));
        assertEquals(pPrependedSizeStringPara1_3_6, pv.getEngValue().getStringValue());

        // Final fixed size string of large space
        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_7"));
        assertEquals(pFixedStringPara1_3_7, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_4StringFloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT14());

        // Check all the parameters have been parsed
        assertEquals(12, received.size());
        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatFSPara1_4_1"));
        assertEquals(Float.parseFloat(tmGenerator.pStringFloatFSPara1_4_1), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals(Float.parseFloat(tmGenerator.pStringFloatTSCPara1_4_2), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.removeLast(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals(Float.parseFloat(pStringFloatTSSCPara1_4_3), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals(Float.parseFloat(pStringFloatTSSCPara1_4_3), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatPSPara1_4_5"));
        assertEquals(Float.parseFloat(pStringFloatPSPara1_4_5), pv.getEngValue().getFloatValue(), 0.0001);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatFSBPara1_4_4"));
        assertEquals(Float.parseFloat(pStringFloatFSBPara1_4_4), pv.getEngValue().getFloatValue(), 0.0001);
    }

    @Test
    public void testPKT1_4InvalidStringFloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        tmGenerator.pStringFloatTSCPara1_4_2 = "invalidfloat";

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT14());

        // Check all the parameters have been parsed
        assertEquals(12, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    @Test
    public void testPKT1_5StringIntStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_5());
        // Check all the parameters have been parsed
        assertEquals(11, received.size());

        // Verify correct names
        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntFixedPara1_5_1"));
        assertEquals(Integer.parseInt(pStringIntFixedPara1_5_1), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals(Integer.parseInt(tmGenerator.pStringIntTermPara1_5_2), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_3"));
        assertEquals(Integer.parseInt(pStringIntTermPara1_5_3), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntPrePara1_5_4"));
        assertEquals(Integer.parseInt(pStringIntPrePara1_5_4), pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntStrPara1_5_5"));
        assertEquals(Integer.parseInt(pStringIntStrPara1_5_5), pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT1_5InvalidStringIntStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        tmGenerator.pStringIntTermPara1_5_2 = "invalidint";

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_5());
        // Check all the parameters have been parsed
        assertEquals(11, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    @Test
    public void testPKT1_9BooleanValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_9());

        assertEquals(10, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_1"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_2"));
        assertEquals(0, pv.getRawValue().getUint32Value());
        assertEquals(false, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_3"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_4"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testContainerSubscriptionPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        ProcessorConfig pconf = new ProcessorConfig();
        pconf.setSubscribeContainerArchivePartitions(false);
        ProcessorData pdata = new ProcessorData("XTCEPROC", mdb, pconf);

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb, pdata);
        tmExtractor.startProviding(mdb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1"));

        byte[] bb = tmGenerator.generate_PKT1_1();
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb);

        List<ContainerExtractionResult> received = cpr.containers;
        assertEquals(2, received.size());
        assertEquals("/REFMDB/ccsds-default", received.get(0).getContainer().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/PKT1", received.get(1).getContainer().getQualifiedName());
        assertEquals("description 1", received.get(1).getContainer().getShortDescription());
        assertEquals("long description of pkt1", received.get(1).getContainer().getLongDescription());

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

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_7());

        assertEquals(11, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FloatPara1_7_1"));
        assertEquals(-14.928, pv.getEngValue().getFloatValue(), 1e-5);

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_2"));
        assertEquals(6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_3"));
        assertEquals(-6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_4"));
        assertEquals(6, pv.getEngValue().getSint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_7_5"));
        assertEquals(-6, pv.getEngValue().getSint32Value());
    }

    @Test
    public void testPKT1_11_longuint32() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_11());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_11_1"));
        assertEquals(tmGenerator.pIntegerPara1_11_1_unsigned_value / 2,
                pv.getEngValue().getUint32Value() & 0xFFFFFFFFL);
    }

    @Test
    public void testPKT1_12_stringenum() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_12());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals("value1", pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT1_12_invalidstringenum() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        tmGenerator.pStringEnumPara1_12_1 = "invalidlong";

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT1_12());
        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    private void testPKT10(String rawValue, Boolean expectedEngineering) {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        tmGenerator.pStringBooleanPara10_1 = rawValue;

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT10());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/StringBooleanPara10_1"));
        assertEquals(tmGenerator.pStringBooleanPara10_1, pv.getRawValue().getStringValue());
        assertTrue(expectedEngineering == pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testPKT10_values() {
        testPKT10("True", true);
        testPKT10("False", false);
        testPKT10("false", false);
        testPKT10("true", true);
        testPKT10("0", false);
        testPKT10("1", true);
        testPKT10("", false);
        testPKT10("arbitrary content", true);
    }

    @Test
    public void testPKT3_dynamicSize() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        ParameterValueList received = extractParameters(tmGenerator.generate_PKT3());

        assertEquals(19, received.size());
        assertEquals(2, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1")).getRawValue()
                .getUint32Value());
        assertEquals(3, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue()
                .getUint32Value());
        assertEquals(4, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue()
                .getUint32Value());
        assertEquals(5, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(6, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(61, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para2_1")).getRawValue()
                .getUint32Value());
        assertEquals(7, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(8, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(9, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue()
                .getUint32Value());
        assertEquals(10, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue()
                .getUint32Value());
        assertEquals(11, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue()
                .getUint32Value());
        assertEquals(12, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue()
                .getUint32Value());
        assertEquals(13, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue()
                .getUint32Value());
        assertEquals(14, received.removeFirst(mdb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue()
                .getUint32Value());
    }

    @Test
    public void testProcessPacket_startContainer() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        SequenceContainer startContainer = mdb.getSequenceContainer("/REFMDB/SUBSYS1/PKT2");
        ContainerProcessingResult cpr = processPacket(tmExtractor, tmGenerator.generate_PKT2(), startContainer);

        ParameterValueList received = cpr.getParameterResult();

        assertEquals(2, received.size());
        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_1"));
        assertEquals(pIntegerPara2_1, pv.getEngValue().getUint32Value());

        pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_2"));
        assertEquals(pIntegerPara2_2, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT1_List() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT1_List();
        ContainerProcessingResult result = processPacket(tmExtractor, bb.array());

        List<ContainerExtractionResult> containers = result.containers;
        ;
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_List", container.getName());

        ParameterValueList received = result.getTmParams();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_AND() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        ByteBuffer bb = tmGenerator.generate_PKT1_AND();
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb.array());

        List<ContainerExtractionResult> containers = cpr.getContainerResult();
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND", container.getName());

        ParameterValueList received = cpr.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_OR() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        // first condition
        ByteBuffer bb = tmGenerator.generate_PKT1_OR_1();
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb.array());
        List<ContainerExtractionResult> containers = cpr.getContainerResult();
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR", container.getName());
        ParameterValueList received = cpr.getParameterResult();
        assertEquals(received.size(), 6);

    }

    @Test
    public void testPKT1_PKT1_AND_OR() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        // first condition
        ByteBuffer bb = tmGenerator.generate_PKT1_AND_OR_1();
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb.array());
        List<ContainerExtractionResult> containers = cpr.getContainerResult();
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        ParameterValueList received = cpr.getParameterResult();
        assertEquals(received.size(), 6);

        // second condition
        bb = tmGenerator.generate_PKT1_AND_OR_2();
        cpr = processPacket(tmExtractor, bb.array());
        containers = cpr.getContainerResult();
        container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        received = cpr.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_OR_AND() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        // or condition 1
        byte[] bb = tmGenerator.generate_PKT1(0, 0, (short) 1);
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb);
        List<ContainerExtractionResult> containers = cpr.getContainerResult();
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        ParameterValueList received = cpr.getParameterResult();
        assertEquals(received.size(), 6);

        // or condition 3
        bb = tmGenerator.generate_PKT1(0, 0, (short) 3);
        cpr = processPacket(tmExtractor, bb);
        containers = cpr.getContainerResult();
        container = cpr.getContainerResult().get(containers.size() - 1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        received = cpr.getParameterResult();
        assertEquals(received.size(), 6);

        // does not match
        bb = tmGenerator.generate_PKT1(0, 0, (short) 4);
        cpr = processPacket(tmExtractor, bb);
        containers = cpr.getContainerResult();
        container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1", container.getName());
        received = cpr.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_RANGE() {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        // in range ]0xA, 0xC[
        byte[] bb = tmGenerator.generate_PKT1(0, 0, (short) 11);
        ContainerProcessingResult cpr = processPacket(tmExtractor, bb);
        List<ContainerExtractionResult> containers = cpr.getContainerResult();
        SequenceContainer container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1_RANGE", container.getName());
        ParameterValueList received = cpr.getParameterResult();
        assertEquals(received.size(), 6);

        // out of range
        bb = tmGenerator.generate_PKT1(0, 0, (short) 12);
        cpr = processPacket(tmExtractor, bb);
        containers = cpr.getContainerResult();
        container = containers.get(containers.size() - 1).getContainer();
        assertEquals("PKT1", container.getName());
        received = cpr.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT4_JavaAlgo() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT4());
        assertEquals(4, received.size());

        ParameterValue pv2 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FloatPara4_1"));
        assertNotNull(pv2);

        assertEquals(3.0, pv2.getEngValue().getFloatValue(), 1e-2);
    }

    @Test
    public void testPKT5BinaryRawValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT5());
        assertEquals(6, received.size());

        ParameterValue pv1 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/FixedBinary1"));
        assertTrue(Arrays.equals(RefMdbPacketGenerator.pFixedBinary1, pv1.getRawValue().getBinaryValue()));

        ParameterValue pv2 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/PrependedSizeBinary1"));
        byte[] psb = RefMdbPacketGenerator.pPrependedSizeBinary1;
        // the first byte is the size
        assertTrue(Arrays.equals(Arrays.copyOfRange(psb, 1, psb.length), pv2.getRawValue().getBinaryValue()));

        ParameterValue pv3 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/CustomBinaryEncoding1"));
        assertTrue(Arrays.equals(MyDecoder.fixedValue, pv3.getRawValue().getBinaryValue()));
    }

    @Test
    public void testPKT6_TimePara() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT6());
        assertEquals(5, received.size());

        ParameterValue pv1 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/TimePara6_1"));
        assertEquals("1980-01-06T00:00:01.000Z", TimeEncoding.toString(pv1.getEngValue().getTimestampValue()));

        ParameterValue pv2 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/TimePara6_2"));
        assertEquals("1980-01-06T00:00:01.500Z", TimeEncoding.toString(pv2.getEngValue().getTimestampValue()));
    }

    @Test
    public void testPKT7_Aggrgate() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT7());
        assertEquals(4, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/aggregate_para1"));
        AggregateValue aggrv = (AggregateValue) pv.getEngValue();
        assertEquals(tmGenerator.paggr1_member1, aggrv.getMemberValue("member1").getUint32Value());
        assertEquals(tmGenerator.paggr1_member2, aggrv.getMemberValue("member2").getUint32Value());
        assertEquals(tmGenerator.paggr1_member3, aggrv.getMemberValue("member3").getFloatValue(), 1e-5);
    }

    @Test
    public void testPKT8_ArrayOfAggrgate() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        ParameterValueList received = extractParameters(tmGenerator.generate_PKT8());
        assertEquals(6, received.size());

        ParameterValue pv = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/array_para1"));
        ArrayValue arrv = (ArrayValue) pv.getEngValue();
        assertArrayEquals(new int[] { tmGenerator.para_pkt8_count }, arrv.getDimensions());
        for (int i = 0; i < tmGenerator.para_pkt8_count; i++) {
            AggregateValue aggrv = (AggregateValue) arrv.getElementValue(i);
            assertEquals(i, aggrv.getMemberValue("member1").getUint32Value());
            assertEquals(2 * i, aggrv.getMemberValue("member2").getUint32Value());
            assertEquals(i / 2.0, aggrv.getMemberValue("member3").getFloatValue(), 1e-5);
        }
        assertEquals(pv.getRawValue(), pv.getEngValue());

        ParameterValue pv1 = received.getLastInserted(mdb.getParameter("/REFMDB/SUBSYS1/matrix-para"));
        ArrayValue arrv1 = (ArrayValue) pv1.getEngValue();
        assertArrayEquals(new int[] { 3, 3 }, arrv1.getDimensions());
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                FloatValue fv = (FloatValue) arrv1.getElementValue(new int[] { i, j });
                assertEquals(i * 3 + j, fv.getFloatValue(), 1e-5);
            }
        }
    }

    @Test
    public void testPKT1_10ContextCalibration() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        Parameter p10_3 = mdb.getParameter("/REFMDB/SUBSYS1/FloatPara1_10_3");

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.startProviding(p10_3);

        ParameterValue pv1 = processTm(tmExtractor, tmGenerator.generate_PKT1_10(0, 1, 30))
                .getLastInserted(p10_3);
        assertEquals(30, pv1.getRawValue().getFloatValue(), 1E-10);
        assertEquals(30, pv1.getEngValue().getFloatValue(), 1E-10);

        // now with the spline calibrator

        ParameterValue pv2 = processTm(tmExtractor, tmGenerator.generate_PKT1_10(0, 0, 30)).getLastInserted(p10_3);
        assertEquals(30, pv2.getRawValue().getFloatValue(), 1E-10);
        assertEquals(3, pv2.getEngValue().getFloatValue(), 1E-10);
    }

    @Test
    public void testPKT6_TimeParaPartialExtraction() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        Parameter p6_2 = mdb.getParameter("/REFMDB/SUBSYS1/TimePara6_2");

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.startProviding(p6_2);

        ParameterValueList received = processTm(tmExtractor, tmGenerator.generate_PKT6());

        ParameterValue pv2 = received.getLastInserted(p6_2);
        assertEquals("1980-01-06T00:00:01.500Z", TimeEncoding.toString(pv2.getEngValue().getTimestampValue()));
    }

    @Test
    public void testPKT9_IndirectPara() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();

        byte[] bb = tmGenerator.generate_PKT9((short) 1, 0x01020304);

        ParameterValueList received = processTm(tmExtractor, bb);
        ParameterValue pv = received.getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/obpara1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());

        bb = tmGenerator.generate_PKT9((short) 2, 0x02030405);
        received = processTm(tmExtractor, bb);
        assertNull(received.getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/obpara1")));
        pv = received.getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/obpara2"));
        assertEquals(0x02030405, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT9_IndirectParaPartialExtraction() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        Parameter p = mdb.getParameter("/REFMDB/SUBSYS1/obpara1");
        tmExtractor.startProviding(p);

        byte[] bb = tmGenerator.generate_PKT9((short) 1, 0x01020304);
        ParameterValueList received = processTm(tmExtractor, bb);
        ParameterValue pv = received.getFirstInserted(p);
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT11_TerminatedStringWithSize() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);

        tmExtractor.provideAll();
        String s = "blabla";
        byte[] bb = tmGenerator.generate_PKT11(s, (byte) 55);

        ParameterValueList received = processTm(tmExtractor, bb);
        ParameterValue pv = received
                .getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/terminatedString_with_max_size"));
        assertEquals(s, pv.getEngValue().getStringValue());
        pv = received
                .getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/para_after_terminatedString_with_max_size"));
        assertEquals(55, pv.getEngValue().getUint32Value());

        s = "string of 20 chars..";
        bb = tmGenerator.generate_PKT11(s, (byte) 77);

        received = processTm(tmExtractor, bb);
        pv = received.getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/terminatedString_with_max_size"));
        assertEquals(s, pv.getEngValue().getStringValue());
        pv = received
                .getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/para_after_terminatedString_with_max_size"));
        assertEquals(77, pv.getEngValue().getUint32Value());

        s = "string longer than 20 chars";
        bb = tmGenerator.generate_PKT11(s, (byte) 99);
        received = processTm(tmExtractor, bb);
        pv = received.getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/terminatedString_with_max_size"));
        assertEquals(s.substring(0, 20), pv.getEngValue().getStringValue());
        pv = received
                .getFirstInserted(mdb.getParameter("/REFMDB/SUBSYS1/para_after_terminatedString_with_max_size"));
        assertEquals(99, pv.getEngValue().getUint32Value());
    }

    private ParameterValueList extractParameters(byte[] pkt) {
        XtceTmExtractor tmExtractor = new XtceTmExtractor(mdb);
        tmExtractor.provideAll();
        return processTm(tmExtractor, pkt);
    }

    private ParameterValueList processTm(XtceTmExtractor tmExtractor, byte[] pkt) {
        ContainerProcessingResult cpr = processPacket(tmExtractor, pkt);
        return cpr.getParameterResult();
    }

    void printParaList(ParameterValueList pvl) {
        System.out.println(String.format("%-30s %10s %10s", "name", "eng", "raw"));
        System.out.println(String.format("----------------------------------------------------"));
        for (ParameterValue pv : pvl) {
            System.out.println(String.format("%-30s %10s %10s", pv.getParameter().getName(),
                    pv.getEngValue().toString(), pv.getRawValue().toString()));
        }
    }

    ContainerProcessingResult processPacket(XtceTmExtractor tmExtractor, byte[] pkt) {
        return tmExtractor.processPacket(pkt, TimeEncoding.getWallclockTime(),
                TimeEncoding.getWallclockTime(), 0);
    }

    ContainerProcessingResult processPacket(XtceTmExtractor tmExtractor, byte[] pkt, SequenceContainer startContainer) {
        return tmExtractor.processPacket(pkt, TimeEncoding.getWallclockTime(),
                TimeEncoding.getWallclockTime(), 0, startContainer);
    }

    private String byteBufferToHexString(ByteBuffer bb) {
        bb.mark();
        StringBuilder sb = new StringBuilder();
        while (bb.hasRemaining()) {
            String s = Integer.toString(bb.get() & 0xFF, 16);
            if (s.length() == 1) {
                sb.append("0");
            }
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
        public Value extractRaw(DataEncoding de, ContainerProcessingContext pcontext, BitBuffer buf) {
            buf.setPosition(buf.getPosition() + 32);
            return ValueUtility.getBinaryValue(fixedValue);
        }
    }
}
