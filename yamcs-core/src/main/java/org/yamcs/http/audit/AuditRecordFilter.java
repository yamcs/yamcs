package org.yamcs.http.audit;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.TimeInterval;

public class AuditRecordFilter {
    final TimeInterval interval;
    private String search;
    private List<String> services = new ArrayList<>();

    public AuditRecordFilter(TimeInterval interval) {
        this.interval = interval;
    }

    TimeInterval getTimeInterval() {
        return interval;
    }

    String getSearch() {
        return search;
    }

    public List<String> getServices() {
        return services;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public void addService(String service) {
        services.add(service);
    }

    public void setServices(List<String> services) {
        this.services = services;
    }
}
