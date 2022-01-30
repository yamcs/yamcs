package org.yamcs.algorithms;

import java.time.Instant;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.utils.TimeEncoding;

/**
 * A ParameterValue as passed to an algorithm. Actual implementations are generated on-the-fly to walk around the issue
 * of Rhino that maps boxed primitives to JavaScript Objects instead of Numbers
 */
public abstract class ValueBinding {
    public long acquisitionTimeMillis;
    public long generationTimeMillis;
    public AcquisitionStatus acquisitionStatus;
    public MonitoringResult monitoringResult;
    public RangeCondition rangeCondition;

    public void updateValue(RawEngValue newValue) {
        if (newValue instanceof ParameterValue) {
            ParameterValue pv = (ParameterValue) newValue;
            acquisitionStatus = pv.getAcquisitionStatus();
            monitoringResult = pv.getMonitoringResult();
            rangeCondition = pv.getRangeCondition();

            acquisitionTimeMillis = pv.getAcquisitionTime();
        }
        generationTimeMillis = newValue.getGenerationTime();
    }

    public Instant generationTime() {
        return Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(generationTimeMillis));
    }

    public Instant acquisitionTime() {
        return Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(acquisitionTimeMillis));
    }

}
