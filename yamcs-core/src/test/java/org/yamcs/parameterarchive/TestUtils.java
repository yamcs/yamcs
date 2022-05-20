package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class TestUtils {
    static ParameterValue getParameterValue(Parameter p, long instant, int intv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setAcquisitionTime(instant);
        Value v = ValueUtility.getSint32Value(intv);
        pv.setEngValue(v);
        return pv;
    }

    static ParameterValue getParameterValue(Parameter p, long instant, String sv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setAcquisitionTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngValue(v);
        return pv;
    }

    public static void checkEquals(ParameterValue pv1, ParameterValue pv2) {
        assertEquals(pv1.getParameter(), pv2.getParameter());
        assertEquals(pv1.getGenerationTime(), pv2.getGenerationTime());
        assertEquals(pv1.getAcquisitionTime(), pv2.getAcquisitionTime());
        assertEquals(pv1.getRawValue(), pv2.getRawValue());
        assertEquals(pv1.getEngValue(), pv2.getEngValue());
        assertEquals(pv1.getStatus(), pv2.getStatus());
    }

    public static void checkEquals(ParameterValueArray pva, ParameterValue... pvs) {
        checkEquals(true, true, true, pva, pvs);
    }

    public static void checkEquals(boolean shouldHaveEngValues, boolean shouldHaveRawValues,
            boolean shouldHaveParameterStatus, ParameterValueArray actualPva, ParameterValue... expectedPvs) {
        assertEquals(expectedPvs.length, actualPva.timestamps.length);
        for (int i = 0; i < expectedPvs.length; i++) {
            ParameterValue pv = expectedPvs[i];
            assertEquals(pv.getGenerationTime(), actualPva.timestamps[i]);
        }
        if (shouldHaveEngValues) {
            for (int i = 0; i < expectedPvs.length; i++) {
                assertEquals(expectedPvs[i].getEngValue(), actualPva.getEngValues().getValue(i));
            }
        } else {
            assertNull(actualPva.engValues);
        }
        if (shouldHaveRawValues) {
            for (int i = 0; i < expectedPvs.length; i++) {
                if (expectedPvs[i].getRawValue() != null) {
                    assertEquals(expectedPvs[i].getRawValue(), actualPva.getRawValues().getValue(i));
                }
            }
        } else {
            assertNull(actualPva.rawValues);
        }
        if (shouldHaveParameterStatus) {
            assertNotNull(actualPva.paramStatus);
            assertEquals(expectedPvs.length, actualPva.paramStatus.length);
            for (int i = 0; i < expectedPvs.length; i++) {
                checkEquals(expectedPvs[i], actualPva.paramStatus[i]);
            }
        } else {
            assertNull(actualPva.paramStatus);
        }
    }

    private static void checkEquals(ParameterValue parameterValue, ParameterStatus parameterStatus) {
        assertEquals(ParameterStatusSegment.getStatus(parameterValue, null), parameterStatus);
    }

    public static void checkEquals(ParameterIdValueList plist, long expectedTime, ParameterValue... expectedPv) {
        assertEquals(expectedTime, plist.instant);
        assertEquals(expectedPv.length, plist.values.size());
        for (int i = 0; i < expectedPv.length; i++) {
            ParameterValue pv = expectedPv[i];
            Value v = plist.values.get(i).getEngValue();
            Value rv = plist.values.get(i).getRawValue();
            assertEquals(pv.getEngValue(), v);
            if (pv.getRawValue() != null) {
                assertEquals(pv.getRawValue(), rv);
            }
        }
    }

    static void checkEquals(MyValueConsumer c, ParameterValue... pvs) {
        assertEquals(pvs.length, c.times.size());
        for (int i = 0; i < pvs.length; i++) {
            ParameterValue pv = pvs[i];
            assertEquals(pv.getAcquisitionTime(), (long) c.times.get(i));
            assertEquals(pv.getEngValue(), c.values.get(i));
        }
    }
}
