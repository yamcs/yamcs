package org.yamcs.parameter;

import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.xtce.util.DoubleRange;

public class ParameterStatus {
    public static final ParameterStatus NOMINAL = new ParameterStatus();


    private AcquisitionStatus acquisitionStatus = AcquisitionStatus.ACQUIRED;
    private MonitoringResult monitoringResult;
    private MonitoringResult deltaMonitoringResult;
    private RangeCondition rangeCondition;

    private DoubleRange watchRange;
    private DoubleRange warningRange;
    private DoubleRange distressRange;
    private DoubleRange criticalRange;
    private DoubleRange severeRange;

    //-1 means it's not set.
    private long expireMillis = -1;

    public AcquisitionStatus getAcquisitionStatus() {
        return acquisitionStatus;
    }
    public void setAcquisitionStatus(AcquisitionStatus acquisitionStatus) {
        this.acquisitionStatus = acquisitionStatus;
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
    public void setExpireMillis(long em) {
        this.expireMillis = em;
    }

    public long getExpireMills() {
        return expireMillis;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((acquisitionStatus == null) ? 0 : acquisitionStatus.hashCode());
        result = prime * result + ((criticalRange == null) ? 0 : criticalRange.hashCode());
        result = prime * result + ((deltaMonitoringResult == null) ? 0 : deltaMonitoringResult.hashCode());
        result = prime * result + ((distressRange == null) ? 0 : distressRange.hashCode());
        result = prime * result + (int) (expireMillis ^ (expireMillis >>> 32));
        result = prime * result + ((monitoringResult == null) ? 0 : monitoringResult.hashCode());
        result = prime * result + ((rangeCondition == null) ? 0 : rangeCondition.hashCode());
        result = prime * result + ((severeRange == null) ? 0 : severeRange.hashCode());
        result = prime * result + ((warningRange == null) ? 0 : warningRange.hashCode());
        result = prime * result + ((watchRange == null) ? 0 : watchRange.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParameterStatus other = (ParameterStatus) obj;
        if (acquisitionStatus != other.acquisitionStatus)
            return false;
        if (criticalRange == null) {
            if (other.criticalRange != null)
                return false;
        } else if (!criticalRange.equals(other.criticalRange))
            return false;
        if (deltaMonitoringResult != other.deltaMonitoringResult)
            return false;
        if (distressRange == null) {
            if (other.distressRange != null)
                return false;
        } else if (!distressRange.equals(other.distressRange))
            return false;
        if (expireMillis != other.expireMillis)
            return false;
        if (monitoringResult != other.monitoringResult)
            return false;
        if (rangeCondition != other.rangeCondition)
            return false;
        if (severeRange == null) {
            if (other.severeRange != null)
                return false;
        } else if (!severeRange.equals(other.severeRange))
            return false;
        if (warningRange == null) {
            if (other.warningRange != null)
                return false;
        } else if (!warningRange.equals(other.warningRange))
            return false;
        if (watchRange == null) {
            if (other.watchRange != null)
                return false;
        } else if (!watchRange.equals(other.watchRange))
            return false;
        return true;
    }


    public org.yamcs.protobuf.Pvalue.ParameterStatus toProtoBuf() {
        org.yamcs.protobuf.Pvalue.ParameterStatus.Builder b = org.yamcs.protobuf.Pvalue.ParameterStatus.newBuilder();
        if (acquisitionStatus != null) {
            b.setAcquisitionStatus(acquisitionStatus);
        }

        if (monitoringResult != null) {
            b.setMonitoringResult(monitoringResult);
        }
        if (rangeCondition != null) {
            b.setRangeCondition(rangeCondition);
        }

        if(expireMillis!=-1) {
            b.setExpireMillis(expireMillis);
        }

        addAlarmRange(b, AlarmLevelType.WATCH, watchRange);
        addAlarmRange(b, AlarmLevelType.WARNING, warningRange);
        addAlarmRange(b, AlarmLevelType.DISTRESS, distressRange);
        addAlarmRange(b, AlarmLevelType.CRITICAL, criticalRange);
        addAlarmRange(b, AlarmLevelType.SEVERE, severeRange);
        
        return b.build();
    }
    
    private static void addAlarmRange(org.yamcs.protobuf.Pvalue.ParameterStatus.Builder pvfb, AlarmLevelType level, DoubleRange range) {
        if (range == null) {
            return;
        }
        pvfb.addAlarmRange(BasicParameterValue.toGpbAlarmRange(level, range));
    }
}
