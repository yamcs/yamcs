package org.yamcs.algorithms;

import java.util.Date;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.utils.TimeEncoding;

/**
 * A ParameterValue as passed to an algorithm. Actual implementations are
 * generated on-the-fly to walk around the issue of Rhino that maps
 * boxed primitives to JavaScript Objects instead of Numbers
 */
public abstract class ValueBinding {
    
    public Date acquisitionTime;
    public Date generationTime;
    public AcquisitionStatus acquisitionStatus;
    public MonitoringResult monitoringResult;

    public void updateValue(ParameterValue newValue) {
        acquisitionStatus = newValue.getAcquisitionStatus();
        monitoringResult = newValue.getMonitoringResult();
        acquisitionTime = TimeEncoding.toCalendar(newValue.getAcquisitionTime()).getTime();
        generationTime = TimeEncoding.toCalendar(newValue.getGenerationTime()).getTime();
    }
}
