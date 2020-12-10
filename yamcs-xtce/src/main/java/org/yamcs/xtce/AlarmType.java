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

    // if true it means that when the parameter is back within limits (RTN = return to normal) the alarm is cleared and
    // does not need an acknowledgement
    // FIXME: not supported by the excel or by XTCE
    private boolean autoAck;

    // if true it means that the alarm will stay triggered even when the parameter is back within limits.
    // FIXME: not supported by the excel or by XTCE.
    // However XTCE defines a minConformance attribute which is the number of times the parameter
    // is back within limits in order to clear the alarm.
    // This can be seen as a generalisation of latching: latching = true <=> minConformance = Infinite
    private boolean latching;

    public int getMinViolations() {
        return minViolations;
    }

    public void setMinViolations(int minViolations) {
        this.minViolations = minViolations;
    }

    public AlarmReportType getAlarmReportType() {
        return reportType;
    }

    public void setAlarmReportType(AlarmReportType reportType) {
        this.reportType = reportType;
    }

    /**
     * Latching means that the alarm will stay triggered even when the parameter is back within limits.
     *
     * @return
     */
    public boolean isLatching() {
        return latching;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    @Override
    public String toString() {
        return "AlarmType[reportType=" + reportType + ",minViolations=" + minViolations + "]";
    }

}
