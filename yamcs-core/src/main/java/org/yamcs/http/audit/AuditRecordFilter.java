package org.yamcs.http.audit;

import org.yamcs.utils.TimeInterval;

public class AuditRecordFilter {
    final TimeInterval interval;
    private String search;

    public AuditRecordFilter(TimeInterval interval) {
        this.interval = interval;
    }

    TimeInterval getTimeInterval() {
        return interval;
    }

    String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }
}
