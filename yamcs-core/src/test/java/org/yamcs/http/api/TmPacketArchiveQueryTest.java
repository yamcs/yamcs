package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.archive.TmReceptionFixture;
import org.yamcs.archive.TmReceptionTimeIndex;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ServiceUnavailableException;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.PacketTimeBasis;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchTestCase;

public class TmPacketArchiveQueryTest extends YarchTestCase {
    @BeforeEach
    void fixture() throws Exception {
        TmReceptionFixture.create(ydb);
        TmReceptionFixture.seed(ydb);
        TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
    }

    private ListPacketsRequest.Builder request(String order) {
        return ListPacketsRequest.newBuilder().setInstance(instance).setTimeBasis(PacketTimeBasis.RECEPTION_TIME)
                .setOrder(order).setLimit(1);
    }

    private ListPacketsResponse list(ListPacketsRequest request, Set<String> names) throws Exception {
        try (var query = new TmPacketArchiveQuery(ydb,
                request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart()) : null,
                request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop()) : null,
                "asc".equals(request.getOrder()), names,
                request.hasLink() ? request.getLink() : null,
                request.hasFilter() ? request.getFilter() : null)) {
            return query.list(request, () -> false);
        }
    }

    @Test
    void paginationAcrossTiesAndPartitionsInBothDirections() throws Exception {
        for (String order : List.of("asc", "desc")) {
            var request = request(order);
            List<TmPacketData> packets = new ArrayList<>();
            ListPacketsResponse page;
            do {
                page = list(request.build(), Set.of("a", "b"));
                packets.addAll(page.getPacketsList());
                request.setNext(page.getContinuationToken());
            } while (page.hasContinuationToken());
            assertEquals(4, packets.size());
            assertEquals(4, packets.stream().map(p -> p.getId().getName() + p.getGenerationTime() + p.getSequenceNumber())
                    .distinct().count());
            var times = packets.stream().map(p -> TimeEncoding.fromProtobufTimestamp(p.getReceptionTime())).toList();
            assertEquals("asc".equals(order) ? List.of(TmReceptionFixture.BASE + 10, TmReceptionFixture.BASE + 20,
                    TmReceptionFixture.BASE + 30, TmReceptionFixture.BASE + 30)
                    : List.of(TmReceptionFixture.BASE + 30, TmReceptionFixture.BASE + 30,
                            TmReceptionFixture.BASE + 20, TmReceptionFixture.BASE + 10), times);
        }
    }

    @Test
    void selectedTimeBoundsNameLinkSearchAndExportParity() throws Exception {
        var request = request("asc").setLimit(100)
                .setStart(TimeEncoding.toProtobufTimestamp(TmReceptionFixture.BASE + 10))
                .setStop(TimeEncoding.toProtobufTimestamp(TmReceptionFixture.BASE + 30));
        assertEquals(2, list(request.build(), Set.of("a", "b")).getPacketsCount());
        request.setLink("live").setFilter("seqNumber = 2");
        var page = list(request.build(), Set.of("a"));
        assertEquals(1, page.getPacketsCount());
        assertTrue(TimeEncoding.fromProtobufTimestamp(page.getPackets(0).getGenerationTime())
                > TmReceptionFixture.BASE + 30); // It lies outside the reception window on the generation clock.
        List<Byte> bytes = new ArrayList<>();
        try (var query = new TmPacketArchiveQuery(ydb, TmReceptionFixture.BASE + 10, TmReceptionFixture.BASE + 30,
                true, Set.of("a"), "live", "seqNumber = 2")) {
            query.walk(tuple -> {
                bytes.add(((byte[]) tuple.getColumn("packet"))[0]);
                return true;
            }, () -> false);
        }
        assertEquals(List.of((byte) 2), bytes);
        assertEquals(0, list(request("desc").setLimit(100).build(), Set.of()).getPacketsCount());
        assertEquals(1, list(request("desc").setLimit(100).build(), Set.of("b")).getPacketsCount());
    }

    @Test
    void tokensRejectChangedQueriesButAllowDifferentPageSizes() throws Exception {
        var request = request("desc").setStop(TimeEncoding.toProtobufTimestamp(TmReceptionFixture.BASE + 40));
        var first = list(request.build(), Set.of("a", "b"));
        request.setNext(first.getContinuationToken());
        assertEquals(3, list(request.setLimit(3).build(), Set.of("a", "b")).getPacketsCount());
        var changed = request.clone().clearStop().build();
        assertThrows(BadRequestException.class, () -> list(changed, Set.of("a", "b")));
        assertThrows(BadRequestException.class,
                () -> list(request.clone().setOrder("asc").build(), Set.of("a", "b")));
        assertThrows(BadRequestException.class,
                () -> list(request.clone().setLink("live").build(), Set.of("a", "b")));
        assertThrows(BadRequestException.class,
                () -> list(request.clone().setNext("not a token").build(), Set.of("a", "b")));
        TmReceptionTimeIndex.rebuild(ydb).get(30, TimeUnit.SECONDS);
        assertThrows(BadRequestException.class, () -> list(request.build(), Set.of("a", "b")));
    }

    @Test
    void cancelledQueryEmitsNothing() throws Exception {
        try (var query = new TmPacketArchiveQuery(ydb, null, null, false, Set.of("a", "b"), null, null)) {
            query.walk(tuple -> { fail("Cancelled query emitted a row"); return true; }, () -> true);
        }
    }

    @Test
    void unavailableIndexNeverFallsBackToScan() throws Exception {
        execute("drop table tm");
        TmReceptionFixture.create(ydb);
        assertThrows(ServiceUnavailableException.class,
                () -> new TmPacketArchiveQuery(ydb, null, null, false, Set.of("a"), null, null));
    }
}
