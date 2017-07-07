package org.yamcs.parameter;

import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.utils.DoubleRange;

public class ParameterStatus {
    public static final ParameterStatus NOMINAL = new ParameterStatus();
    
    private AcquisitionStatus acquisitionStatus = AcquisitionStatus.ACQUIRED;
    private boolean processingStatus = true;
    private MonitoringResult monitoringResult;
    private MonitoringResult deltaMonitoringResult;
    private RangeCondition rangeCondition;

    private DoubleRange watchRange;
    private DoubleRange warningRange;
    private DoubleRange distressRange;
    private DoubleRange criticalRange;
    private DoubleRange severeRange;
    
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
    public DoubleRange getWatchRange() {
        return watchRange;
    }
    public void setWatchRange(DoubleRange watchRange) {
        this.watchRange = watchRange;
    }
    public DoubleRange getWarningRange() {
        return warningRange;
    }
    public void setWarningRange(DoubleRange warningRange) {
        this.warningRange = warningRange;
    }
    public DoubleRange getDistressRange() {
        return distressRange;
    }
    public void setDistressRange(DoubleRange distressRange) {
        this.distressRange = distressRange;
    }
    public DoubleRange getCriticalRange() {
        return criticalRange;
    }
    public void setCriticalRange(DoubleRange criticalRange) {
        this.criticalRange = criticalRange;
    }
    public DoubleRange getSevereRange() {
        return severeRange;
    }
    public void setSevereRange(DoubleRange severeRange) {
        this.severeRange = severeRange;
    }
   
}
