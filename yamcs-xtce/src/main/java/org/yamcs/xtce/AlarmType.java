package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Base type for alarms
 */
public abstract class AlarmType implements Serializable {
    
    private static final long serialVersionUID = 7443202826018275789L;
    public static final AlarmReportType DEFAULT_REPORT_TYPE = AlarmReportType.ON_SEVERITY_CHANGE;
    
    private AlarmReportType reportType = DEFAULT_REPORT_TYPE; // When alarms should be reported (not in XTCE)
    private int minViolations = 1;
    
    public int getMinViolations() {
        return minViolations;
    }
    
    public void setMinViolations(int minViolations) {
        this.minViolations=minViolations;
    }
    
    public AlarmReportType getAlarmReportType() {
        return reportType;
    }
    
    public void setAlarmReportType(AlarmReportType reportType) {
        this.reportType=reportType;
    }
    
    @Override
    public String toString() {
        return "AlarmType[reportType="+reportType+",minViolations="+minViolations+"]";
    }
}
