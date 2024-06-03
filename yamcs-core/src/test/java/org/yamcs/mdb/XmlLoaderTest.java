package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatDataEncoding.Encoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.RateInStream;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataEncoding.SizeType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.UnitType;

public class XmlLoaderTest {

    @Test
    public void test1() throws Exception {
        Mdb mdb = MdbFactory.createInstanceByConfig("ccsds-green-book");
        Parameter pmt = mdb.getParameter("/SpaceVehicle/MissionTime");
        assertTrue(pmt.getParameterType() instanceof AbsoluteTimeParameterType);

        Parameter cst = mdb.getParameter("/SpaceVehicle/CheckSum");
        assertTrue(cst.getParameterType() instanceof IntegerParameterType);
        IntegerParameterType ipt = (IntegerParameterType) cst.getParameterType();
        assertEquals(8, ipt.getEncoding().getSizeInBits());
        assertEquals(DataSource.DERIVED, cst.getDataSource());

        Parameter pms = mdb.getParameter("/SpaceVehicle/Seconds");
        assertTrue(pms.getParameterType() instanceof AbsoluteTimeParameterType);
        AbsoluteTimeParameterType ptype = (AbsoluteTimeParameterType) pms.getParameterType();
        ReferenceTime rtime = ptype.getReferenceTime();
        assertEquals(TimeEpoch.CommonEpochs.TAI, rtime.getEpoch().getCommonEpoch());

        DataEncoding encoding = ptype.getEncoding();
        assertTrue(encoding instanceof IntegerDataEncoding);
        assertEquals(32, ((IntegerDataEncoding) encoding).getSizeInBits());

        MetaCommand cmd1 = mdb.getMetaCommand("/SpaceVehicle/PWHTMR");
        CommandContainer cc = cmd1.getCommandContainer();
        List<SequenceEntry> sel = cc.getEntryList();
        assertEquals(3, sel.size());

        assertEquals("Header", cc.getBaseContainer().getName());
        assertTrue(cc.getRestrictionCriteria() instanceof ComparisonList);
    }

    @Test
    public void testBogusSat() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("BogusSAT");

        SpaceSystem sc001 = mdb.getSpaceSystem("/BogusSAT/SC001");
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
        StringParameterType sp = (StringParameterType) p.getParameterType();
        assertEquals(sp.getEncoding().getClass(), StringDataEncoding.class);
        StringDataEncoding sde = (StringDataEncoding) sp.getEncoding();
        assertEquals(SizeType.FIXED, sde.getSizeType());
        assertEquals(128, sde.getSizeInBits());

        p = payload1.getParameter("Payload_Fault_Message");
        assertNotNull(p);
        assertEquals(p.getParameterType().getClass(), StringParameterType.class);
        sp = (StringParameterType) p.getParameterType();
        assertEquals(sp.getEncoding().getClass(), StringDataEncoding.class);
        sde = (StringDataEncoding) sp.getEncoding();
        assertEquals(SizeType.TERMINATION_CHAR, sde.getSizeType());
        assertEquals(0, sde.getTerminationChar());

        SequenceContainer sc = busElectronics.getSequenceContainer("SensorHistoryRecord");
        assertNotNull(sc);
        RateInStream ris = sc.getRateInStream();
        assertNotNull(ris);
        assertEquals(10000, ris.getMaxInterval());
        assertEquals(100, ris.getMinInterval());

        Parameter p1 = busElectronics.getParameter("Battery_Current");
        FloatParameterType fpt1 = (FloatParameterType) p1.getParameterType();
        assertEquals(0.2, fpt1.getInitialValue(), 1e-5);

        Parameter p2 = payload1.getParameter("Basic_MilFloat32");
        FloatParameterType fpt2 = (FloatParameterType) p2.getParameterType();
        FloatDataEncoding fde2 = (FloatDataEncoding) fpt2.getEncoding();
        assertEquals(Encoding.MILSTD_1750A, fde2.getEncoding());
        assertEquals(32, fde2.getSizeInBits());

        Parameter p3 = payload1.getParameter("Basic_MilFloat48");
        FloatParameterType fpt3 = (FloatParameterType) p3.getParameterType();
        FloatDataEncoding fde3 = (FloatDataEncoding) fpt3.getEncoding();
        assertEquals(Encoding.MILSTD_1750A, fde2.getEncoding());
        assertEquals(48, fde3.getSizeInBits());

        MetaCommand mc = payload1.getMetaCommand("Adjust_Payload_1_Config");
        assertNotNull(mc);
        CommandContainer cc = mc.getCommandContainer();
        assertEquals("Adjust_Payload_1_Config_Container", cc.getName());
        Container basec = cc.getBaseContainer();
        assertEquals("CCSDSPUSCommandPacket", basec.getName());

        Parameter pssl = busElectronics.getParameter("SunSensorLevel");
        FloatParameterType ptype = (FloatParameterType) pssl.getParameterType();
        IntegerDataEncoding encoding = (IntegerDataEncoding) ptype.getEncoding();
        PolynomialCalibrator cal = (PolynomialCalibrator) encoding.getDefaultCalibrator();
        assertArrayEquals(new double[] { -10.0, 5.0 }, cal.getCoefficients(), 1E-10);

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

