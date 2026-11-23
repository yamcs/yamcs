package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ReportPage;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.protobuf.satellite.ReportStatus;
import org.yamcs.security.ObjectPrivilege;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Timestamp;

class SatelliteReportsApiTest {
    @BeforeAll
    static void initTime() {
        TimeEncoding.setUp();
    }

    @Test
    void commandLinksRequireSeparateHistoryPermissionForEachCommand() {
        var user = new User("operator", null);
        user.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.CommandHistory, "/Mission/request"), false);
        var report = ReportSnapshot.newBuilder().setEcho("operator-id").addRejectedPages(2)
                .setCommand(command("/Mission/request"))
                .addPages(ReportPage.newBuilder().addRows(ReportRow.newBuilder()
                        .setScheduledCommand(command("/Mission/restricted"))))
                .build();

        var filtered = SatelliteReportsApi.redact(user, report);
        assertTrue(filtered.hasCommand());
        assertFalse(filtered.getPages(0).getRows(0).hasScheduledCommand());
        assertFalse(filtered.hasEcho());
        assertEquals(2, filtered.getRejectedPages(0));
    }

    @Test
    void commandOnlyAttemptRequiresCommandAccess() {
        var user = new User("packet-reader", null);
        var report = ReportSnapshot.newBuilder().setStatus(ReportStatus.NO_RESPONSE)
                .setCommand(command("/Mission/request")).build();
        assertFalse(SatelliteReportsApi.isVisible(user, report));

        var received = report.toBuilder().setLastReceived(Timestamp.newBuilder().setSeconds(1))
                .setRequestedAt(Timestamp.newBuilder().setSeconds(1)).build();
        assertTrue(SatelliteReportsApi.isVisible(user, received));
        var filtered = SatelliteReportsApi.redact(user, received);
        assertFalse(filtered.hasCommand());
        assertFalse(filtered.hasRequestedAt());
    }

    @Test
    void archivedPayloadAuthorizationUsesOriginalPacketContainer() {
        var user = new User("new-profile-reader", null);
        user.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.ReadPacket, "/Mission/PublicNow"), false);
        var existing = ReportSnapshot.newBuilder().setPacketName("/Mission/OldRestricted").build();
        assertFalse(SatelliteReportsApi.canReadSnapshot(user, existing));
        assertTrue(SatelliteReportsApi.canReadSnapshot(user,
                existing.toBuilder().setPacketName("/Mission/PublicNow").build()));
        assertFalse(SatelliteReportsApi.canReadSnapshot(user, existing.toBuilder().clearPacketName().build()));
    }

    private static ReportCommand command(String name) {
        return ReportCommand.newBuilder().setName(name).setId("command-id").build();
    }
}
