package org.yamcs.pus.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.satellite.ReportCell;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.SequenceContainer;

class ReportDecoderTest {
    private static final long GENERATION = 1000L;
    private static final long RECEPTION = 2000L;
    private static final int SEQUENCE = 7;
    private static final int DATA_OFFSET = 23;
    private static Mdb mdb;
    private static SequenceContainer root;
    private static ReportDecoder decoder;

    @BeforeAll
    static void loadMissionTelemetry() throws Exception {
        YConfiguration.setupTest(null);
        TimeEncoding.setUp();
        mdb = loadFixture();
        root = mdb.getSequenceContainer("/SatelliteReportTest/ccsds_space_packet");
        decoder = new ReportDecoder("test", mdb, root, YConfiguration.emptyConfig());
    }

    @Test
    void resolvesDefaultBindingsFromTheMdbRoot() {
        var decoderWithDefaultRoot = new ReportDecoder("test", mdb, null, YConfiguration.emptyConfig());
        assertEquals(root.getSubsystemName() + "/time_based_schedule_detail_report",
                decoderWithDefaultRoot.profile("11-10").containerName());
    }

    @Test
    void mapsEveryWireRequestAndExtractsRenderableRows() {
        assertEquals(17, decoder.profiles().size());
        assertNull(decoder.expectedFor(11, 4));
        assertRow(11, 10, new int[] { 9, 11, 16 }, "tc_packet", 1, 8, 2, 8, 3, 8, 0, 48,
                0x1810, 16, 0xc123, 16, 4, 16, 0x20, 8, 15, 8, 22, 8, 0x1234, 16);
        assertRow(11, 13, new int[] { 12, 14, 17 }, "request_id",
                1, 16, 2, 8, 3, 8, 0, 48, 0x1234, 16, 16, 11, 0x123, 14);
        assertRow(11, 19, new int[] { 18 }, "subschedule_id", 1, 32, 3, 8, 1, 8);
        assertRow(11, 27, new int[] { 26 }, "group_id", 1, 32, 4, 8, 1, 8);
        assertRow(15, 6, new int[] { 5 }, "apid_reports",
                4, 8, 1, 8, 16, 11, 1, 8, 3, 8, 1, 8, 25, 8);
        assertRow(15, 13, new int[] { 12 }, "oldest_stored_packet_time",
                1, 8, 4, 8, 0, 48, 0, 48, 0, 48, 75, 8, 15, 8);
        assertRow(15, 19, new int[] { 18 }, "time_range_retrieval_status", 1, 8, 4, 8, 1, 8, 1, 8, 0, 8);
        assertRow(15, 23, new int[] { 22 }, "packet_store_size", 1, 8, 4, 8, 217, 8, 0, 8, 9, 8);
        assertRow(15, 36, new int[] { 35 }, "apid_reports", 4, 8, 1, 8, 16, 11, 1, 8, 25, 8, 7, 8);
        assertRow(15, 38, new int[] { 37 }, "apid_reports", 4, 8, 1, 8, 16, 11, 1, 8, 25, 8, 7, 8);
        assertRow(15, 40, new int[] { 39 }, "apid_reports", 4, 8, 1, 8, 16, 11, 1, 8, 8, 8);
        assertRow(130, 4, new int[] { 3 }, "remaining_time", 0, 8, 0, 48, 0, 8, 60, 16);
        assertRow(130, 8, new int[] { 7 }, "type_reports", 1, 8, 0, 8, 1, 8, 0, 8, 16, 11, 25, 8);
        assertRow(130, 14, new int[] { 13 }, "type_reports",
                1, 8, 22, 8, 1, 1, 60, 16, 1, 8, 0, 8, 16, 11, 25, 8);
        assertRow(130, 16, new int[] { 15 }, "remaining_time", 1, 8, 22, 8, 1, 1, 1, 1, 15, 16);
        assertRow(130, 18, new int[] { 17 }, "source_mask", 1, 8, 0, 8, 16, 11, 25, 8, 0x5, 8, 1, 1, 1, 1, 0, 1);
        assertRow(130, 22, new int[] { 21 }, "slot_reports",
                0, 8, 0, 48, 1, 1, 0, 8, 219, 8, 45, 16, 3, 16, 7, 16, 1, 8, 0, 8, 251, 8, 30, 16, 1, 1);
    }