        IntegerParameterType ptype2 = (IntegerParameterType) mdb.getParameterType("/BogusSAT/CCSDSPacketLengthType");
        List<UnitType> unitl = ptype2.getUnitSet();
        assertEquals(1, unitl.size());
    }

    @Test
    public void testMathOpCal() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("BogusSAT");
        SpaceSystem busElectronics = mdb.getSpaceSystem("/BogusSAT/SC001/BusElectronics");

        FloatParameterType ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_2_Type");
        FloatDataEncoding encoding = (FloatDataEncoding) ptype.getEncoding();
        MathOperationCalibrator c = (MathOperationCalibrator) encoding.getDefaultCalibrator();

        CalibratorProc cproc = MathOperationCalibratorFactory.compile(c);
        double value = 3;
        double expectedResult = 64 * (Math.log(1.234 * value) / Math.log(2));
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);

        ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_7_Type");
        encoding = (FloatDataEncoding) ptype.getEncoding();
        c = (MathOperationCalibrator) encoding.getDefaultCalibrator();

        cproc = MathOperationCalibratorFactory.compile(c);
        value = 20;
        double x1 = Math.pow((5 - 3), 3.0) + 92;
        double x2 = Math.abs(-4.0 / Math.log10(x1));
        double x3 = Math.atan(Math.acos(Math.sin(Math.acos(Math.cos(Math.asin(5 - x2 - 2.0))))));
        double x4 = (x3 + 19.0) % 8.0;
        expectedResult = Math.pow(4.0, x4);
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);

        ptype = (FloatParameterType) busElectronics.getParameterType("Float_MathOpCal_9_Type");
        encoding = (FloatDataEncoding) ptype.getEncoding();
        c = (MathOperationCalibrator) encoding.getDefaultCalibrator();

        cproc = MathOperationCalibratorFactory.compile(c);
        value = 50;
        x1 = Math.cos(Math.cos(90 - 1) + 89) + 45;
        expectedResult = Math.sinh(Math.cosh(Math.tanh(Math.tan(x1))));
        assertEquals(expectedResult, cproc.calibrate(value), 1E-10);

    }

    @Test
    public void testBogusSat2() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("BogusSAT2");

        ParameterType ptype = mdb.getParameterType("/BogusSAT/CCSDSAPIDType");
        assertEquals(2047, ((Long) ptype.getInitialValue()).intValue());

        ptype = mdb.getParameterType("/BogusSAT/TM_CHECKSUMType");
        assertEquals("CRC", (String) ptype.getInitialValue());

        Parameter p = mdb.getParameter("/BogusSAT/LOG_MSGS/RECORDFLAG");

        assertEquals(3735928559L, ((Long) p.getInitialValue()).longValue());
        IntegerParameterType itype = (IntegerParameterType) mdb.getParameterType("/BogusSAT/LittleEndianInteger1");
        assertEquals(ByteOrder.LITTLE_ENDIAN, itype.getEncoding().getByteOrder());

        FloatParameterType ftype = (FloatParameterType) mdb.getParameterType("/BogusSAT/LittleEndianFloat1");
        assertEquals(ByteOrder.LITTLE_ENDIAN, ftype.getEncoding().getByteOrder());
    }

    @Test
    public void testXtceCommandSignificance() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("refxtce");
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/vital_command");
        Significance significance = mc.getDefaultSignificance();
        assertEquals("no particular reason", significance.getReasonForWarning());
        assertEquals(Levels.DISTRESS, significance.getConsequenceLevel());
    }

    @Test
    public void testPersistence() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("refxtce");
        Parameter p1 = mdb.getParameter("/RefXtce/local_para1");
        assertTrue(p1.isPersistent());

        Parameter p2 = mdb.getParameter("/RefXtce/local_para2");
        assertFalse(p2.isPersistent());
    }

    @Test
    public void testTransmissionConstraint() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("refxtce");
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/cmd_with_constraint1");
        List<TransmissionConstraint> tcList = mc.getTransmissionConstraintList();
        assertEquals(1, tcList.size());
        TransmissionConstraint tc0 = tcList.get(0);
        assertEquals(1234, tc0.getTimeout());
        ComparisonList matchCriteria = (ComparisonList) tc0.getMatchCriteria();
        assertEquals(1, matchCriteria.getComparisonList().size());
        Comparison c0 = matchCriteria.getComparisonList().get(0);
        assertEquals(mdb.getParameter("/RefXtce/local_para1"), ((ParameterInstanceRef) c0.getRef()).getParameter());
    }

    @Test
    public void testCommandVerification() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("refxtce");
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/cmd_with_verifier1");
        List<CommandVerifier> cvList = mc.getCommandVerifiers();
        assertEquals(1, cvList.size());
        CommandVerifier cv0 = cvList.get(0);
        assertEquals(100, cv0.getCheckWindow().getTimeToStartChecking());
        assertEquals(1000, cv0.getCheckWindow().getTimeToStopChecking());

        Comparison c0 = (Comparison) cv0.getMatchCriteria();
        assertEquals(mdb.getParameter("/RefXtce/local_para1"), ((ParameterInstanceRef) c0.getRef()).getParameter());
    }

    @Test
    public void testAutoPart() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("BogusSAT2");
        assertTrue(mdb.getSequenceContainer("/BogusSAT/CCSDSPUSTelemetryPacket").useAsArchivePartition());
        assertFalse(mdb.getSequenceContainer("/BogusSAT/SC001/ECSS_Service_1_Subservice_1").useAsArchivePartition());
    }

    @Test
    public void testNoAutoPart() throws XMLStreamException, IOException {
        Mdb mdb = MdbFactory.createInstanceByConfig("BogusSAT2-noautopart");
        assertFalse(
                mdb.getSequenceContainer("/BogusSAT/SC001/BusElectronics/SensorHistoryRecord").useAsArchivePartition());
        assertTrue(mdb.getSequenceContainer("/BogusSAT/CCSDSPUSTelemetryPacket").useAsArchivePartition());
    }
}
