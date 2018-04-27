package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.xml.XtceStaxReader;
import org.yamcs.xtceproc.XtceDbFactory;

public class XmlLoaderTest {

    @Test
    public void test1() throws Exception {        
        XtceDb db = XtceDbFactory.createInstanceByConfig("ccsds-green-book");
        Parameter pmt = db.getParameter("/SpaceVehicle/MissionTime");
        assertTrue(pmt.getParameterType() instanceof AbsoluteTimeParameterType);
        
        Parameter cst = db.getParameter("/SpaceVehicle/CheckSum");
        assertTrue(cst.getParameterType() instanceof IntegerParameterType);
        IntegerParameterType ipt = (IntegerParameterType) cst.getParameterType();
        assertEquals(8, ipt.getEncoding().getSizeInBits());
        assertEquals(DataSource.DERIVED, cst.getDataSource());
        
        
        Parameter pms = db.getParameter("/SpaceVehicle/Seconds");
        assertTrue(pms.getParameterType() instanceof AbsoluteTimeParameterType);
        AbsoluteTimeParameterType ptype = (AbsoluteTimeParameterType) pms.getParameterType();
        ReferenceTime rtime = ptype.getReferenceTime();
        assertEquals(TimeEpoch.CommonEpochs.TAI, rtime.getEpoch().getCommonEpoch());
        
        DataEncoding encoding = ptype.getEncoding();
        assertTrue(encoding instanceof IntegerDataEncoding);
        assertEquals(32, ((IntegerDataEncoding) encoding).getSizeInBits());
        
        
        MetaCommand cmd1 = db.getMetaCommand("/SpaceVehicle/PWHTMR");
        CommandContainer cc = cmd1.getCommandContainer();
        List<SequenceEntry> sel = cc.getEntryList();
        assertEquals(3, sel.size());
        
        assertEquals("Header", cc.getBaseContainer().getName());
        assertTrue(cc.getRestrictionCriteria() instanceof ComparisonList);
    }

    @Test
    public void testBogusSat() throws XMLStreamException, IOException {
        XtceDb db = XtceDbFactory.createInstanceByConfig("BogusSAT");
        
        SpaceSystem sc001 = db.getSpaceSystem("/BogusSAT/SC001"); 
        assertNotNull(sc001);
        
        SpaceSystem busElectronics = sc001.getSubsystem("BusElectronics");
        assertNotNull(busElectronics);
        SpaceSystem payload1 = sc001.getSubsystem("Payload1");
        assertNotNull(payload1);
        SpaceSystem payload2 = sc001.getSubsystem("Payload2");
        assertNotNull(payload2);
        
        
        Parameter p = busElectronics.getParameter("Bus_Fault_Message");
        assertNotNull(p);
        assertEquals(p.getParameterType().getClass(), StringParameterType.class);
        StringParameterType sp = (StringParameterType)p.getParameterType();
        assertEquals(sp.encoding.getClass(), StringDataEncoding.class);
        StringDataEncoding sde = (StringDataEncoding) sp.encoding;
        assertEquals(SizeType.FIXED, sde.getSizeType());
        assertEquals(128, sde.getSizeInBits());
        
        p = payload1.getParameter("Payload_Fault_Message");
        assertNotNull(p);
        assertEquals(p.getParameterType().getClass(), StringParameterType.class);
        sp = (StringParameterType)p.getParameterType();
        assertEquals(sp.encoding.getClass(), StringDataEncoding.class);
        sde = (StringDataEncoding) sp.encoding;
        assertEquals(SizeType.TERMINATION_CHAR, sde.getSizeType());
        assertEquals(0, sde.getTerminationChar());
        
        SequenceContainer sc = busElectronics.getSequenceContainer("SensorHistoryRecord");
        assertNotNull(sc);
        RateInStream ris = sc.getRateInStream();
        assertNotNull(ris);
        assertEquals(10000, ris.getMaxInterval());
        assertEquals(100, ris.getMinInterval());
        
        
        
        MetaCommand mc = payload1.getMetaCommand("Adjust_Payload_1_Config");
        assertNotNull(mc);
        CommandContainer cc = mc.getCommandContainer();
        assertEquals("Payload_1_Control_Container", cc.getName());
        Container basec = cc.getBaseContainer();
        assertEquals("CCSDSPUSCommandPacket", basec.getName());
        
        Parameter pssl = busElectronics.getParameter("SunSensorLevel");
        FloatParameterType ptype = (FloatParameterType) pssl.getParameterType();
        IntegerDataEncoding encoding = (IntegerDataEncoding) ptype.getEncoding();
        PolynomialCalibrator cal = (PolynomialCalibrator) encoding.getDefaultCalibrator();
        assertArrayEquals(new double[] {-10.0, 5.0}, cal.coefficients, 1E-10);
        
        List<ContextCalibrator> ctxc = encoding.getContextCalibratorList();
        assertEquals(2, ctxc.size());
        

    }
    
        
}
