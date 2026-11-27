package org.yamcs.pus.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.satellite.ReportCell;
import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.protobuf.satellite.ReportStatus;
import org.yamcs.utils.TimeEncoding;

class ReportAssemblerTest {
    @BeforeAll
    static void initTime() {
        TimeEncoding.setUp();
    }

    @Test
    void assemblesPagesInPageOrderWithActualCompletionTime() {
        var attempt = attempt("a", 100, null);
        attempt.accept(page(2, 2, 115, "second"));
        attempt.accept(page(1, 2, 120, "first"));

        var snapshot = attempt.finish(200, false);
        assertEquals(ReportStatus.COMPLETE, snapshot.getStatus());
        assertEquals(List.of(1, 2), snapshot.getReceivedPagesList());
        assertEquals("first", snapshot.getPages(0).getRows(0).getCells(0).getLabel());
        assertEquals(TimeEncoding.toProtobufTimestamp(120), snapshot.getAssembledAt());
    }

    @Test
    void acceptsEmptyReportsOnlyAsOneOfOne() {
        var empty = attempt("a", 100, null);
        empty.accept(new ReportAssembler.Page(1, 1, 115, 115, 16, 7, List.of(), null));
        assertEquals(ReportStatus.COMPLETE, empty.finish(200, false).getStatus());

        var invalid = attempt("b", 100, null);
        invalid.accept(new ReportAssembler.Page(1, 2, 115, 115, 16, 7, List.of(), null));
        invalid.accept(new ReportAssembler.Page(2, 2, 120, 120, 16, 8, List.of(), null));
        assertEquals(ReportStatus.PARTIAL, invalid.finish(200, false).getStatus());
    }

    @Test
    void listsMissingPagesWhenFirstPageNeverArrived() {
        var attempt = attempt("a", 100, null);
        attempt.accept(page(2, 3, 101, "middle"));

        var snapshot = attempt.finish(200, false);
        assertEquals(ReportStatus.PARTIAL, snapshot.getStatus());
        assertEquals(List.of(1, 3), snapshot.getMissingPagesList());
    }

    @Test
    void withholdsSharedPagesAcrossOverlappingRequests() {
        var a = attempt("a", 100, null);
        var b = attempt("b", 110, null);
        var first = page(1, 2, 105, "only A");
        assertTrue(a.matches("11-10", first.receptionTime(), null, 0));
        assertFalse(b.matches("11-10", first.receptionTime(), null, 0));
        a.accept(first);
        var shared = page(2, 2, 115, "unknown");
        for (var attempt : List.of(a, b)) {
            assertTrue(attempt.matches("11-10", shared.receptionTime(), null, 0));
            attempt.withhold(shared.receptionTime());
            assertEquals(ReportStatus.AMBIGUOUS, attempt.finish(210, false).getStatus());
        }
        assertEquals(List.of(1), a.finish(210, false).getReceivedPagesList());
        assertEquals(List.of(), b.finish(210, false).getReceivedPagesList());
    }

    @Test
    void echoDisambiguatesInterleavedPages() {
        var attempt = attempt("a", 100, "id-A");
        assertTrue(attempt.matches("11-10", 110, "id-A", 0));
        assertFalse(attempt.matches("11-10", 110, "id-B", 0));
        assertFalse(attempt.matches("11-10", 110, null, 0));
        assertFalse(attempt.matches("11-13", 110, "id-A", 0));
    }

    @Test
    void quarantinesAConflictingRetransmission() {
        var attempt = attempt("a", 100, null);
        attempt.accept(page(1, 1, 101, "first"));
        attempt.accept(page(1, 1, 102, "first"));
        assertEquals(ReportStatus.COMPLETE, attempt.finish(200, false).getStatus());
        attempt.accept(page(1, 1, 103, "conflict"));
        var snapshot = attempt.finish(200, false);
        assertEquals(ReportStatus.PARTIAL, snapshot.getStatus());
        assertEquals(List.of(1), snapshot.getMissingPagesList());
        assertEquals(0, snapshot.getPagesCount());
    }

    @Test
    void preservesAnInterruptedCheckpointWithoutCallingItComplete() {
        var attempt = attempt("a", 100, null);
        attempt.accept(page(1, 1, 108, "row"));
        var recovered = new ReportAssembler.Attempt(attempt.checkpoint());
        var snapshot = recovered.finish(150, true);
        assertEquals(ReportStatus.PARTIAL, snapshot.getStatus());
        assertEquals(List.of(1), snapshot.getReceivedPagesList());
        assertFalse(snapshot.getIssuesList().isEmpty());
    }

    @Test
    void aFastResponseCanPrecedeTheSentAcknowledgement() {
        var attempt = attempt("a", 100, null);
        assertTrue(attempt.matches("11-10", 97, null, 5));
        assertFalse(attempt.matches("11-10", 94, null, 5));
        assertFalse(attempt.matches("11-10", 200, null, 5));
    }

    private static ReportAssembler.Attempt attempt(String id, long sent, String echo) {
        return new ReportAssembler.Attempt("11-10", "test", "/test/report", ReportCommand.newBuilder().setId(id).build(),
                sent, sent + 100, echo);
    }

    private static ReportAssembler.Page page(int number, int total, long received, String content) {
        var row = ReportRow.newBuilder().addCells(ReportCell.newBuilder().setLabel(content)).build();
        return new ReportAssembler.Page(number, total, received, received, 16, 1, List.of(row), null);
    }
}
