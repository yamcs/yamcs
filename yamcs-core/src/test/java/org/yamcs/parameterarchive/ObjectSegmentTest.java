package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.Parameter;


public class ObjectSegmentTest {
    Parameter p1 = new Parameter("abc");
    
    
    @Test
    public void test() throws DecodingException {
        ParameterStatusSegment pss = new ParameterStatusSegment(true);
        ParameterValue pv = TestUtils.getParameterValue(p1, 0, 120);
        pv.setMonitoringResult(MonitoringResult.CRITICAL);
        pv.setCriticalRange(new FloatRange(0, 100));
        
        pss.addParameterValue(pv);
        pss.consolidate();
        
        assertEquals(1, pss.rleValues.size());
        ParameterStatus s = pss.get(0);
        assertEquals(pv.criticalRange.getMaxInclusive(), s.getAlarmRange(0).getMaxInclusive(), 1e-10);
        assertEquals(pv.criticalRange.getMinInclusive(), s.getAlarmRange(0).getMinInclusive(), 1e-10);
        
        //ascending range
        ParameterStatus[] statusList = (ParameterStatus[]) pss.getRange(0, 1, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        
        //descending range
        statusList = (ParameterStatus[]) pss.getRange(-1, 0, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        pv.setAcquisitionTime(100);
        pss.addParameterValue(pv);
        assertEquals(1, pss.rleValues.size());
        ParameterStatus s1 = pss.get(1);
        assertEquals(s, s1);
        
        //ascending range
        statusList = (ParameterStatus[]) pss.getRange(0, 2, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        
        //descending range
        statusList = (ParameterStatus[]) pss.getRange(-1, 1, false);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[1]);
        assertEquals(s, statusList[0]);
        
        
        pv.setEngineeringValue(ValueUtility.getUint32Value(70));
        pv.setMonitoringResult(MonitoringResult.WARNING);
        pv.setWarningRange(new FloatRange(0, 80));
        pv.setAcquisitionTime(200);
        pss.addParameterValue(pv);
        pss.consolidate();
        assertEquals(2, pss.rleValues.size());
        ParameterStatus s2 = pss.get(2);
        assertEquals(pv.warningRange.getMaxInclusive(), s2.getAlarmRange(0).getMaxInclusive(), 1e-10);
        assertEquals(pv.warningRange.getMinInclusive(), s2.getAlarmRange(0).getMinInclusive(), 1e-10);
        assertEquals(pv.criticalRange.getMaxInclusive(), s2.getAlarmRange(1).getMaxInclusive(), 1e-10);
        assertEquals(pv.criticalRange.getMinInclusive(), s2.getAlarmRange(1).getMinInclusive(), 1e-10);
        
        //ascending ranges
        statusList = (ParameterStatus[]) pss.getRange(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);
        
        statusList = (ParameterStatus[]) pss.getRange(0, 1, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(0, 2, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        
        statusList = (ParameterStatus[]) pss.getRange(1, 2, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(2, 3, true);
        assertEquals(1, statusList.length);
        assertEquals(s2, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(1, 3, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s2, statusList[1]);
        
        
        //descending ranges
        statusList = (ParameterStatus[]) pss.getRange(-1, 2, false);
        assertEquals(3, statusList.length);
        assertEquals(s2, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s, statusList[2]);
        
        statusList = (ParameterStatus[]) pss.getRange(-1, 0, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(-1, 1, false);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        
        statusList = (ParameterStatus[]) pss.getRange(0, 1, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(1, 2, false);
        assertEquals(1, statusList.length);
        assertEquals(s2, statusList[0]);
        
        statusList = (ParameterStatus[]) pss.getRange(0, 2, false);
        assertEquals(2, statusList.length);
        assertEquals(s2, statusList[0]);
        assertEquals(s, statusList[1]);
        
        
        // encoding/decoding RAW
        int size = pss.getMaxSerializedSize();
        
        ByteBuffer bb = ByteBuffer.allocate(size);
        pss.writeRaw(bb);
        assertEquals(105, bb.position());
        
        bb.rewind();
        ParameterStatusSegment pss1 = ParameterStatusSegment.parseFrom(bb);
        
        assertEquals(3, pss1.objectList.size());
        statusList = (ParameterStatus[]) pss1.getRange(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);
        
        
        
        //encoding/decoding ENUM RLE
        bb = ByteBuffer.allocate(pss.enumRleSize);
        pss.writeEnumRle(bb);
        assertEquals(83, bb.position());
        
        bb.rewind();
        pss1 = ParameterStatusSegment.parseFrom(bb);
        
        assertTrue(pss1.runLengthEncoded);
        assertEquals(2, pss1.rleObjectList.size());
        statusList = (ParameterStatus[]) pss1.getRange(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);
        
        
        //encoding/decoding ENUM FPROF
        bb = ByteBuffer.allocate(size);
        pss.writeEnumFprof(bb);
        assertEquals(82, bb.position());
        
        bb.rewind();
        pss1 = ParameterStatusSegment.parseFrom(bb);
        
        assertFalse(pss1.runLengthEncoded);
        assertEquals(3, pss1.objectList.size());
        statusList = (ParameterStatus[]) pss1.getRange(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);
        
        
    }
    
    @Test
    public void testEnumFprof() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatusSegment pss = new ParameterStatusSegment(true);

        for(int i=0; i<1000;i++) {
            pss.add(ps1);
            pss.add(ps2);
        }
        pss.consolidate();
        
        assertTrue(pss.enumRleSize>pss.enumRawSize);
        
        ByteBuffer bb = ByteBuffer.allocate(pss.getMaxSerializedSize());
        
        pss.writeTo(bb);
        assertEquals(396, bb.position());
        
        assertEquals(ObjectSegment.SUBFORMAT_ID_ENUM_FPROF, bb.get(0));
        bb.rewind();
        pss =  ParameterStatusSegment.parseFrom(bb);
        
        for(int i=0; i<2000;i+=2) {
            assertTrue(ps1.equals(pss.get(i)));
            assertTrue(ps2.equals(pss.get(i+1)));
        }
    }
    
    @Test
    public void testRangesAscending() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatus ps3 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(3).build()).build();
        
        
        ParameterStatusSegment pss = new ParameterStatusSegment(true);
        pss.add(ps1);
        pss.add(ps1);
        
        pss.add(ps2);
        pss.add(ps2);
        pss.add(ps2);
        
        pss.add(ps3);
        pss.add(ps3);
        pss.add(ps3);
        pss.add(ps3);
        
        pss.consolidate();
        
        assertEquals(9, pss.size);
        
        ByteBuffer bb = ByteBuffer.allocate(pss.getMaxSerializedSize());
        pss.writeTo(bb);
        bb.rewind();
        
        pss = ParameterStatusSegment.parseFrom(bb);
        
        assertTrue(pss.runLengthEncoded);
        assertEquals(3, pss.rleObjectList.size());
        
        ParameterStatus[] r = pss.getRange(0, 1, true);
        checkEquals(r, ps1);
        
        
        r = pss.getRange(0, 3, true);
        checkEquals(r, ps1, ps1, ps2);
        
        
        r = pss.getRange(1, 2, true);
        checkEquals(r, ps1);

        
        r = pss.getRange(2, 3, true);
        checkEquals(r, ps2);
        
        r = pss.getRange(1, 5, true);
        checkEquals(r, ps1, ps2, ps2, ps2);
    
        r = pss.getRange(2, 5, true);
        checkEquals(r, ps2, ps2, ps2);
        
        r = pss.getRange(5, 9, true);
        checkEquals(r, ps3, ps3, ps3, ps3);
        
        r = pss.getRange(8, 9, true);
        checkEquals(r, ps3);
        
        r = pss.getRange(0, 9, true);
        checkEquals(r, ps1, ps1, ps2, ps2, ps2, ps3, ps3, ps3, ps3);
        
    }

    

    @Test
    public void testRangesDescending() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatus ps3 = ParameterStatus.newBuilder().addAlarmRange(AlarmRange.newBuilder().setMinInclusive(3).build()).build();
        
        
        ParameterStatusSegment pss = new ParameterStatusSegment(true);
        pss.add(ps1);
        pss.add(ps1);
        
        pss.add(ps2);
        pss.add(ps2);
        pss.add(ps2);
        
        pss.add(ps3);
        pss.add(ps3);
        pss.add(ps3);
        pss.add(ps3);
        
        assertEquals(9, pss.size);
        pss.consolidate();
        ByteBuffer bb = ByteBuffer.allocate(pss.getMaxSerializedSize());
        pss.writeTo(bb);
        bb.rewind();
        pss = ParameterStatusSegment.parseFrom(bb);
        
        ParameterStatus[]   r = pss.getRange(-1, 8, false);
        checkEquals(r, ps3, ps3, ps3, ps3, ps2, ps2, ps2, ps1, ps1);
        
        r = pss.getRange(-1, 0, false);
        checkEquals(r, ps1);
        
        
        r = pss.getRange(-1, 2, false);
        checkEquals(r, ps2, ps1, ps1);
        
        
        r = pss.getRange(0, 1, false);
        checkEquals(r, ps1);

        
        r = pss.getRange(1, 2, false);
        checkEquals(r, ps2);
        
        r = pss.getRange(0, 4, false);
        checkEquals(r, ps2, ps2, ps2, ps1);
    
        r = pss.getRange(1, 4, false);
        checkEquals(r, ps2, ps2, ps2);
        
        r = pss.getRange(4, 8, false);
        checkEquals(r, ps3, ps3, ps3, ps3);
        
        r = pss.getRange(7, 8, false);
        checkEquals(r, ps3);
        
        r = pss.getRange(6, 7, false);
        checkEquals(r, ps3);
        
        r = pss.getRange(3, 5, false);
        checkEquals(r, ps3, ps2);
        
    }

    private void checkEquals(ParameterStatus[] actual, ParameterStatus ... expected) {
        assertEquals(expected.length, actual.length);
        for(int i =0; i<expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
