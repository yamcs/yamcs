package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.archive.TmPartitionGroups;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.archive.XtceTmReplayHandler;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.PacketsApiClient;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Tests the tm table partitioned by group, as configured for instance2 (see yamcs.instance2.yaml): the packets under
 * /REFMDB/ccsds-default go into the g1 group, the packets under /REFMDB/tm2 (received on the tm2_realtime stream) go
 * into the default group.
 */
public class TmPartitionGroupsIntegrationTest extends AbstractIntegrationTest {
    static final String INSTANCE = "instance2";
    // these two packet names are in the same group (g1)
    static final String PKT1_1 = "/REFMDB/SUBSYS1/PKT1_1";
    static final String CCSDS_DEFAULT = "/REFMDB/ccsds-default"; // the name of the PKT1_3 packets
    // in the default group
    static final String TM2 = "/REFMDB/tm2";

    static final String START = "2026-01-01T10:00:00";
    static final int N = 10;
    static boolean generated = false;

    @BeforeEach
    public void generate() {
        if (generated) {
            return;
        }
        generated = true;
        // the packet providers 2 and 3 send data to the tm_realtime and tm2_realtime streams of instance2
        var generator = PacketProvider.instance[2].mdbPacketGenerator;
        var generator2 = PacketProvider.instance[3].mdbPacketGenerator;
        long t0 = TimeEncoding.parse(START);
        for (int i = 0; i < N; i++) {
            generator.setGenerationTime(t0 + 1000 * i);
            generator.generate_PKT1_1();
            generator.generate_PKT1_3();
            generator2.setGenerationTime(t0 + 1000 * i);
            generator2.generate_TM2_PKT1();
        }
        generator.setGenerationTime(TimeEncoding.INVALID_INSTANT);
        generator2.setGenerationTime(TimeEncoding.INVALID_INSTANT);
    }

    @Test
    public void testTableDefinition() {
        var tblDef = ydb().getTable(XtceTmRecorder.TABLE_NAME);
        assertEquals(TmPartitionGroups.PGROUP_COLUMN, tblDef.getPartitioningSpec().valueColumn);
        List<String> keyColumns = new ArrayList<>();
        tblDef.getKeyDefinition().forEach(cd -> keyColumns.add(cd.getName()));
        assertEquals(List.of("gentime", "seqNum", "pname"), keyColumns);
        assertEquals(List.of("pname"), tblDef.getHistogramColumns());
    }

    @Test
    public void testRecordedGroups() throws Exception {
        Map<String, Set<String>> groups = new HashMap<>();
        var r = ydb().execute("select pname, pgroup from tm");
        while (r.hasNext()) {
            Tuple t = r.next();
            groups.computeIfAbsent(t.getColumn("pname"), k -> new HashSet<>()).add(t.getColumn("pgroup"));
        }
        r.close();
        assertEquals(Map.of(
                PKT1_1, Set.of("g1"),
                CCSDS_DEFAULT, Set.of("g1"),
                TM2, Set.of(TmPartitionGroups.DEFAULT_GROUP)), groups);
    }

    @Test
    public void testStreamPacketsByName() throws Exception {
        assertEquals(Set.of(PKT1_1), streamPacketNames(N, PKT1_1));
        assertEquals(Set.of(CCSDS_DEFAULT), streamPacketNames(N, CCSDS_DEFAULT));
        assertEquals(Set.of(TM2), streamPacketNames(N, TM2));
        assertEquals(Set.of(PKT1_1, TM2), streamPacketNames(2 * N, PKT1_1, TM2));
    }

    @Test
    public void testReplayHandler() throws Exception {
        var request = new ReplayOptions(TimeEncoding.parse(START), TimeEncoding.parse(START) + 1000 * N, false);
        request.setPacketRequest(PacketReplayRequest.newBuilder()
                .addNameFilter(NamedObjectId.newBuilder().setName(PKT1_1))
                .build());
        var handler = new XtceTmReplayHandler(INSTANCE, MdbFactory.getInstance(INSTANCE));
        handler.setRequest(request);
        var sqlb = handler.getSelectCmd();
        assertTrue(sqlb.toString().contains("pgroup"), sqlb.toString());

        int n = 0;
        var r = ydb().execute(sqlb.toString(), sqlb.getQueryArgumentsArray());
        while (r.hasNext()) {
            assertEquals(PKT1_1, r.next().getColumn("pname"));
            n++;
        }
        r.close();
        assertEquals(N, n);
    }

    /**
     * The user is allowed to read PKT1_1 which shares the g1 group with ccsds-default; it should not see the
     * ccsds-default packets.
     */
    @Test
    public void testPermissions() throws Exception {
        YamcsClient client = YamcsClient.newBuilder(yamcsHost, yamcsPort).build();
        try {
            client.login("pkt11reader", "password".toCharArray());
            var packetsApi = new PacketsApiClient(client.getMethodHandler());

            var request = ListPacketsRequest.newBuilder()
                    .setInstance(INSTANCE)
                    .setLimit(1000)
                    .build();
            CompletableFuture<ListPacketsResponse> f = new CompletableFuture<>();
            packetsApi.listPackets(null, request, new ResponseObserver<>(f));
            List<TmPacketData> packets = f.get().getPacketList();

            assertEquals(N, packets.size());
            for (var p : packets) {
                assertEquals(PKT1_1, p.getId().getName());
            }
        } finally {
            client.close();
        }
    }

    private Set<String> streamPacketNames(int expectedCount, String... names) throws Exception {
        ArchiveClient archiveClient = yamcsClient.createArchiveClient(INSTANCE);
        List<String> result = new ArrayList<>();
        Instant start = TimeEncoding.toJavaInstant(TimeEncoding.parse(START));
        archiveClient.streamPackets(p -> result.add(p.getId().getName()), start, start.plusSeconds(N),
                ArchiveClient.StreamOptions.packets(names)).get();
        assertEquals(expectedCount, result.size());
        return new HashSet<>(result);
    }

    private static YarchDatabaseInstance ydb() {
        return YarchDatabase.getInstance(INSTANCE);
    }
}
