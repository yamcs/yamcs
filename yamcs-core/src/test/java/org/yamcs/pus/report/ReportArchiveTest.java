package org.yamcs.pus.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

class ReportArchiveTest extends YarchTestCase {

    private static final long TIME = 1_800_000_000_000L;

    @BeforeAll
    static void configureTimeEncoding() {
        TimeEncoding.setUp();
    }

    @Test
    void persistsCheckpointsAndMakesFinalizationImmutable() {
        var archive = new ReportArchive(ydb);
        var snapshot = snapshot("attempt", "11-10", true).toBuilder().addReceivedPages(2).build();
        archive.save(snapshot, TIME, false);
        assertNull(archive.get("attempt"));
        assertEquals(List.of(), archive.list(null, null, null, null, null, 10, false));

        reloadDb();
        archive = new ReportArchive(ydb);
        assertEquals(List.of(2), archive.open().getFirst().getReceivedPagesList());
        archive.save(snapshot, TIME + 1, true);
        archive.save(snapshot.toBuilder().clearReceivedPages().build(), TIME + 2, false);
        archive.save(snapshot.toBuilder().clearReceivedPages().build(), TIME + 3, true);

        assertEquals(List.of(), archive.open());
        assertEquals(List.of(2), archive.get("attempt").getReceivedPagesList());
        assertEquals(TimeEncoding.toProtobufTimestamp(TIME + 1), archive.get("attempt").getSortTime());
        assertEquals(List.of("attempt"), ids(archive.list(null, null, null, null, null, 10, false)));
    }

    @Test
    void continuesInDescendingTimeAndIdOrderWithFilters() {
        var archive = new ReportArchive(ydb);
        archive.save(snapshot("a", "11-10", true), TIME, true);
        archive.save(snapshot("b", "11-10", true), TIME + 1, true);
        archive.save(snapshot("c", "11-10", true), TIME + 1, true);
        archive.save(snapshot("d", "11-10", false), TIME + 2, true);
        archive.save(snapshot("e", "11-10", true), TIME + 2, true);
        archive.save(snapshot("f", "11-13", true), TIME + 2, true);

        assertEquals(List.of("c"), ids(archive.list("11-10", TIME, TIME + 2, null, null, 1, false)));
        assertEquals(List.of("b", "a"), ids(archive.list("11-10", TIME, TIME + 2, TIME + 1, "c", 2, false)));
        assertEquals(List.of("e", "c", "b", "a"), ids(archive.list("11-10", null, null, null, null, 4, true)));
        assertEquals(List.of("e", "d", "c", "b", "a"), ids(archive.list("11-10", null, null, null, null, 6, false)));
    }

    @Test
    void readsAndFinalizesCheckpointsInAnOldArchivePartition() {
        ydb.executeUnchecked("create table satellite_reports"
                + " (id string, reportType string, target string, scope string, sortTime timestamp, observed boolean,"
                + " snapshot protobuf('" + ReportSnapshot.class.getName() + "'), primary key(id), index(sortTime))"
                + " partition by value(scope) table_format=compressed").close();
        var table = ydb.getTable("satellite_reports");
        var open = snapshot("old-attempt", "11-10", true).toBuilder()
                .setCommand(ReportCommand.newBuilder().setId("original-command")).build();
        var legacy = new Tuple(table.getTupleDefinition(), new Object[] {
                open.getId(), open.getType(), "legacy-label", "11-10:legacy-label",
                TimeEncoding.INVALID_INSTANT, true, open });
        var writer = ydb.getStorageEngine(table).newTableWriter(ydb, table, InsertMode.UPSERT);
        try {
            writer.onTuple(null, legacy);
        } finally {
            writer.close();
        }

        reloadDb();
        var archive = new ReportArchive(ydb);
        archive.save(archive.open().getFirst(), TIME, true);
        archive.save(snapshot("new-attempt", "11-10", true), TIME + 1, true);
        assertEquals(List.of(), archive.open());
        assertEquals("old-attempt", archive.get("old-attempt").getId());
        var sameRequest = open.toBuilder().setId("instance-scoped-id").build();
        assertEquals("old-attempt", archive.findLegacyAttempt(sameRequest, TIME - 1).getId());
        assertEquals(List.of("new-attempt", "old-attempt"),
                ids(archive.list("11-10", null, null, null, null, 10, false)));
    }

    private static ReportSnapshot snapshot(String id, String type, boolean observed) {
        var builder = ReportSnapshot.newBuilder().setId(id).setType(type);
        if (observed) {
            builder.setLastReceived(TimeEncoding.toProtobufTimestamp(TIME));
        }
        return builder.build();
    }

    private static List<String> ids(List<ReportSnapshot> snapshots) {
        return snapshots.stream().map(ReportSnapshot::getId).toList();
    }
}
