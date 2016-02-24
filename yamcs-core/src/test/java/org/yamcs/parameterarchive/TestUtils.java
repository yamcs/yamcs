package org.yamcs.parameterarchive;

import static org.junit.Assert.*;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;

public class TestUtils {
    static ParameterValue getParameterValue(Parameter p, long instant, int intv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setAcquisitionTime(instant);
        Value v = ValueUtility.getSint32Value(intv);
        pv.setEngineeringValue(v);
        return pv;
    }

    static ParameterValue getParameterValue(Parameter p, long instant, String sv) {
        ParameterValue pv = new ParameterValue(p);
        pv.setAcquisitionTime(instant);
        Value v = ValueUtility.getStringValue(sv);
        pv.setEngineeringValue(v);
        return pv;
    }
    public static void checkEquals( ParameterValueArray pva, ParameterValue...pvs) {
        checkEquals(true, true, true, pva, pvs);
    }
    public static void checkEquals(boolean shouldHaveEngValues, boolean shouldHaveRawValues, boolean shouldHaveParameterStatus, ParameterValueArray actualPva, ParameterValue...expectedPvs) {
        assertEquals(expectedPvs.length, actualPva.timestamps.length);
        for(int i=0; i<expectedPvs.length; i++) {
            ParameterValue pv = expectedPvs[i];
            assertEquals(pv.getGenerationTime(), actualPva.timestamps[i]);
        }
        if(shouldHaveEngValues) {
            Value v = expectedPvs[0].getEngValue();
            if(v.getType()==Type.STRING) {
                assertTrue(actualPva.engValues instanceof String[]);
                String[] s = (String[]) actualPva.engValues;
                for(int i=0; i<expectedPvs.length; i++) {
                    v = expectedPvs[i].getEngValue();
                    assertEquals(v.getStringValue(), s[i]);
                }            
            } else {
                fail("check for "+v.getType()+" not implemented");
            }
        } else {
            assertNull(actualPva.engValues);
        }
        if(shouldHaveRawValues) {
            Value rv = expectedPvs[0].getRawValue();
            if(rv!=null) {
                if(rv.getType()==Type.UINT32) {
                    assertTrue(actualPva.rawValues instanceof int[]);
                    int[] s = (int[]) actualPva.rawValues;
                    for(int i=0; i<expectedPvs.length; i++) {
                        rv = expectedPvs[i].getRawValue();
                        assertEquals(rv.getUint32Value(), s[i]);
                    }            
                } else if(rv.getType()==Type.STRING) {
                    assertTrue(actualPva.rawValues instanceof String[]);
                    String[] s = (String[]) actualPva.rawValues;
                    for(int i=0; i<expectedPvs.length; i++) {
                        Value v = expectedPvs[i].getRawValue();
                        assertEquals(v.getStringValue(), s[i]);
                    }                
                }else {
                    fail("check for "+rv.getType()+" not implemented");
                }
            }
        } else {
            assertNull(actualPva.rawValues);
        }
        if(shouldHaveParameterStatus) {
            assertNotNull(actualPva.paramStatus);
            assertEquals(expectedPvs.length, actualPva.paramStatus.length);
            for(int i=0; i<expectedPvs.length; i++) {
                checkEquals(expectedPvs[i], actualPva.paramStatus[i]);
            }
        } else {
            assertNull(actualPva.paramStatus);
        }
    }




    private static void checkEquals(ParameterValue parameterValue,    ParameterStatus parameterStatus) {
        assertEquals(ParameterStatusSegment.getStatus(parameterValue), parameterStatus);
    }

    public static void checkEquals(ParameterIdValueList plist, long expectedTime, ParameterValue... expectedPv) {
        assertEquals(expectedTime, plist.instant);
        assertEquals(expectedPv.length, plist.values.size());
        for(int i=0; i<expectedPv.length; i++) {
            ParameterValue pv = expectedPv[i];
            Value v = plist.values.get(i).getEngValue();
            Value rv = plist.values.get(i).getRawValue();
            assertEquals(pv.getEngValue(), v);
            if(pv.getRawValue()!=null) {
                assertEquals(pv.getRawValue(), rv);
            }
        }
    }

    static void checkEquals(MyValueConsummer c, ParameterValue...pvs) {
        assertEquals(pvs.length, c.times.size());
        for(int i=0; i<pvs.length; i++) {
            ParameterValue pv = pvs[i];
            assertEquals(pv.getAcquisitionTime(), (long)c.times.get(i));
            assertEquals(pv.getEngValue(), c.values.get(i));
        }
    }
    
    
}