    @Test
    void preservesStoreCapacityInReceivedBytes() {
        var row = decode(packet(15, 23, 1, 1, 1, 8, 4, 8, 217, 8, 0, 8, 9, 8)).rows().getFirst();
        var size = cell(row, "packet_store_size");
        assertEquals(217, size.getValue().getUint32Value());
        assertEquals("bytes", size.getUnit());
        assertEquals(9, cell(row, "virtual_channel").getValue().getUint32Value());
    }

    @Test
    void retainsFixedPassFieldsWithApplicabilityAndUnsignedEightBitIds() {
        var inactive = decode(packet(130, 22, 1, 1, 0, 8, 0, 48, 0, 1, 0, 8, 219, 8, 45, 16,
                3, 16, 7, 16, 0, 8)).rows().getFirst();
        assertFalse(cell(inactive, "current_slot_applicable").getValue().getBooleanValue());
        assertEquals(219, cell(inactive, "current_slot_id").getValue().getUint32Value());
        assertEquals(45, cell(inactive, "allotted_time").getValue().getUint32Value());
        assertEquals(0, cell(inactive, "slot_reports").getValue().getArrayValueCount());
        var activeWithoutSlots = decode(packet(130, 22, 1, 1, 0, 8, 0, 48, 1, 1, 0, 8, 219, 8, 45, 16,
                3, 16, 7, 16, 0, 8)).rows().getFirst();
        assertFalse(cell(activeWithoutSlots, "current_slot_applicable").getValue().getBooleanValue());

        var active = decode(packet(130, 22, 1, 1, 0, 8, 0, 48, 1, 1, 0, 8, 219, 8, 45, 16,
                3, 16, 7, 16, 1, 8, 0, 8, 251, 8, 30, 16, 1, 1)).rows().getFirst();
        assertTrue(cell(active, "current_slot_applicable").getValue().getBooleanValue());
        assertEquals(219, cell(active, "current_slot_id").getValue().getUint32Value());
        var slots = cell(active, "slot_reports").getValue().getArrayValueList();
        assertEquals(1, slots.size());
        assertEquals(251, aggregateMember(slots.getFirst(), "slot_id").getUint32Value());
        assertEquals(30, aggregateMember(slots.getFirst(), "allotted_time").getUint32Value());
    }

    @Test
    void retainsParentsWithZeroChildrenAndKeepsSuccessiveStoreSegments() {
        var row = decode(packet(15, 6, 1, 1, 4, 8, 2, 8,
                16, 11, 0, 8, 16, 11, 2, 8, 3, 8, 0, 8, 3, 8, 1, 8, 25, 8)).rows().getFirst();
        assertEquals(4, cell(row, "packet_store_id").getValue().getUint32Value());
        var apids = cell(row, "apid_reports").getValue().getArrayValueList();
        assertEquals(2, apids.size());
        assertEquals(0, aggregateMember(apids.getFirst(), "services").getArrayValueCount());
        var services = aggregateMember(apids.getLast(), "services").getArrayValueList();
        assertEquals(2, services.size());
        assertEquals(0, aggregateMember(services.getFirst(), "subservices").getArrayValueCount());
        assertEquals(25, aggregateMember(services.getLast(), "subservices").getArrayValue(0).getUint32Value());

        var rows = decode(packet(15, 23, 1, 1, 2, 8, 4, 8, 217, 8, 0, 8, 9, 8,
                4, 8, 201, 8, 0, 8, 12, 8)).rows();
        assertEquals(List.of(217, 201), rows.stream()
                .map(r -> cell(r, "packet_store_size").getValue().getUint32Value()).toList());
    }

