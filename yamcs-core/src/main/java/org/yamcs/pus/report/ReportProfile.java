package org.yamcs.pus.report;

/** A report's wire identity, operator-facing title, and MDB container. */
public record ReportProfile(String type, String title, int service, int subtype, String containerName,
        String commandArgument, String commandHistoryAttribute) {
}
