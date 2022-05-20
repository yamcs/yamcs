package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.parameterarchive.TestUtils;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class ArrayParameterCacheTest {
    Parameter p1 = new Parameter("p1");
    Parameter p2 = new Parameter("p2");

    @BeforeAll
    public static void before() {
        TimeEncoding.setUp();
    }

    @Test
    public void test1() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 1000, 4096);

        ArrayParameterCache pcache = new ArrayParameterCache("test", pcc); // 1 second
        assertNull(pcache.getLastValue(p1));

        ParameterValue p1v1 = getStringParameterValue(p1, 10);
        p1v1.setExpireMillis(1000);

        ParameterValue p2v1 = getFloatParameterValue(p2, 10);
        p2v1.setAcquisitionStatus(AcquisitionStatus.INVALID);
        pcache.update(Arrays.asList(p1v1, p2v1));

        TestUtils.checkEquals(p1v1, pcache.getLastValue(p1));
        TestUtils.checkEquals(p2v1, pcache.getLastValue(p2));

        ParameterValue p1v2 = getStringParameterValue(p1, 20);
        p1v2.setExpireMillis(1000);

        pcache.update(Arrays.asList(p1v2));

        TestUtils.checkEquals(p1v2, pcache.getLastValue(p1));
        TestUtils.checkEquals(p2v1, pcache.getLastValue(p2));

        List<ParameterValue> pvlist = pcache.getValues(Arrays.asList(p1, p2));

        checkEquals(pvlist, p1v2, p2v1);

        pvlist = pcache.getValues(Arrays.asList(p2, p1));
        checkEquals(pvlist, p2v1, p1v1);

    }

    @Test
    public void testNoCacheAll() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, false, 1000, 4096);

        ArrayParameterCache pcache = new ArrayParameterCache("test", pcc); // 1 second
        ParameterValue p1v0 = getStringParameterValue(p1, 0);
        pcache.update(Arrays.asList(p1v0));
        assertNull(pcache.getLastValue(p1));

        ParameterValue p1v1 = getStringParameterValue(p1, 10);
        ParameterValue p2v1 = getFloatParameterValue(p2, 10);
        pcache.update(Arrays.asList(p1v1, p2v1));

        TestUtils.checkEquals(p1v1, pcache.getLastValue(p1));
        assertNull(pcache.getLastValue(p2));

        ParameterValue p2v2 = getStringParameterValue(p2, 20);
        pcache.update(Arrays.asList(p2v2));

        TestUtils.checkEquals(p2v2, pcache.getLastValue(p2));

        List<ParameterValue> pvlist = pcache.getValues(Arrays.asList(p1, p2));
        checkEquals(pvlist, p1v1, p2v2);

        pvlist = pcache.getValues(Arrays.asList(p2, p1));
        checkEquals(pvlist, p2v2, p1v1);

    }

    @Test
    public void testCircularity() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 1000, 4096);
        ArrayParameterCache pcache = new ArrayParameterCache("test", pcc);
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i * 100L);
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }
        ParameterValue pv0 = expectedPVlist.get(0);

        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(10, pvlist.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(pv0.getStatus().hashCode(), pvlist.get(i).getStatus().hashCode());
            TestUtils.checkEquals(expectedPVlist.get(9 - i), pvlist.get(i));
        }

        for (int i = 10; i < 16; i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i * 100L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }

        pvlist = pcache.getAllValues(p1);

        assertEquals(16, pvlist.size());
        for (int i = 0; i < 16; i++) {
            TestUtils.checkEquals(expectedPVlist.get(15 - i), pvlist.get(i));
        }

        ParameterValue pv = getUint64ParameterValue(p1, 16 * 100L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);

        pvlist = pcache.getAllValues(p1);
        assertEquals(16, pvlist.size());
        for (int i = 0; i < 16; i++) {
            TestUtils.checkEquals(expectedPVlist.get(16 - i), pvlist.get(i));
        }
    }

    @Test
    public void test5() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 2000, 4096);
        ArrayParameterCache pcache = new ArrayParameterCache("test", pcc); // should keep at least 200 samples
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i * 10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }

        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(256, pvlist.size());
        for (int i = 0; i < 256; i++) {
            TestUtils.checkEquals(expectedPVlist.get(255 - i), pvlist.get(i));
        }
        ParameterValue pv = getUint64ParameterValue(p1, 256 * 10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);

        pv = getUint64ParameterValue(p1, 257 * 10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);

        pvlist = pcache.getAllValues(p1);
        assertEquals(256, pvlist.size());
        for (int i = 0; i < 256; i++) {
            TestUtils.checkEquals(expectedPVlist.get(257 - i), pvlist.get(i));
        }

    }

    @Test
    public void testMaxSize() {
        ParameterCacheConfig pcc = new ParameterCacheConfig(true, true, 2000, 128);
        ArrayParameterCache pcache = new ArrayParameterCache("test", pcc); // should keep max 128 samples
        assertNull(pcache.getLastValue(p1));
        List<ParameterValue> expectedPVlist = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            ParameterValue pv = getUint64ParameterValue(p1, i * 10L);
            expectedPVlist.add(pv);
            pcache.update(Arrays.asList(pv));
        }

        List<ParameterValue> pvlist = pcache.getAllValues(p1);
        assertEquals(128, pvlist.size());
        for (int i = 0; i < 128; i++) {
            TestUtils.checkEquals(expectedPVlist.get(255 - i), pvlist.get(i));
        }
        ParameterValue pv = getUint64ParameterValue(p1, 256 * 10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);

        pv = getUint64ParameterValue(p1, 257 * 10L);
        pcache.update(Arrays.asList(pv));
        expectedPVlist.add(pv);

        pvlist = pcache.getAllValues(p1);
        assertEquals(128, pvlist.size());
        for (int i = 0; i < 128; i++) {
            TestUtils.checkEquals(expectedPVlist.get(257 - i), pvlist.get(i));
        }

    }

    ParameterValue getUint64ParameterValue(Parameter p, long t) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(t);
        pv.setAcquisitionTime(t + 5);
        pv.setEngineeringValue(ValueUtility.getUint64Value(t));
        return pv;
    }

    ParameterValue getFloatParameterValue(Parameter p, long t) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(t);
        pv.setEngineeringValue(ValueUtility.getFloatValue((float) t));
        return pv;
    }

    ParameterValue getStringParameterValue(Parameter p, long timestamp) {
        ParameterValue pv = new ParameterValue(p);
        pv.setGenerationTime(timestamp);
        pv.setEngineeringValue(ValueUtility.getStringValue(p.getName() + "_" + timestamp));
        return pv;
    }

    public static void checkEquals(List<ParameterValue> actual, ParameterValue... expected) {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            ParameterValue pv = expected[i];
            TestUtils.checkEquals(pv, actual.get(i));
        }
    }
}
