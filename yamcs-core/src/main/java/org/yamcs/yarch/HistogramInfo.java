package org.yamcs.yarch;

/**
 * 
 * information about histogram.
 * Only the column name is kept here - subclasses can store more information if required.
 *
 */
public class HistogramInfo {
    protected final String columnName;
    public HistogramInfo(String columnName) {
        this.columnName = columnName;
    }
}
