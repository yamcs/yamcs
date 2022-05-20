package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.http.api.ParameterRanger.MultiRange;
import org.yamcs.http.api.ParameterRanger.Range;
import org.yamcs.http.api.ParameterRanger.SingleRange;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterStatus;

public class ParameterRangerTest {
    @Test
    public void test1() {
        ParameterRanger ranger = new ParameterRanger(-1, -1, 6, 2);

        ParameterValueArray pva = getPva("a", "b", "c", "b", "a", "a");
        ranger.accept(pva);
        List<Range> rlist = ranger.getRanges();
        assertEquals(1, rlist.size());
        checkMultiRange((MultiRange) rlist.get(0), 6, new int[] { 3, 2 }, new String[] { "a", "b" });
    }

    @Test
    public void test2() {
        ParameterRanger ranger = new ParameterRanger(-1, -1, 5, 2);
        ParameterValueArray pva = getPva("a", "b", "c", "b", "a", "a");
        ranger.accept(pva);
        List<Range> rlist = ranger.getRanges();
        assertEquals(2, rlist.size());
        checkMultiRange((MultiRange) rlist.get(0), 5, new int[] { 2, 2 }, new String[] { "a", "b" });
        checkSingleRange((SingleRange) rlist.get(1), 1, "a");
    }

    @Test
    public void test3() {
        ParameterRanger ranger = new ParameterRanger(-1, 1, 5, 2);
        ParameterValueArray pva = getPva("a", "b", "c", "b", "b",
                "a", "a", "a", "a", "a",
                "c");
        ranger.accept(pva);
        List<Range> rlist = ranger.getRanges();
        assertEquals(3, rlist.size());
        checkMultiRange((MultiRange) rlist.get(0), 5, new int[] { 1, 3 }, new String[] { "a", "b" });
        checkSingleRange((SingleRange) rlist.get(1), 5, "a");

        checkSingleRange((SingleRange) rlist.get(2), 1, null);
    }

    @Test
    public void test4() {
        ParameterRanger ranger = new ParameterRanger(-1, 1, 3, 1);
        ParameterValueArray pva = getPva("a", "a", "b", "c", "c", "c");
        ranger.accept(pva);
        List<Range> rlist = ranger.getRanges();
        assertEquals(2, rlist.size());

        checkMultiRange((MultiRange) rlist.get(0), 3, new int[] {}, new String[] {});
        checkSingleRange((SingleRange) rlist.get(1), 3, "c");
    }

    @Test
    public void test5() {
        ParameterRanger ranger = new ParameterRanger(-1, 1, 5, 1);
        ParameterValueArray pva = getPva("a", "a", "b", "a", "c");
        ranger.accept(pva);
        List<Range> rlist = ranger.getRanges();
        assertEquals(1, rlist.size());

        checkMultiRange((MultiRange) rlist.get(0), 5, new int[] { 3 }, new String[] { "a" });
    }

    private void checkSingleRange(SingleRange sr, int count, String value) {
        assertEquals(count, sr.totalCount());
        if (value == null) {
            assertNull(sr.value);
        } else {
            assertEquals(value, sr.value.getStringValue());
        }
    }

    void checkMultiRange(MultiRange mr, int count, int[] counts, String[] values) {
        assertEquals(count, mr.totalCount());

        assertArrayEquals(counts, mr.counts.toArray());
        assertEquals(values.length, mr.valueCount());
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], mr.values.get(i).getStringValue());
        }
    }

    ParameterValueArray getPva(String... values) {
        ValueArray engValues = new ValueArray(values);
        long[] timestamps = new long[values.length];
        for (int i = 0; i < timestamps.length; i++) {
            timestamps[i] = i;
        }
        ParameterStatus[] paramStatus = new ParameterStatus[values.length];
        Arrays.fill(paramStatus, ParameterStatus.newBuilder().setAcquisitionStatus(AcquisitionStatus.ACQUIRED).build());

        return new ParameterValueArray(timestamps, engValues, engValues, paramStatus);
    }
}
