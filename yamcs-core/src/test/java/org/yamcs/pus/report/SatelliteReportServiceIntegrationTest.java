package org.yamcs.pus.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.satellite.ReportStatus;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

class SatelliteReportServiceIntegrationTest {
    @TempDir
    static Path data;
    static YamcsServer server;
    static int port;

    @BeforeAll
    static void startYamcs() throws Exception {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        System.setProperty("satelliteReports.testData", data.toString());
        System.setProperty("satelliteReports.testPort", Integer.toString(port));
        YConfiguration.setupTest("SatelliteReports");
        server = YamcsServer.getServer();
        server.prepareStart();
        server.start();
    }

    @AfterAll
    static void stopYamcs() {
        try {
            if (server != null) {
                server.shutDown();
            }
        } finally {
            System.clearProperty("satelliteReports.testData");
            System.clearProperty("satelliteReports.testPort");
        }
    }

    @Test
    void archivesRealStreamResponsesAndServesLatestAndHistory() throws Exception {
        var instance = server.getInstance("reporttest");
        var time = instance.getTimeService();
        var history = new StreamCommandHistoryPublisher("reporttest");
        var stream = YarchDatabase.getInstance("reporttest").getStream("tm_realtime");
        var collector = instance.getServices(SatelliteReportService.class).getFirst();
        var start = time.getMissionTime();
        var first = request(history, start, 1);
        // The physical TM can precede the link's sent acknowledgement.
        stream.emitTuple(statusPage(time.getMissionTime(), 2, 2, 22));
        sent(history, first, time.getMissionTime());
        stream.emitTuple(statusPage(time.getMissionTime(), 1, 2, 11));
        awaitReports(collector, "11-19", 1);

        long later = time.getMissionTime();
        sent(history, request(history, later, 2), later);
        sent(history, request(history, later, 3), later);
        stream.emitTuple(statusPage(time.getMissionTime(), 1, 1, 99));
        awaitReports(collector, "11-19", 3);

        try (var client = HttpClient.newHttpClient()) {
            var enabled = json(client, "/api/instances/reporttest").getAsJsonArray("capabilities");
            assertTrue(enabled.asList().stream().anyMatch(x -> x.getAsString().equals("satellite-reports")));
            var types = json(client, "/api/satellite-reports/reporttest/types");
            assertEquals(17, types.getAsJsonArray("types").size());
            var latest = types.getAsJsonArray("types").asList().stream().map(x -> x.getAsJsonObject())
                    .filter(x -> x.get("type").getAsString().equals("11-19"))
                    .findFirst().orElseThrow().getAsJsonObject("latest");
            assertEquals("AMBIGUOUS", latest.get("status").getAsString());
            assertEquals(1, latest.get("withheldPackets").getAsInt());
            assertTrue(!latest.has("pages") || latest.getAsJsonArray("pages").size() == 0);

            String reports = "/api/satellite-reports/reporttest/reports";
            var page = json(client, reports + "?type=11-19&limit=1");
            assertEquals(1, page.getAsJsonArray("reports").size());
            String token = page.get("continuationToken").getAsString();
            var rest = json(client, reports + "?type=11-19&limit=5&next="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8));
            assertEquals(2, rest.getAsJsonArray("reports").size());
            var completed = rest.getAsJsonArray("reports").get(1).getAsJsonObject();
            assertEquals("COMPLETE", completed.get("status").getAsString());
            var firstPage = completed.getAsJsonArray("pages").get(0).getAsJsonObject();
            assertEquals(new JsonPrimitive(1), firstPage.get("pageNumber"));
            assertEquals(new JsonPrimitive(16), firstPage.get("apid"));
            assertEquals(new JsonPrimitive(11), firstPage.get("sourceSequenceCount"));
            assertFalse(firstPage.has("sequence"));
            assertEquals("/SatelliteReportTest/report", completed.getAsJsonObject("command").get("name").getAsString());
            assertTrue(completed.has("assembledAt"));
            assertFalse(completed.has("target"));
            assertEquals(completed, json(client, reports + "/" + completed.get("id").getAsString()));
            var otherCatalog = json(client, "/api/satellite-reports/other/types").getAsJsonArray("types");
            assertEquals(17, otherCatalog.size());
            assertTrue(otherCatalog.asList().stream().noneMatch(item -> item.getAsJsonObject().has("latest")));
            assertEquals(404, get(client, "/api/satellite-reports/other/reports/"
                    + completed.get("id").getAsString()).statusCode());
            assertEquals(404, get(client, "/api/satellite-reports/disabled/types").statusCode());
        }
        long late = time.getMissionTime();
        sent(history, request(history, late, 4), late);
        // This page was received before the deadline, but its stream callback arrives after it.
        Thread.sleep(550);
        stream.emitTuple(statusPage(late + 490, 1, 1, 4));
        awaitReports(collector, "11-19", 4);
        var finalAttempt = collector.archive().list("11-19", null, null, null, null, 1, false).getFirst();
        assertEquals(ReportStatus.COMPLETE, finalAttempt.getStatus());