    @Test
    void extractsSameScheduledRequestIdentityFromDetailAndSummary() {
        var detail = decode(packet(11, 10, 1, 1, 1, 8, 2, 8, 3, 8, 0, 48,
                0x1810, 16, 0xc123, 16, 4, 16, 0x20, 8, 15, 8, 22, 8, 0x1234, 16)).rows().getFirst();
        var summary = decode(packet(11, 13, 1, 1, 1, 16, 2, 8, 3, 8, 0, 48,
                0x1234, 16, 16, 11, 0x123, 14)).rows().getFirst();
        var request = detail.getScheduledRequest();
        assertEquals(0x1234, request.getSourceId());
        assertEquals(16, request.getApid());
        assertEquals(0x123, request.getSequenceCount());
        assertTrue(request.hasReleaseTime());
        assertArrayEquals(new byte[] { 0x18, 0x10, (byte) 0xc1, 0x23, 0, 4, 0x20, 15, 22, 0x12, 0x34 },
                request.getPacket().toByteArray());
        assertEquals(request.toBuilder().clearPacket().build(), summary.getScheduledRequest());
    }

    @Test
    void distinguishesEmptyValidPagesFromMissingPayloadAndInvalidPagination() {
        var empty = decode(packet(11, 27, 1, 1, 0, 32));
        assertEquals(1, empty.pageNumber());
        assertEquals(1, empty.totalPages());
        assertEquals(RECEPTION, empty.receptionTime());
        assertEquals(GENERATION, empty.generationTime());
        assertEquals(SEQUENCE, empty.sequence());
        assertTrue(empty.rows().isEmpty());

        var malformed = decoder.decode(packet(11, 27, 1, 2, 2, 32, 7, 8, 1, 8),
                GENERATION, RECEPTION, SEQUENCE, root);
        assertNotNull(malformed.issue());
        assertTrue(malformed.rows().isEmpty());

        var badNumber = decoder.decode(packet(11, 27, 2, 1, 0, 32),
                GENERATION, RECEPTION, SEQUENCE, root);
        assertNotNull(badNumber.issue());
        assertEquals(2, badNumber.pageNumber());
        assertEquals(1, badNumber.totalPages());

        assertNull(decoder.decode(packet(5, 1, 1, 1), GENERATION, RECEPTION, SEQUENCE, root));

        // The report container for this wire type only matches the flight-software producer.
        var wrongProducer = packet(11, 27, 1, 1, 0, 32);
        ByteArrayUtils.encodeUnsignedShort(0x0811, wrongProducer, 0);
        assertNull(decoder.decode(wrongProducer, GENERATION, RECEPTION, SEQUENCE, root));
    }

    @Test
    void validatesEchoMembershipAndComparesTypedValues() {
        var args = YConfiguration.wrap(Map.of("correlation", Map.of("11-27",
                Map.of("telemetryParameter", "/SatelliteReportTest/pus_destination_id", "commandArgument", "request_id"))));
        var echoDecoder = new ReportDecoder("test", mdb, root, args);
        byte[] packet = packet(11, 27, 1, 1, 0, 32);
        ByteArrayUtils.encodeUnsignedShort(32769, packet, 11);
        var page = echoDecoder.decode(packet, GENERATION, RECEPTION, SEQUENCE, root);
        assertEquals("request_id", page.profile().commandArgument());
        assertEquals(ReportDecoder.canonicalEcho(Value.newBuilder().setType(Value.Type.SINT64)
                .setSint64Value(32769).build()), page.echo());
        assertNotNull(page.echo());
        assertFalse(page.echo().equals(ReportDecoder.canonicalEcho(
                Value.newBuilder().setType(Value.Type.STRING).setStringValue("32769").build())));

        var unrelated = YConfiguration.wrap(Map.of("correlation", Map.of("11-27",
                Map.of("telemetryParameter", "/SatelliteReportTest/subschedule_count", "commandArgument", "request_id"))));
        assertThrows(ConfigurationException.class, () -> new ReportDecoder("test", mdb, root, unrelated));

        var incomplete = YConfiguration.wrap(Map.of("correlation", Map.of("11-27", Map.of())));
        assertThrows(ConfigurationException.class, () -> new ReportDecoder("test", mdb, root, incomplete));
    }


