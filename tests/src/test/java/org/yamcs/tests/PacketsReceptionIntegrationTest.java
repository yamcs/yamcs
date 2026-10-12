package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.yamcs.archive.TmReceptionTimeIndex;
import org.yamcs.client.BasicAuthCredentials;
import org.yamcs.client.base.HttpClient;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.GetPacketArchiveInfoRequest;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.PacketArchiveInfo;
import org.yamcs.protobuf.PacketTimeBasis;
import org.yamcs.protobuf.PacketsApiClient;
import org.yamcs.protobuf.RebuildReceptionTimeIndexRequest;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

/** Exercises the actual HTTP routes and protobuf transport, without running unrelated integration cases. */
public class PacketsReceptionIntegrationTest extends AbstractIntegrationTest {
    private PacketsApiClient api() {
        return new PacketsApiClient(yamcsClient.getMethodHandler());
    }

    private PacketArchiveInfo info() throws Exception {
        var future = new CompletableFuture<PacketArchiveInfo>();
        api().getPacketArchiveInfo(null, GetPacketArchiveInfoRequest.newBuilder().setInstance(yamcsInstance).build(),
                new ResponseObserver<>(future));
        return future.get(10, TimeUnit.SECONDS);
    }

    private ListPacketsResponse list(ListPacketsRequest request) throws Exception {
        var future = new CompletableFuture<ListPacketsResponse>();
        api().listPackets(null, request, new ResponseObserver<>(future));
        return future.get(10, TimeUnit.SECONDS);
    }

    private void put(long generation, int seq, long reception, String name) {
        var database = YarchDatabase.getInstance(yamcsInstance);
        var table = database.getTable("tm");
        var tuple = new Tuple();
        tuple.addTimestampColumn("gentime", generation);
        tuple.addColumn("seqNum", org.yamcs.yarch.DataType.INT, seq);
        tuple.addTimestampColumn("rectime", reception);
        tuple.addEnumColumn("pname", name);
        tuple.addColumn("packet", org.yamcs.yarch.DataType.BINARY, new byte[] { (byte) seq });
        tuple.addEnumColumn("link", "test-link");
        var writer = RdbStorageEngine.getInstance().getTablespace(database).newTableWriter(database, table, InsertMode.INSERT);
        try {
            writer.onTuple(null, tuple);
        } finally {
            writer.close();
        }
    }

    @Test
    public void receptionRoutesPaginationExportAndLegacyGeneration() throws Exception {
        long base = TimeEncoding.parse("2024-01-01T00:00:00Z");
        String a = "/reception-test/a";
        String b = "/reception-test/b";
        put(base, 1, base + 30, a);
        put(base, 1, base + 30, b);
        put(base + 40L * 86400000, 2, base + 10, a);
        put(base + 1, 3, base + 20, a);
        assertEquals(PacketArchiveInfo.IndexState.ABSENT, info().getReceptionTimeIndexState());
        var request = ListPacketsRequest.newBuilder().setInstance(yamcsInstance)
                .setTimeBasis(PacketTimeBasis.RECEPTION_TIME).setOrder("asc").setLimit(1)
                .addName(a).addName(b)
                .setStart(TimeEncoding.toProtobufTimestamp(base))
                .setStop(TimeEncoding.toProtobufTimestamp(base + 40));
        var unavailable = assertThrows(ExecutionException.class, () -> list(request.build()));
        assertTrue(unavailable.getCause().getMessage().contains("ABSENT"));

        var rebuilding = new CompletableFuture<PacketArchiveInfo>();
        api().rebuildReceptionTimeIndex(null,
                RebuildReceptionTimeIndexRequest.newBuilder().setInstance(yamcsInstance).build(),
                new ResponseObserver<>(rebuilding));
        rebuilding.get(10, TimeUnit.SECONDS);
        TmReceptionTimeIndex.get(YarchDatabase.getInstance(yamcsInstance)).rebuild(false).get(30, TimeUnit.SECONDS);
        assertEquals(PacketArchiveInfo.IndexState.READY, info().getReceptionTimeIndexState());

        List<TmPacketData> packets = new ArrayList<>();
        ListPacketsResponse page;
        do {
            page = list(request.build());
            packets.addAll(page.getPacketsList());
            request.setNext(page.getContinuationToken());
        } while (page.hasContinuationToken());
        assertEquals(List.of(base + 10, base + 20, base + 30, base + 30),
                packets.stream().map(p -> TimeEncoding.fromProtobufTimestamp(p.getReceptionTime())).toList());
        assertEquals(4, packets.stream().map(p -> p.getId().getName() + p.getSequenceNumber() + p.getGenerationTime())
                .distinct().count());

        var raw = new HttpClient();
        raw.setCredentials(new BasicAuthCredentials(adminUsername, adminPassword));
        try {
            var url = new io.netty.handler.codec.http.QueryStringEncoder("http://localhost:9190/api/archive/"
                    + yamcsInstance + ":exportPackets");
            url.addParam("timeBasis", "RECEPTION_TIME");
            url.addParam("order", "asc");
            url.addParam("name", a);
            url.addParam("name", b);
            url.addParam("start", TimeEncoding.toString(base));
            url.addParam("stop", TimeEncoding.toString(base + 40));
            assertArrayEquals(new byte[] { 2, 3, 1, 1 }, raw.doAsyncRequest(url.toString(),
                    io.netty.handler.codec.http.HttpMethod.GET, null).get(10, TimeUnit.SECONDS));
        } finally {
            raw.close();
        }

        var generationRequest = request.clone().clearTimeBasis().clearNext().setLimit(100).build();
        var generationPage = list(generationRequest);
        assertEquals(3, generationPage.getPacketsCount());
        assertEquals(base, TimeEncoding.fromProtobufTimestamp(generationPage.getPackets(0).getGenerationTime()));

        var first = list(request.clone().clearNext().build());
        assertThrows(ExecutionException.class,
                () -> list(request.clone().setNext(first.getContinuationToken()).clearStop().build()));
    }
}
