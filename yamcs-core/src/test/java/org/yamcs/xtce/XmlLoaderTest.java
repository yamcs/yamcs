package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.yamcs.xtce.FloatDataEncoding.Encoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtceproc.CalibratorProc;
import org.yamcs.xtceproc.MathOperationCalibratorFactory;
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
        
        Parameter p1 = busElectronics.getParameter("Battery_Current");
        FloatParameterType fpt1 = (FloatParameterType)p1.getParameterType();
        assertEquals(0.2, fpt1.getInitialValue(), 1e-5);
        
        
        Parameter p2 =  payload1.getParameter("Basic_MilFloat32");
        FloatParameterType fpt2 = (FloatParameterType)p2.getParameterType();
        FloatDataEncoding fde2 = (FloatDataEncoding)fpt2.getEncoding();
        assertEquals(Encoding.MILSTD_1750A, fde2.getEncoding());
        assertEquals(32, fde2.getSizeInBits());
        
        Parameter p3 =  payload1.getParameter("Basic_MilFloat48");
        FloatParameterType fpt3 = (FloatParameterType)p3.getParameterType();
        FloatDataEncoding fde3 = (FloatDataEncoding)fpt3.getEncoding();
        assertEquals(Encoding.MILSTD_1750A, fde2.getEncoding());
        assertEquals(48, fde3.getSizeInBits());
        
        
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
        
        mc = busElectronics.getMetaCommand("Cmd1");
        List<ArgumentAssignment> l = mc.getArgumentAssignmentList();
        assertEquals(1, l.size());
        ArgumentAssignment argasign = l.get(0);
        assertEquals("CmdId", argasign.getArgumentName());
        assertEquals("1", argasign.getArgumentValue());
        
        Argument sarg = mc.getArgument("STRING_FV");
        assertEquals("blabla", sarg.getInitialValue());
        StringArgumentType sargType = (StringArgumentType) sarg.getArgumentType();
        StringDataEncoding sencoding = (StringDataEncoding) sargType.getEncoding();
        assertEquals(SizeType.FIXED, sencoding.getSizeType());
        assertEquals(320, sencoding.getSizeInBits());
        
        Argument barg = mc.getArgument("BINARY_FV");
        BinaryArgumentType bargType = (BinaryArgumentType) barg.getArgumentType();
        BinaryDataEncoding bencoding = (BinaryDataEncoding) bargType.getEncoding();
        assertEquals(SizeType.FIXED, sencoding.getSizeType());
        assertEquals(128, bencoding.getSizeInBits());
        
        
       
       
    }
    
    
    
    @Test
    public void testMathOpCal() throws XMLStreamException, IOException {
        XtceDb db = XtceDbFactory.createInstanceByConfig("BogusSAT");
        SpaceSystem busElectronics = db.getSpaceSystem("/BogusSAT/SC001/BusElectronics"); 

        FloatParameterType ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_2_Type");
        FloatDataEncoding encoding = (FloatDataEncoding) ptype.getEncoding();
        MathOperationCalibrator c = (MathOperationCalibrator)encoding.getDefaultCalibrator();
        
        CalibratorProc cproc = MathOperationCalibratorFactory.compile(c);
        double value = 3;
        double expectedResult = 64 * (Math.log(1.234 * value) / Math.log(2));
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);
        
        
        ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_7_Type");
        encoding = (FloatDataEncoding) ptype.getEncoding();
        c = (MathOperationCalibrator)encoding.getDefaultCalibrator();
        
        cproc = MathOperationCalibratorFactory.compile(c);
        value = 20;
        double x1 = Math.pow((5-3), 3.0)+92;
        double x2 = Math.abs(-4.0/Math.log10(x1));
        double x3 = Math.atan(Math.acos(Math.sin(Math.acos(Math.cos(Math.asin(5-x2-2.0))))));
        double x4 = (x3+19.0)%8.0;
        expectedResult = Math.pow(4.0, x4);
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);
        
        ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_9_Type");
        encoding = (FloatDataEncoding) ptype.getEncoding();
        c = (MathOperationCalibrator)encoding.getDefaultCalibrator();
        
        cproc = MathOperationCalibratorFactory.compile(c);
        value = 50;
        x1 = Math.cos(Math.cos(90-1)+89)+45;
        expectedResult = Math.sinh(Math.cosh(Math.tanh(Math.tan(x1))));
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);
        
    }
}