    @Test
    void validatesCriticalPayloadFieldTypesAtStartup() throws Exception {
        var incompatibleMdb = loadFixture();
        var payload = incompatibleMdb.getParameter("/SatelliteReportTest/pass_state_report");
        var aggregateType = (AggregateParameterType) payload.getParameterType();
        aggregateType.getMember("dump_active_flag").setDataType(aggregateType.getMember("n_count").getType());
        var configuredRoot = incompatibleMdb.getSequenceContainer("/SatelliteReportTest/ccsds_space_packet");
        var exception = assertThrows(ConfigurationException.class,
                () -> new ReportDecoder("test", incompatibleMdb, configuredRoot, YConfiguration.emptyConfig()));
        assertTrue(exception.getMessage().contains("/SatelliteReportTest/pass_state_report.dump_active_flag"));
    }

    private static Mdb loadFixture() throws Exception {
        var fixture = Path.of(ReportDecoderTest.class.getResource("/xtce/satellite-report-test.xml").toURI());
        return MdbFactory.createInstance(
                List.of(YConfiguration.wrap(Map.of("type", "xtce", "spec", fixture.toString()))), false, false);
    }

    private static void assertRow(int service, int subtype, int[] requestSubtypes, String cellName, int... contents) {
        var page = decode(packet(service, subtype, 2, 3, contents));
        assertEquals(2, page.pageNumber());
        assertEquals(3, page.totalPages());
        assertEquals(service + "-" + subtype, page.profile().type());
        for (int request : requestSubtypes) {
            assertEquals(page.profile(), decoder.expectedFor(service, request));
        }
        assertEquals(1, page.rows().size(), page.profile().type());
        assertTrue(cell(page.rows().getFirst(), cellName).hasValue());
    }

    private static ReportDecoder.DecodedPage decode(byte[] packet) {
        var page = decoder.decode(packet, GENERATION, RECEPTION, SEQUENCE, root);
        assertNotNull(page);
        assertNull(page.issue(), () -> page.profile().type() + " " + page.issue());
        return page;
    }

    private static ReportCell cell(ReportRow row, String name) {
        return row.getCellsList().stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    private static Value aggregateMember(Value value, String name) {
        var aggregate = value.getAggregateValue();
        return aggregate.getValue(aggregate.getNameList().indexOf(name));
    }

    // CCSDS primary header followed by the mission's PUS TM header and report payload.
    private static byte[] packet(int service, int subtype, int pageNumber, int totalPages, int... wireFieldWidths) {
        byte[] bytes = new byte[1024];
        ByteArrayUtils.encodeUnsignedShort(0x0810, bytes, 0);
        ByteArrayUtils.encodeUnsignedShort(0xc007, bytes, 2);
        bytes[6] = 0x20;
        bytes[7] = (byte) service;
        bytes[8] = (byte) subtype;
        ByteArrayUtils.encodeUnsignedShort(pageNumber, bytes, 19);
        ByteArrayUtils.encodeUnsignedShort(totalPages, bytes, 21);
        var payload = new BitBuffer(bytes);
        payload.setPosition(DATA_OFFSET * 8);
        for (int i = 0; i < wireFieldWidths.length; i += 2) {
            payload.putBits(wireFieldWidths[i], wireFieldWidths[i + 1]);
        }
        int packetLength = (payload.getPosition() + 7) / 8;
        ByteArrayUtils.encodeUnsignedShort(packetLength - 7, bytes, 4);
        return Arrays.copyOf(bytes, packetLength);
    }
}
