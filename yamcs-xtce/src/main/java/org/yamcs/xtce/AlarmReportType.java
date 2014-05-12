package org.yamcs.xtce;

/**
 * Allow control over when alarms are reported.
 * NOT IN XTCE 
 */
public enum AlarmReportType {
    ON_SEVERITY_CHANGE,
    ON_VALUE_CHANGE; // When value changes (not when parameter updates)
}
