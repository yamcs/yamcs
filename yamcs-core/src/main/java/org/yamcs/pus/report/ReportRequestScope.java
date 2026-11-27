package org.yamcs.pus.report;

import org.yamcs.protobuf.satellite.ReportCommand;

/** Operator-facing coverage of a PUS report request. */
final class ReportRequestScope {
    static String describe(int service, int subtype, ReportCommand command) {
        return switch (service) {
        case 11 -> switch (subtype) {
        case 9, 12 -> "Selected request IDs";
        case 11, 14 -> "Filtered schedule entries";
        case 16, 17 -> "All scheduled entries";
        case 18 -> "All sub-schedules";
        case 26 -> "All scheduling groups";
        default -> null;
        };
        case 15 -> switch (subtype) {
        case 5, 35, 37, 39 -> "Selected packet store";
        case 12 -> {
            if (command.getName().endsWith("/summary_report_content_of_all_packet_stores")) {
                yield "All packet stores";
            }
            yield hasSelection(command, "packet_store_ids") ? "Selected packet stores" : null;
        }
        case 18, 22 -> "All packet stores";
        default -> null;
        };
        case 130 -> switch (subtype) {
        case 7 -> hasSelection(command, "table_ids") ? "Selected tables" : null;
        case 13 -> hasSelection(command, "event_ids") ? "Selected event overlays" : null;
        case 15 -> "All event overlays";
        default -> null;
        };
        default -> null;
        };
    }

    private static boolean hasSelection(ReportCommand command, String name) {
        return command.getArgumentsList().stream()
                .anyMatch(argument -> argument.getName().equals(name) && argument.getValue().getArrayValueCount() > 0);
    }

    private ReportRequestScope() {
    }
}
