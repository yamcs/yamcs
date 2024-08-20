package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.util.DoubleRange;

public class ObjectSegmentTest {
    Parameter p1 = new Parameter("abc");

    @Test
    public void test() throws DecodingException {
        ParameterStatusSegment pss = new ParameterStatusSegment(true);
        ParameterValue pv = TestUtils.getParameterValue(p1, 0, 120);
        pv.setMonitoringResult(MonitoringResult.CRITICAL);
        pv.setCriticalRange(new DoubleRange(5, 100));

        pss.addParameterValue(pv);
        pss.consolidate();

        assertEquals(1, pss.rleValues.size());
        ParameterStatus s = pss.get(0);
        // assertEquals(pv.getCriticalRange().getMax(), s.getAlarmRange(0).getMaxInclusive(), 1e-10);
        assertEquals(pv.getCriticalRange().getMin(), s.getAlarmRange(0).getMinInclusive(), 1e-10);

        // ascending range
        ParameterStatus[] statusList = (ParameterStatus[]) pss.getRangeArray(0, 1, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        // descending range
        statusList = (ParameterStatus[]) pss.getRangeArray(-1, 0, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        pv.setAcquisitionTime(100);
        pss.addParameterValue(pv);
        assertEquals(1, pss.rleValues.size());
        ParameterStatus s1 = pss.get(1);
        assertEquals(s, s1);

        // ascending range
        statusList = (ParameterStatus[]) pss.getRangeArray(0, 2, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);

        // descending range
        statusList = (ParameterStatus[]) pss.getRangeArray(-1, 1, false);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[1]);
        assertEquals(s, statusList[0]);

        pv.setEngValue(ValueUtility.getUint32Value(70));
        pv.setMonitoringResult(MonitoringResult.WARNING);
        pv.setWarningRange(new DoubleRange(0, 80));
        pv.setAcquisitionTime(200);
        pss.addParameterValue(pv);
        pss.consolidate();
        assertEquals(2, pss.rleValues.size());
        ParameterStatus s2 = pss.get(2);
        assertEquals(pv.getWarningRange().getMax(), s2.getAlarmRange(0).getMaxInclusive(), 1e-10);
        assertEquals(pv.getWarningRange().getMin(), s2.getAlarmRange(0).getMinInclusive(), 1e-10);
        assertEquals(pv.getCriticalRange().getMax(), s2.getAlarmRange(1).getMaxInclusive(), 1e-10);
        assertEquals(pv.getCriticalRange().getMin(), s2.getAlarmRange(1).getMinInclusive(), 1e-10);

        // ascending ranges
        statusList = (ParameterStatus[]) pss.getRangeArray(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);

        statusList = (ParameterStatus[]) pss.getRangeArray(0, 1, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(0, 2, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);

        statusList = (ParameterStatus[]) pss.getRangeArray(1, 2, true);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(2, 3, true);
        assertEquals(1, statusList.length);
        assertEquals(s2, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(1, 3, true);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s2, statusList[1]);

        // descending ranges
        statusList = (ParameterStatus[]) pss.getRangeArray(-1, 2, false);
        assertEquals(3, statusList.length);
        assertEquals(s2, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s, statusList[2]);

        statusList = (ParameterStatus[]) pss.getRangeArray(-1, 0, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(-1, 1, false);
        assertEquals(2, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);

        statusList = (ParameterStatus[]) pss.getRangeArray(0, 1, false);
        assertEquals(1, statusList.length);
        assertEquals(s, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(1, 2, false);
        assertEquals(1, statusList.length);
        assertEquals(s2, statusList[0]);

        statusList = (ParameterStatus[]) pss.getRangeArray(0, 2, false);
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
        statusList = (ParameterStatus[]) pss1.getRangeArray(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);

        // encoding/decoding ENUM RLE
        bb = ByteBuffer.allocate(pss.enumRleSize);
        pss.writeEnumRle(bb);
        assertEquals(83, bb.position());

        bb.rewind();
        pss1 = ParameterStatusSegment.parseFrom(bb);

        assertTrue(pss1.runLengthEncoded);
        assertEquals(2, pss1.rleObjectList.size());
        statusList = (ParameterStatus[]) pss1.getRangeArray(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);

        // encoding/decoding ENUM FPROF
        bb = ByteBuffer.allocate(size);
        pss.writeEnumFprof(bb);
        assertEquals(82, bb.position());

        bb.rewind();
        pss1 = ParameterStatusSegment.parseFrom(bb);

        assertFalse(pss1.runLengthEncoded);
        assertEquals(3, pss1.objectList.size());
        statusList = (ParameterStatus[]) pss1.getRangeArray(0, 3, true);
        assertEquals(3, statusList.length);
        assertEquals(s, statusList[0]);
        assertEquals(s, statusList[1]);
        assertEquals(s2, statusList[2]);

    }

    @Test
    public void testEnumFprof() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatusSegment pss = new ParameterStatusSegment(true);

        for (int i = 0; i < 1000; i++) {
            pss.add(ps1);
            pss.add(ps2);
        }
        pss.consolidate();

        assertTrue(pss.enumRleSize > pss.enumRawSize);

        ByteBuffer bb = ByteBuffer.allocate(pss.getMaxSerializedSize());

        pss.writeTo(bb);
        assertEquals(396, bb.position());

        assertEquals(ObjectSegment.SUBFORMAT_ID_ENUM_FPROF, bb.get(0));
        bb.rewind();
        pss = ParameterStatusSegment.parseFrom(bb);

        for (int i = 0; i < 2000; i += 2) {
            assertTrue(ps1.equals(pss.get(i)));
            assertTrue(ps2.equals(pss.get(i + 1)));
        }

        pss.makeWritable();
        pss.add(ps1);
        pss.add(ps2);
        for (int i = 0; i < 2002; i += 2) {
            assertTrue(ps1.equals(pss.get(i)));
            assertTrue(ps2.equals(pss.get(i + 1)));
        }

        pss.consolidate();
        bb = ByteBuffer.allocate(pss.getMaxSerializedSize());
        pss.writeTo(bb);

        assertEquals(ObjectSegment.SUBFORMAT_ID_ENUM_FPROF, bb.get(0));
        bb.rewind();
        pss = ParameterStatusSegment.parseFrom(bb);

        for (int i = 0; i < 2002; i += 2) {
            assertTrue(ps1.equals(pss.get(i)));
            assertTrue(ps2.equals(pss.get(i + 1)));
        }
    }

    @Test
    public void testRangesAscending() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatus ps3 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(3).build()).build();

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

        ParameterStatus[] r = pss.getRangeArray(0, 1, true);
        checkEquals(r, ps1);

        r = pss.getRangeArray(0, 3, true);
        checkEquals(r, ps1, ps1, ps2);

        r = pss.getRangeArray(1, 2, true);
        checkEquals(r, ps1);

        r = pss.getRangeArray(2, 3, true);
        checkEquals(r, ps2);

        r = pss.getRangeArray(1, 5, true);
        checkEquals(r, ps1, ps2, ps2, ps2);

        r = pss.getRangeArray(2, 5, true);
        checkEquals(r, ps2, ps2, ps2);

        r = pss.getRangeArray(5, 9, true);
        checkEquals(r, ps3, ps3, ps3, ps3);

        r = pss.getRangeArray(8, 9, true);
        checkEquals(r, ps3);

        r = pss.getRangeArray(0, 9, true);
        checkEquals(r, ps1, ps1, ps2, ps2, ps2, ps3, ps3, ps3, ps3);

        pss.makeWritable();
        pss.add(ps3);

        r = pss.getRangeArray(0, 10, true);
        checkEquals(r, ps1, ps1, ps2, ps2, ps2, ps3, ps3, ps3, ps3, ps3);

        pss.consolidate();

        assertEquals(10, pss.size);

        bb = ByteBuffer.allocate(pss.getMaxSerializedSize());
        pss.writeTo(bb);
        bb.rewind();

        pss = ParameterStatusSegment.parseFrom(bb);

        assertTrue(pss.runLengthEncoded);
        assertEquals(3, pss.rleObjectList.size());

        r = pss.getRangeArray(0, 10, true);
        checkEquals(r, ps1, ps1, ps2, ps2, ps2, ps3, ps3, ps3, ps3, ps3);

    }

    @Test
    public void testRangesDescending() throws DecodingException {
        ParameterStatus ps1 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(1).build()).build();
        ParameterStatus ps2 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(2).build()).build();
        ParameterStatus ps3 = ParameterStatus.newBuilder()
                .addAlarmRange(AlarmRange.newBuilder().setMinInclusive(3).build()).build();

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

        ParameterStatus[] r = pss.getRangeArray(-1, 8, false);
        checkEquals(r, ps3, ps3, ps3, ps3, ps2, ps2, ps2, ps1, ps1);

        r = pss.getRangeArray(-1, 0, false);
        checkEquals(r, ps1);

        r = pss.getRangeArray(-1, 2, false);
        checkEquals(r, ps2, ps1, ps1);

        r = pss.getRangeArray(0, 1, false);
        checkEquals(r, ps1);

        r = pss.getRangeArray(1, 2, false);
        checkEquals(r, ps2);

        r = pss.getRangeArray(0, 4, false);
        checkEquals(r, ps2, ps2, ps2, ps1);

        r = pss.getRangeArray(1, 4, false);
        checkEquals(r, ps2, ps2, ps2);

        r = pss.getRangeArray(4, 8, false);
        checkEquals(r, ps3, ps3, ps3, ps3);

        r = pss.getRangeArray(7, 8, false);
        checkEquals(r, ps3);

        r = pss.getRangeArray(6, 7, false);
        checkEquals(r, ps3);

        r = pss.getRangeArray(3, 5, false);
        checkEquals(r, ps3, ps2);
    }

    private void checkEquals(ParameterStatus[] actual, ParameterStatus... expected) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
