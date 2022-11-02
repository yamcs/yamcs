package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.util.DoubleRange;

public class ParameterStatusTest {

    @Test
    public void test1() {
        Parameter p = new Parameter("p1");
        ParameterValue pv = new ParameterValue(p);
        pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);

        ParameterStatus s1 = ParameterStatusSegment.getStatus(pv, null);
        assertNotNull(s1);
        assertTrue(s1 == ParameterStatusSegment.getStatus(pv, s1));

        pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
        ParameterStatus s2 = ParameterStatusSegment.getStatus(pv, s1);
        assertNotEquals(s1, s2);
        assertTrue(s2 == ParameterStatusSegment.getStatus(pv, s2));

        pv.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
        ParameterStatus s3 = ParameterStatusSegment.getStatus(pv, s2);
        assertNotEquals(s2, s3);
        assertTrue(s3 == ParameterStatusSegment.getStatus(pv, s3));

        pv.setMonitoringResult(MonitoringResult.CRITICAL);
        ParameterStatus s4 = ParameterStatusSegment.getStatus(pv, s3);
        assertNotEquals(s3, s4);
        assertTrue(s4 == ParameterStatusSegment.getStatus(pv, s4));

        pv.setDistressRange(new DoubleRange(100, 1000));
        ParameterStatus s5 = ParameterStatusSegment.getStatus(pv, s4);
        assertNotEquals(s4, s5);
        assertTrue(s5 == ParameterStatusSegment.getStatus(pv, s5));

        pv.setWarningRange(new DoubleRange(0, 2, false, false));
        ParameterStatus s6 = ParameterStatusSegment.getStatus(pv, s5);
        assertNotEquals(s5, s6);
        assertTrue(s6 == ParameterStatusSegment.getStatus(pv, s6));
    }
}
