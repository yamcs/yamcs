package org.yamcs.xtceproc;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

import static  org.yamcs.RefMdbPacketGenerator.*;

public class TestXtceTmExtractor {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false,false);
        XtceDbFactory.reset();
    }

    @Test
    public void testPKT11() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT11();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT11 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        // System.out.println("received: "+received);
        ParameterValueList received=tmExtractor.getParameterResult();
        Parameter p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        ParameterValue pv=received.getLast(p);
        assertEquals(tmGenerator.pIntegerPara1_1, pv.getEngValue().getUint32Value());

        p = xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara11_6");
        pv=received.getLast(p);
        assertEquals(tmGenerator.pIntegerPara11_6, pv.getEngValue().getUint32Value());
       
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara11_7"));
        assertEquals(tmGenerator.pIntegerPara11_7, pv.getEngValue().getUint32Value());

        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara11_8"));
        assertEquals(tmGenerator.pIntegerPara11_8, pv.getEngValue().getUint64Value());
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringPara11_5"));
        assertEquals(tmGenerator.pStringPara11_5, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT13StringStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
    	XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT13();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT13 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        assertEquals( 13, received.size() );
        
        // Fixed size strings
        ParameterValue pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara13_1"));
        assertEquals(pFixedStringPara13_1, pv.getEngValue().getStringValue());
     
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara13_2"));
        assertEquals(pFixedStringPara13_2, pv.getEngValue().getStringValue());
        
        // Terminated strings
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara13_3"));
        assertEquals(pTerminatedStringPara13_3, pv.getEngValue().getStringValue());
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/TerminatedStringPara13_4"));
        assertEquals(pTerminatedStringPara13_4, pv.getEngValue().getStringValue());
        
        // Prepended size strings
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara13_5"));
        assertEquals(pPrependedSizeStringPara13_5, pv.getEngValue().getStringValue());
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/PrependedSizeStringPara13_6"));
        assertEquals(pPrependedSizeStringPara13_6, pv.getEngValue().getStringValue());
        
        // Final fixed size string of large space
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/FixedStringPara13_7"));
        assertEquals(pFixedStringPara13_7, pv.getEngValue().getStringValue());
    }
   
   
    
    @Test
    public void testPKT14StringFloatStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT14 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        
        // Check all the parameters have been parsed
        assertEquals( 12, received.size() );
        // Verify correct names
        ParameterValue pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSPara14_1"));
        assertEquals( Float.parseFloat(pStringFloatFSPara14_1 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSCPara14_2"));
        assertEquals( Float.parseFloat(pStringFloatTSCPara14_2 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.removeLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara14_3"));
        assertEquals( Float.parseFloat( pStringFloatTSSCPara14_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatTSSCPara14_3"));
        assertEquals( Float.parseFloat(pStringFloatTSSCPara14_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv=received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatPSPara14_5"));
        assertEquals( Float.parseFloat(pStringFloatPSPara14_5 ), pv.getEngValue().getFloatValue(), 0.0001 );
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringFloatFSBPara14_4"));
        assertEquals( Float.parseFloat(pStringFloatFSBPara14_4 ), pv.getEngValue().getFloatValue(), 0.0001 );
    }
   
   
    @Test
    public void testPKT15StringIntStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT15();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT15 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 11, received.size() );
        
        // Verify correct names
        ParameterValue pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntFixedPara15_1"));
        assertEquals( Integer.parseInt(pStringIntFixedPara15_1 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara15_2"));
        assertEquals( Integer.parseInt(pStringIntTermPara15_2 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntTermPara15_3"));
        assertEquals( Integer.parseInt(pStringIntTermPara15_3 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntPrePara15_4"));
        assertEquals( Integer.parseInt(pStringIntPrePara15_4 ), pv.getEngValue().getUint32Value() );
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/StringIntStrPara15_5"));
        assertEquals( Integer.parseInt(pStringIntStrPara15_5 ), pv.getEngValue().getUint32Value() );
    }
    
    
    
    @Test
    public void testPKT19BooleanValues() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT19();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT19 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();

        assertEquals(10, received.size());
        
        ParameterValue pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara19_1"));
        assertEquals(true, pv.getRawValue().getBooleanValue());
        assertEquals(true, pv.getEngValue().getBooleanValue());

        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara19_2"));
        assertEquals(false, pv.getRawValue().getBooleanValue());
        assertEquals(false, pv.getEngValue().getBooleanValue());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara19_3"));
        assertEquals(true, pv.getRawValue().getBooleanValue());
        assertEquals(true, pv.getEngValue().getBooleanValue());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/BooleanPara19_4"));
        assertEquals(1, pv.getRawValue().getUint32Value());
        assertEquals(true, pv.getEngValue().getBooleanValue());
    }

    @Test
    public void testContainerSubscriptionPKT11() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1"));
        ByteBuffer bb = tmGenerator.generate_PKT11();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        ArrayList<ContainerExtractionResult> received=tmExtractor.getContainerResult();
        assertEquals(2, received.size());
        assertEquals("/REFMDB/ccsds-default", received.get(0).getContainer().getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/PKT1", received.get(1).getContainer().getQualifiedName());
        
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
    public void testPKT17FloatStructure() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT17();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        //System.out.println("PKT17 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ParameterValueList received=tmExtractor.getParameterResult();

        assertEquals(11, received.size());
        
        ParameterValue pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/FloatPara17_1"));
        assertEquals(-14.928, pv.getEngValue().getFloatValue(), 1e-5);
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara17_2"));
        assertEquals(6, pv.getEngValue().getSint32Value());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara17_3"));
        assertEquals(-6, pv.getEngValue().getSint32Value());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara17_4"));
        assertEquals(6, pv.getEngValue().getSint32Value());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara17_5"));
        assertEquals(-6, pv.getEngValue().getSint32Value());
    }
    
    @Test
    public void testPKT1_11_longuint32() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT1_11();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        ParameterValueList received=tmExtractor.getParameterResult();
        ParameterValue pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara1_11_1"));
        assertEquals(tmGenerator.pIntegerPara1_11_1_unsigned_value/2, pv.getEngValue().getUint32Value()&0xFFFFFFFFL);
    }
    
    @Test
    public void testProcessPacket_startContainer() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator = new RefMdbPacketGenerator();
        XtceDb xtcedb = XtceDbFactory.getInstanceByConfig("refmdb");

        XtceTmExtractor tmExtractor = new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb = tmGenerator.generate_PKT2();
        SequenceContainer startContainer = xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT2");
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant(), startContainer);
        
        ParameterValueList received=tmExtractor.getParameterResult();
        
        assertEquals(2, received.size());
        ParameterValue pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_1"));
        assertEquals(tmGenerator.pIntegerPara2_1, pv.getEngValue().getUint32Value());
        
        pv = received.getLast(xtcedb.getParameter("/REFMDB/SUBSYS1/IntegerPara2_2"));
        assertEquals(tmGenerator.pIntegerPara2_2, pv.getEngValue().getUint32Value());
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
