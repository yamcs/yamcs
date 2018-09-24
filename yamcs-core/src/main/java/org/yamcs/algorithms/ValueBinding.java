package org.yamcs.algorithms;

import java.util.Calendar;
import java.util.Date;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.utils.TimeEncoding;

/**
 * A ParameterValue as passed to an algorithm. Actual implementations are generated on-the-fly to walk around the issue
 * of Rhino that maps boxed primitives to JavaScript Objects instead of Numbers
 */
public abstract class ValueBinding {

    public Date acquisitionTime;
    public long acquisitionTimeMs;
    public Date generationTime;
    public long generationTimeMs;
    public AcquisitionStatus acquisitionStatus;
    public MonitoringResult monitoringResult;
    public RangeCondition rangeCondition;

    public void updateValue(ParameterValue newValue) {
        acquisitionStatus = newValue.getAcquisitionStatus();
        monitoringResult = newValue.getMonitoringResult();
        rangeCondition = newValue.getRangeCondition();

        if (newValue.hasAcquisitionTime()) {
            Calendar cal = TimeEncoding.toCalendar(newValue.getAcquisitionTime());
            acquisitionTime = cal.getTime();
        }
        acquisitionTimeMs = newValue.getAcquisitionTime();

        if (newValue.hasGenerationTime()) {
            Calendar cal = TimeEncoding.toCalendar(newValue.getGenerationTime());
            generationTime = cal.getTime();
        }
        generationTimeMs = newValue.getGenerationTime();
    }
}