        long scheduleTime = time.getMissionTime();
        var scheduled = request(history, scheduleTime, 5);
        history.publish(scheduled.getCommandId(), "pus11Binary", new byte[15]);
        sent(history, scheduled, scheduleTime);
        long executionTime = time.getMissionTime();
        history.publishAck(scheduled.getCommandId(), "Verifier_OriginalExecution", executionTime, AckStatus.OK);
        stream.emitTuple(statusPage(time.getMissionTime(), 1, 1, 5));
        awaitReports(collector, "11-19", 5);
        var scheduledReport = collector.archive().list("11-19", null, null, null, null, 1, false).getFirst();
        assertEquals(ReportStatus.COMPLETE, scheduledReport.getStatus());
        assertEquals(scheduled.getId(), scheduledReport.getCommand().getId());

        long issuedAt = time.getMissionTime();
        var direct = request(history, issuedAt, 6, 15, 22);
        history.publish(direct.getCommandId(), "fixtureRequestId", 101);
        sent(history, direct, issuedAt);
        var wrapped = request(history, issuedAt, 7, 15, 22);
        history.publish(wrapped.getCommandId(), "pus11Binary", new byte[15]);
        history.publish(wrapped.getCommandId(), "fixtureRequestId", 102);
        sent(history, wrapped, time.getMissionTime());
        stream.emitTuple(storePage(time.getMissionTime(), 1, 2, 101, 10));
        stream.emitTuple(storePage(time.getMissionTime(), 1, 1, 102, 20));
        stream.emitTuple(storePage(time.getMissionTime(), 2, 2, 101, 11));
        awaitReports(collector, "15-23", 2);
        var echoed = collector.archive().list("15-23", null, null, null, null, 10, false);
        assertTrue(echoed.stream().allMatch(report -> report.getStatus() == ReportStatus.COMPLETE));
        assertEquals(2, echoed.stream().filter(report -> report.getCommand().getId().equals(direct.getId()))
                .findFirst().orElseThrow().getPagesCount());
        assertEquals(1, echoed.stream().filter(report -> report.getCommand().getId().equals(wrapped.getId()))
                .findFirst().orElseThrow().getPagesCount());

    }

    private static PreparedCommand request(StreamCommandHistoryPublisher history, long time, int sequence) {
        return request(history, time, sequence, 11, 18);
    }

    private static PreparedCommand request(StreamCommandHistoryPublisher history, long time, int sequence,
            int service, int subtype) {
        var command = new PreparedCommand(CommandId.newBuilder().setGenerationTime(time)
                .setOrigin("satellite-integration").setSequenceNumber(sequence).setCommandName("/SatelliteReportTest/report").build());
        var bytes = new byte[15];
        ByteBuffer.wrap(bytes).putShort(0, (short) 0x1810).putShort(2, (short) (0xc000 | sequence))
                .putShort(4, (short) (bytes.length - 7));
        bytes[6] = 0x20;
        bytes[7] = (byte) service;
        bytes[8] = (byte) subtype;
        command.setBinary(bytes);
        history.addCommand(command);
        return command;
    }

    private static void sent(StreamCommandHistoryPublisher history, PreparedCommand command, long time) {
        history.publishAck(command.getCommandId(), CommandHistoryPublisher.AcknowledgeSent_KEY, time, AckStatus.OK);
    }

    private static Tuple statusPage(long time, int number, int total, int id) {
        var bytes = new byte[29];
        ByteBuffer.wrap(bytes).putShort(0, (short) 0x0810).putShort(2, (short) (0xc000 | id))
                .putShort(4, (short) (bytes.length - 7)).putShort(19, (short) number)
                .putShort(21, (short) total).putInt(23, 1);
        bytes[6] = 0x20;
        bytes[7] = 11;
        bytes[8] = 19;
        bytes[27] = (byte) id;
        bytes[28] = 1;
        return new Tuple(StandardTupleDefinitions.TM,
                new Object[] { time, 10_000 + id, time, 0, bytes, null, null, "live", null });
    }

    private static Tuple storePage(long time, int number, int total, int echo, int id) {
        var bytes = new byte[28];
        ByteBuffer.wrap(bytes).putShort(0, (short) 0x0810).putShort(2, (short) (0xc000 | id))
                .putShort(4, (short) (bytes.length - 7)).putShort(11, (short) echo)
                .putShort(19, (short) number).putShort(21, (short) total);
        bytes[6] = 0x20;
        bytes[7] = 15;
        bytes[8] = 23;
        bytes[23] = 1;
        bytes[24] = (byte) id;
        bytes[25] = 72;
        return new Tuple(StandardTupleDefinitions.TM,
                new Object[] { time, id, time, 0, bytes, null, null, "live", null });
    }

    private static void awaitReports(SatelliteReportService collector, String type, int count) throws Exception {
        long limit = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limit) {
            var found = collector.archive().list(type, null, null, null, null, 10, false);
            if (found.size() == count) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Expected " + count + " reports, service state was " + collector.state());
    }

    private static JsonObject json(HttpClient client, String path) throws Exception {
        var response = get(client, path);
        assertEquals(200, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
