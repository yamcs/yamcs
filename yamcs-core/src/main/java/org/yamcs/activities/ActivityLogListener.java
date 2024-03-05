package org.yamcs.activities;

@FunctionalInterface
public interface ActivityLogListener {

    void onLogRecord(Activity activity, ActivityLog log);
}
