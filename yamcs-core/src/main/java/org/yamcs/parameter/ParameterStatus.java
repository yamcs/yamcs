package org.yamcs.parameter;

import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.xtce.FloatRange;

public class ParameterStatus {
    public static final ParameterStatus NOMINAL = new ParameterStatus();
    
    private AcquisitionStatus acquisitionStatus = AcquisitionStatus.ACQUIRED;
    private boolean processingStatus = true;
    private MonitoringResult monitoringResult;
    private MonitoringResult deltaMonitoringResult;
    private RangeCondition rangeCondition;

    private FloatRange watchRange;
    private FloatRange warningRange;
    private FloatRange distressRange;
    private FloatRange criticalRange;
    private FloatRange severeRange;
    
    public AcquisitionStatus getAcquisitionStatus() {
        return acquisitionStatus;
    }
    public void setAcquisitionStatus(AcquisitionStatus acquisitionStatus) {
        this.acquisitionStatus = acquisitionStatus;
    }
    public boolean getProcessingStatus() {
        return processingStatus;
    }
    public void setProcessingStatus(boolean processingStatus) {
        this.processingStatus = processingStatus;
    }
    public MonitoringResult getMonitoringResult() {
        return monitoringResult;
    }
    public void setMonitoringResult(MonitoringResult monitoringResult) {
        this.monitoringResult = monitoringResult;
    }
    public MonitoringResult getDeltaMonitoringResult() {
        return deltaMonitoringResult;
    }
    public void setDeltaMonitoringResult(MonitoringResult deltaMonitoringResult) {
        this.deltaMonitoringResult = deltaMonitoringResult;
    }
    public RangeCondition getRangeCondition() {
        return rangeCondition;
    }
    public void setRangeCondition(RangeCondition rangeCondition) {
        this.rangeCondition = rangeCondition;
    }
    public FloatRange getWatchRange() {
        return watchRange;
    }
    public void setWatchRange(FloatRange watchRange) {
        this.watchRange = watchRange;
    }
    public FloatRange getWarningRange() {
        return warningRange;
    }
    public void setWarningRange(FloatRange warningRange) {
        this.warningRange = warningRange;
    }
    public FloatRange getDistressRange() {
        return distressRange;
    }
    public void setDistressRange(FloatRange distressRange) {
        this.distressRange = distressRange;
    }
    public FloatRange getCriticalRange() {
        return criticalRange;
    }
    public void setCriticalRange(FloatRange criticalRange) {
        this.criticalRange = criticalRange;
    }
    public FloatRange getSevereRange() {
        return severeRange;
    }
    public void setSevereRange(FloatRange severeRange) {
        this.severeRange = severeRange;
    }
   
}
