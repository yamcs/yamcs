package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.ParameterValue;
import org.yamcs.RefMdbPacketGenerator;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.StringConvertors;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtceproc.XtceDbFactory;

import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;


import static org.junit.Assert.*;

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
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();

        ByteBuffer bb=tmGenerator.generate_PKT11();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT11 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        // System.out.println("received: "+received);
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        ParameterValue pv=received.get(3);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara1_1", pv.getParameter().getQualifiedName());
        assertEquals(tmGenerator.pIntegerPara1_1, pv.getEngValue().getUint32Value());

        pv=received.get(10);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_6", pv.getParameter().getQualifiedName());
        assertEquals(tmGenerator.pIntegerPara11_6, pv.getEngValue().getUint32Value());
       
        
        pv=received.get(11);
        assertEquals("/REFMDB/SUBSYS1/IntegerPara11_7", pv.getParameter().getQualifiedName());
        assertEquals(tmGenerator.pIntegerPara11_7, pv.getEngValue().getUint32Value());
        
        pv=received.get(12);
        assertEquals("/REFMDB/SUBSYS1/StringPara11_5", pv.getParameter().getQualifiedName());
        assertEquals(tmGenerator.pStringPara11_5, pv.getEngValue().getStringValue());
    }

    @Test
    public void testPKT13StringStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT13();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT13 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        assertEquals( 13, received.size() );
        
        // Fixed size strings
        ParameterValue pv=received.get( 6 );
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara13_1", pv.getParameter().getQualifiedName());
        pv=received.get( 7 );
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara13_2", pv.getParameter().getQualifiedName());
        // Terminated strings
        pv=received.get( 8 );
        assertEquals("/REFMDB/SUBSYS1/TerminatedStringPara13_3", pv.getParameter().getQualifiedName());
        pv=received.get( 9 );
        assertEquals("/REFMDB/SUBSYS1/TerminatedStringPara13_4", pv.getParameter().getQualifiedName());
        // Prepended size strings
        pv=received.get( 10 );
        assertEquals("/REFMDB/SUBSYS1/PrependedSizeStringPara13_5", pv.getParameter().getQualifiedName());
        pv=received.get( 11 );
        assertEquals("/REFMDB/SUBSYS1/PrependedSizeStringPara13_6", pv.getParameter().getQualifiedName());
        // Final fixed size string of large space
        pv=received.get( 12 );
        assertEquals("/REFMDB/SUBSYS1/FixedStringPara13_7", pv.getParameter().getQualifiedName());
    }
    
    @Test
    public void testPKT13StringValues() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT13();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT13 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        assertEquals( 13, received.size() );
        // Fixed size strings
        ParameterValue pv=received.get( 6 );
        assertEquals(tmGenerator.pFixedStringPara13_1, pv.getEngValue().getStringValue());
        pv=received.get( 7 );
        assertEquals(tmGenerator.pFixedStringPara13_2, pv.getEngValue().getStringValue());
        // Terminated strings
        pv=received.get( 8 );
        assertEquals(tmGenerator.pTerminatedStringPara13_3, pv.getEngValue().getStringValue());
        pv=received.get( 9 );
        assertEquals(tmGenerator.pTerminatedStringPara13_4, pv.getEngValue().getStringValue());
        // Prepended size strings
        pv=received.get( 10 );
        assertEquals(tmGenerator.pPrependedSizeStringPara13_5, pv.getEngValue().getStringValue());
        pv=received.get( 11 );
        assertEquals(tmGenerator.pPrependedSizeStringPara13_6, pv.getEngValue().getStringValue());
        // Final fixed size string of large space
        pv=received.get( 12 );
        assertEquals(tmGenerator.pFixedStringPara13_7, pv.getEngValue().getStringValue());
    }
    
    
    @Test
    public void testPKT14StringFloatStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT14 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        
        // Check all the parameters have been parsed
        assertEquals( 12, received.size() );
        // Verify correct names
        ParameterValue pv=received.get( 6 );
        assertEquals("/REFMDB/SUBSYS1/StringFloatFSPara14_1", pv.getParameter().getQualifiedName());
        pv=received.get( 7 );
        assertEquals("/REFMDB/SUBSYS1/StringFloatTSCPara14_2", pv.getParameter().getQualifiedName());
        pv=received.get( 8 );
        assertEquals("/REFMDB/SUBSYS1/StringFloatTSSCPara14_3", pv.getParameter().getQualifiedName());
        pv=received.get( 9 );
        assertEquals("/REFMDB/SUBSYS1/StringFloatTSSCPara14_3", pv.getParameter().getQualifiedName());
        pv=received.get( 10 );
        assertEquals("/REFMDB/SUBSYS1/StringFloatPSPara14_5", pv.getParameter().getQualifiedName());
		pv=received.get( 11 );
		assertEquals("/REFMDB/SUBSYS1/StringFloatFSBPara14_4", pv.getParameter().getQualifiedName());
    }
    
    @Test
    public void testPKT14StringFloatValues() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT14();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT14 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 12, received.size() );
        
        ParameterValue pv=received.get( 6 );
		assertEquals( Float.parseFloat( tmGenerator.pStringFloatFSPara14_1 ), pv.getEngValue().getFloatValue(), 0.0001 );
        pv=received.get( 7 );
        assertEquals( Float.parseFloat( tmGenerator.pStringFloatTSCPara14_2 ), pv.getEngValue().getFloatValue(), 0.0001 );
        pv=received.get( 8 );
        assertEquals( Float.parseFloat( tmGenerator.pStringFloatTSSCPara14_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        pv=received.get( 9 );
        assertEquals( Float.parseFloat( tmGenerator.pStringFloatTSSCPara14_3 ), pv.getEngValue().getFloatValue(), 0.0001 );
        pv=received.get( 10 );
        assertEquals( Float.parseFloat( tmGenerator.pStringFloatPSPara14_5 ), pv.getEngValue().getFloatValue(), 0.0001 );
		pv=received.get( 11 );
		assertEquals( Float.parseFloat( tmGenerator.pStringFloatFSBPara14_4 ), pv.getEngValue().getFloatValue(), 0.0001 );
    }
    
    @Test
    public void testPKT15StringIntStructure() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT15();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT15 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 11, received.size() );
        
        // Verify correct names
        ParameterValue pv=received.get( 6 );
        assertEquals("/REFMDB/SUBSYS1/StringIntFixedPara15_1", pv.getParameter().getQualifiedName());
        pv=received.get( 7 );
        assertEquals("/REFMDB/SUBSYS1/StringIntTermPara15_2", pv.getParameter().getQualifiedName());
        pv=received.get( 8 );
        assertEquals("/REFMDB/SUBSYS1/StringIntTermPara15_3", pv.getParameter().getQualifiedName());
        pv=received.get( 9 );
        assertEquals("/REFMDB/SUBSYS1/StringIntPrePara15_4", pv.getParameter().getQualifiedName());
        pv=received.get( 10 );
        assertEquals("/REFMDB/SUBSYS1/StringIntStrPara15_5", pv.getParameter().getQualifiedName());
    }
    
    @Test
    public void testPKT15StringIntValues() throws ConfigurationException {
    	RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProvidingAll();
        
        ByteBuffer bb=tmGenerator.generate_PKT15();
        tmExtractor.processPacket(bb, TimeEncoding.currentInstant());
        
        System.out.println("PKT15 buffer: "+StringConvertors.arrayToHexString(bb.array()));
        ArrayList<ParameterValue> received=tmExtractor.getParameterResult();
        // Check all the parameters have been parsed
        assertEquals( 11, received.size() );
        
        // Verify correct values
        ParameterValue pv=received.get( 6 );
        assertEquals( Integer.parseInt( tmGenerator.pStringIntFixedPara15_1 ), pv.getEngValue().getUint32Value() );
        pv=received.get( 7 );
        assertEquals( Integer.parseInt( tmGenerator.pStringIntTermPara15_2 ), pv.getEngValue().getUint32Value() );
        pv=received.get( 8 );
        assertEquals( Integer.parseInt( tmGenerator.pStringIntTermPara15_3 ), pv.getEngValue().getUint32Value() );
        pv=received.get( 9 );
        assertEquals( Integer.parseInt( tmGenerator.pStringIntPrePara15_4 ), pv.getEngValue().getUint32Value() );
        pv=received.get( 10 );
        assertEquals( Integer.parseInt( tmGenerator.pStringIntStrPara15_5 ), pv.getEngValue().getUint32Value() );
    }

    @Test
    public void testPacketSubscrPKT11() throws ConfigurationException {
        RefMdbPacketGenerator tmGenerator=new RefMdbPacketGenerator();
        XtceDb xtcedb=XtceDbFactory.getInstanceByConfig("refmdb");
        //   xtcedb.print(System.out);

        XtceTmExtractor tmExtractor=new XtceTmExtractor(xtcedb);
        tmExtractor.startProviding(xtcedb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1"));
        tmExtractor.processPacket(tmGenerator.generate_PKT11(), TimeEncoding.currentInstant());
        
        ArrayList<SequenceContainer> received=tmExtractor.getContainerResult();
        assertEquals(2, received.size());
        assertEquals("/REFMDB/ccsds-default", received.get(0).getQualifiedName());
        assertEquals("/REFMDB/SUBSYS1/PKT1", received.get(1).getQualifiedName());
    }
}
