package org.yamcs.xtceproc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

public class XtceTmExtractorTest {
    
    private static XtceDb xtcedb;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
        xtcedb=XtceDbFactory.createInstance("refmdb");
        //xtcedb.print(System.out);
    }

    @Test
    public void testPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb = tmGenerator.generate_PKT1_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT11 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        // System.out.println("received: "+received);
        ParameterValueList received = tmExtractor.getParameterResult();
        Parameter p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        ParameterValue pv=received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1, pv.getEngValue().getUint32Value());

        p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_6");
        pv=received.getLastInserted(p);
        assertEquals(tmGenerator.pIntegerPara1_1_6, pv.getEngValue().getUint32Value());
       
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_7"));
        assertEquals(tmGenerator.pIntegerPara1_1_7, pv.getEngValue().getUint32Value());

        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1_8"));
        assertEquals(tmGenerator.pIntegerPara1_1_8, pv.getEngValue().getUint64Value());
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringPara1_1_5"));
        assertEquals(tmGenerator.pStringPara1_1_5, pv.getEngValue().getStringValue());
    }

    
    @Test
    public void testPKT1_2() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT1_2();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        ParameterValueList received=tmExtractor.getParameterResult();
        
        ParameterValue pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_1, pv.getEngValue().getUint32Value());
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_2, pv.getEngValue().getUint32Value());
        
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEIntegerPara1_2_3"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEIntegerPara1_2_3, pv.getEngValue().getUint32Value());
        
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_1"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_1*0.0001672918, pv.getEngValue().getFloatValue(), 1e-8);
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/LEFloatPara1_2_2"));
        assertNotNull(pv);
        assertEquals(tmGenerator.pLEFloatPara1_2_2, pv.getEngValue().getFloatValue(), 0);
        
        assertEquals(pv.getAcquisitionTime()+1500, pv.getExpirationTime());
    }

    
    @Test
    public void testPKT1_3StringStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);        
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_3();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT13 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals( 13, received.size() );
        
        // Fixed size strings
        ParameterValue pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_1"));
        assertEquals(pFixedStringPara1_3_1, pv.getEngValue().getStringValue());
     
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_2"));
        assertEquals(pFixedStringPara1_3_2, pv.getEngValue().getStringValue());
        
        // Terminated strings
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_3"));
        assertEquals(pTerminatedStringPara1_3_3, pv.getEngValue().getStringValue());
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara1_3_4"));
        assertEquals(pTerminatedStringPara1_3_4, pv.getEngValue().getStringValue());
        
        // Prepended size strings
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_5"));
        assertEquals(pPrependedSizeStringPara1_3_5, pv.getEngValue().getStringValue());
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara1_3_6"));
        assertEquals(pPrependedSizeStringPara1_3_6, pv.getEngValue().getStringValue());
        
        // Final fixed size string of large space
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara1_3_7"));
        assertEquals(pFixedStringPara1_3_7, pv.getEngValue().getStringValue());
    }
   
   
    
    @Test
    public void testPKT1_4StringFloatStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT14 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        
        // Check all the parameters have been parsed
        assertEquals( 12, received.size() );
        
        ParameterValue pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSPara1_4_1"));
        assertEquals( Float.parseFloat(tmGenerator.pStringFloatFSPara1_4_1 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals( Float.parseFloat(tmGenerator.pStringFloatTSCPara1_4_2 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.removeLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals( Float.parseFloat( pStringFloatTSSCPara1_4_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara1_4_3"));
        assertEquals( Float.parseFloat(pStringFloatTSSCPara1_4_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatPSPara1_4_5"));
        assertEquals( Float.parseFloat(pStringFloatPSPara1_4_5 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSBPara1_4_4"));
        assertEquals( Float.parseFloat(pStringFloatFSBPara1_4_4 ), pv.getEngValue().getFloatValue(), 0.0001 );
    }
   
   
    @Test
    public void testPKT1_4InvalidStringFloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        tmGenerator.pStringFloatTSCPara1_4_2 = "invalidfloat";
        ByteBuffer bb=tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT14 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        
        // Check all the parameters have been parsed
        assertEquals( 12, received.size() );
        
        ParameterValue pv=received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara1_4_2"));
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }

    
    @Test
    public void testPKT1_5StringIntStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_5();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT15 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 11, received.size() );
        
        // Verify correct names
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntFixedPara1_5_1"));
        assertEquals( Integer.parseInt(pStringIntFixedPara1_5_1 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals( Integer.parseInt(tmGenerator.pStringIntTermPara1_5_2 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_3"));
        assertEquals( Integer.parseInt(pStringIntTermPara1_5_3 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntPrePara1_5_4"));
        assertEquals( Integer.parseInt(pStringIntPrePara1_5_4 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntStrPara1_5_5"));
        assertEquals( Integer.parseInt(pStringIntStrPara1_5_5 ), pv.getEngValue().getUint32Value() );
    }
    
    @Test
    public void testPKT1_5InvalidStringIntStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        tmGenerator.pStringIntTermPara1_5_2="invalidint";
        
        ByteBuffer bb=tmGenerator.generate_PKT1_5();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT15 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 11, received.size() );
        
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara1_5_2"));
        assertEquals( AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }
    
    
    @Test
    public void testPKT1_9BooleanValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_9();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT19 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();

        assertEquals(10, received.size());
        
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_1"));
        assertEquals(true, pv.getRawValue().getBooleanValue());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_2"));
        assertEquals(false, pv.getRawValue().getBooleanValue());
        assertEquals(false, pv.getEngValue().getBooleanValue());
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_3"));
        assertEquals(true, pv.getRawValue().getBooleanValue());
        assertEquals(true, pv.getEngValue().getBooleanValue());
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara1_9_4"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testContainerSubscriptionPKT1_1() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1"));

        ByteBuffer bb = tmGenerator.generate_PKT1_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        ArrayList<ContainerExtractionResult> received=tmExtractor.getContainerResult();
        assertEquals(2, received.size());
        assertEquals("/REFMDB/ccsds-default", received.get(0).getContainer().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/PKT1", received.get(1).getContainer().getQualifiedName());
        assertEquals("description 1", received.get(1).getContainer().getLongDescription());
        
        bb.position(0);
        String pkt11 = byteBufferToHexString(bb);
        
        // First example, access the received PKT1, as its PKT11 instantiation
        ContainerExtractionResult pkt1Result = received.get(1);
        ByteBuffer pkt1Buffer = pkt1Result.getContainerContent();
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
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_7();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        //System.out.println("PKT17 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();

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
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_11();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        ParameterValueList received=tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_11_1"));
        assertEquals(tmGenerator.pIntegerPara1_11_1_unsigned_value/2, pv.getEngValue().getUint32Value()&0xFFFFFFFFL);
    }
    
    @Test
    public void testPKT1_12_stringenum() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_12();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        ParameterValueList received=tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals("value1", pv.getEngValue().getStringValue());
    }
    
    @Test
    public void testPKT1_12_invalidstringenum() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        tmGenerator.pStringEnumPara1_12_1="invalidlong";
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_12();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        
        ParameterValueList received=tmExtractor.getParameterResult();
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/StringEnumPara1_12_1"));
        assertEquals(tmGenerator.pStringEnumPara1_12_1, pv.getRawValue().getStringValue());
        assertEquals(AcquisitionStatus.INVALID, pv.getAcquisitionStatus());
    }


    @Test
    public void testPKT3_dynamicSize() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT3();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(17, received.size());
        assertEquals(2,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1")).getRawValue().getUint32Value());
        assertEquals(3,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue().getUint32Value());
        assertEquals(4,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_2")).getRawValue().getUint32Value());
        assertEquals(5,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue().getUint32Value());
        assertEquals(6,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue().getUint32Value());
        assertEquals(61,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2_1")).getRawValue().getUint32Value());
        assertEquals(7,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue().getUint32Value());
        assertEquals(8,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue().getUint32Value());
        assertEquals(9,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para1")).getRawValue().getUint32Value());
        assertEquals(10,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para2")).getRawValue().getUint32Value());
        assertEquals(11,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue().getUint32Value());
        assertEquals(12,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue().getUint32Value());
        assertEquals(13,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para3")).getRawValue().getUint32Value());
        assertEquals(14,  received.removeFirst(xtcedb.getParameter("/REFMDB/SUBSYS1/block_para4")).getRawValue().getUint32Value());
    }
    
    @Test
    public void testProcessPacket_startContainer() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb = tmGenerator.generate_PKT2();
        SequenceContainer startContainer = xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT2");
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime(), startContainer);
        
        ParameterValueList received=tmExtractor.getParameterResult();
        
        assertEquals(2, received.size());
        ParameterValue pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_1"));
        assertEquals(pIntegerPara2_1, pv.getEngValue().getUint32Value());
        
        pv = received.getLastInserted(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_2"));
        assertEquals(pIntegerPara2_2, pv.getEngValue().getUint32Value());
    }

    @Test
    public void testPKT1_List() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT1_List();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_List", container.getName());

        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_AND() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT1_AND();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());

        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_AND", container.getName());

        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_OR() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        // first condition
        ByteBuffer bb=tmGenerator.generate_PKT1_OR_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_OR", container.getName());
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

    }

    @Test
    public void testPKT1_PKT1_AND_OR() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        // first condition
        ByteBuffer bb=tmGenerator.generate_PKT1_AND_OR_1();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // second condition
        bb=tmGenerator.generate_PKT1_AND_OR_2();
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_AND_OR", container.getName());
        received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }


    @Test
    public void testPKT1_PKT1_OR_AND() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        // or condition 1
        ByteBuffer bb=tmGenerator.generate_PKT1(0, 0, (short) 1);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // or condition 3
        bb=tmGenerator.generate_PKT1(0, 0, (short) 3);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_OR_AND", container.getName());
        received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // does not match
        bb=tmGenerator.generate_PKT1(0, 0, (short) 4);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1", container.getName());
        received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }

    @Test
    public void testPKT1_PKT1_RANGE() {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        // in range ]0xA, 0xC[
        ByteBuffer bb=tmGenerator.generate_PKT1(0, 0, (short) 11);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        ArrayList<ContainerExtractionResult> containers = tmExtractor.getContainerResult();
        SequenceContainer container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1_RANGE", container.getName());
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);

        // out of range
        bb=tmGenerator.generate_PKT1(0, 0, (short) 12);
        tmExtractor.processPacket(bb, TimeEncoding.getWallclockTime(), TimeEncoding.getWallclockTime());
        containers = tmExtractor.getContainerResult();
        container = tmExtractor.getContainerResult().get(containers.size() -1).getContainer();
        assertEquals("PKT1", container.getName());
        received=tmExtractor.getParameterResult();
        assertEquals(received.size(), 6);
    }


    private String byteBufferToHexString(ByteBuffer bb) {
        bb.mark();
        StringBuilder sb =new StringBuilder();
        while(bb.hasRemaining()) {
            String s=Integer.toString(bb.get()&0xFF,16);
            if(s.length()==1) sb.append("0");
            sb.append(s.toUpperCase());
        }
        bb.reset();
        return sb.toString();
    }
}
