package org.yamcs.pus.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.ArgumentLocation;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.Assignment;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.satellite.ScheduledRequestId;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

import com.google.protobuf.ByteString;

class ReportCommandHistoryTest extends YarchTestCase {

    private static final long TIME = 1_800_000_000_000L;
    private static final String TABLE = CommandHistoryRecorder.TABLE_NAME;
    private static final String SENT = "Acknowledge_Sent";

    @BeforeAll
    static void configureTimeEncoding() {
        TimeEncoding.setUp();
    }

    @BeforeEach
    void createCommandArchive() throws Exception {
        execute("create table " + TABLE + " (" + StandardTupleDefinitions.TC.getStringDefinition1()
                + ", primary key(gentime, origin, seqNum))");
    }

    @Test
    void recoversSuccessfulSendTimesIndependentlyOfIssueTimes() throws Exception {
        var history = new ReportCommandHistory(ydb);
        assertEquals(List.of(), history.sentSince(TIME));
        addCommand(TIME - 100_000, TIME, "OK", 1, packet(1, 4, 8, 17, 1, 0));
        addCommand(TIME - 15, TIME - 1, "OK", 2, packet(1, 4, 8, 17, 1, 0));
        addCommand(TIME + 2, TIME + 2, "NOK", 3, packet(1, 4, 8, 17, 1, 0));
        assertEquals(List.of(1), history.sentSince(TIME).stream()
                .map(PreparedCommand::getCommandId).map(id -> id.getSequenceNumber()).toList());
    }

    @Test
    void requiresUniqueWireIdentityAndUsesPacketBytesAndReleaseTime() throws Exception {
        var history = new ReportCommandHistory(ydb);
        var original = packet(701, 123, 9, 17, 1, 6);
        var different = packet(701, 123, 9, 17, 1, 8);
        addCommand(TIME - 500, TIME - 400, "OK", 1, original);
        addCommand(TIME - 300, TIME - 300, "OK", 2, different);
        addCommand(TIME - 200, TIME - 200, "OK", 3, packet(701, 123, 10, 17, 1, 6));
        addCommand(TIME - 10, TIME + 1, "OK", 4, original);
        var identity = ScheduledRequestId.newBuilder().setApid(701).setSequenceCount(123).setSourceId(9);

        assertNull(history.findScheduledCommand(identity.build(), TIME));
        var match = history.findScheduledCommand(identity.setPacket(ByteString.copyFrom(original)).build(), TIME);
        assertEquals("1799999999500-origin-1", match.getId());
        assertEquals("/test/activity", match.getName());
        assertFalse(match.hasEmbeddedIndex());
        assertNull(history.findScheduledCommand(identity.build(), TIME - 401));
    }

    @Test
    void excludesArchivedCommandsOutsideTheConfiguredApid() throws Exception {
        var history = new ReportCommandHistory(ydb);
        addCommand(TIME - 500, TIME - 400, "OK", 1, packet(701, 123, 9, 17, 1, 6));
        var identity = ScheduledRequestId.newBuilder().setApid(701).setSequenceCount(123).setSourceId(9)
                .setReleaseTime(TimeEncoding.toProtobufTimestamp(TIME)).build();

        assertNull(history.findScheduledCommands(List.of(identity), tuple -> false).getFirst());
        assertEquals("1799999999500-origin-1",
                history.findScheduledCommands(List.of(identity), tuple -> true).getFirst().getId());
    }

    @Test
    void resolvesExplicitWrapperToItsEmbeddedOccurrence() throws Exception {
        byte[] first = packet(701, 0, 9, 17, 1, 1);
        byte[] second = packet(701, 0, 9, 17, 1, 4);
        byte[] header = packet(701, 1, 3, 11, 4, 0, 0, 0);
        int firstOffset = header.length;
        int secondOffset = firstOffset + first.length + 1;
        byte[] unprocessed = Arrays.copyOf(header, secondOffset + second.length);
        System.arraycopy(first, 0, unprocessed, firstOffset, first.length);
        unprocessed[secondOffset - 1] = (byte) second.length;
        System.arraycopy(second, 0, unprocessed, secondOffset, second.length);

        // Adding both CRCs shifts the second packet and changes its leading size tag.
        byte[] firstOnWire = Arrays.copyOf(first, first.length + 2);
        var ccsds = CcsdsPacket.wrap(firstOnWire);
        ccsds.setCcsdsPacketLength((short) (firstOnWire.length - 7));
        ccsds.setSequenceCount((short) 0x3fff);
        byte[] secondOnWire = Arrays.copyOf(second, second.length + 2);
        CcsdsPacket.wrap(secondOnWire).setCcsdsPacketLength((short) (secondOnWire.length - 7));
        byte[] parent = Arrays.copyOf(unprocessed, unprocessed.length + 4);
        CcsdsPacket.wrap(parent).setCcsdsPacketLength((short) (parent.length - 7));
        System.arraycopy(firstOnWire, 0, parent, firstOffset, firstOnWire.length);
        parent[secondOffset + 1] = (byte) secondOnWire.length;
        System.arraycopy(secondOnWire, 0, parent, secondOffset + 2, secondOnWire.length);

        addCommand(TIME - 100, TIME - 80, "OK", 1, parent);
        var assignments = AssignmentInfo.newBuilder()
                .addAssignment(Assignment.newBuilder().setName("activities").setValue(Value.newBuilder()
                        .setType(Value.Type.UINT32).setUint32Value(2)))
                .addLocation(location("activities[0].tc", firstOffset, first.length))
                .addLocation(location("activities[1].tc", secondOffset - 1, second.length + 1))
                .build();
        var attributes = commandTuple(TIME - 100, 1);
        attributes.addColumn("unprocessedBinary", DataType.BINARY, unprocessed);
        attributes.addColumn("assignments", DataType.protobuf(AssignmentInfo.class.getName()), assignments);
        attributes.addColumn("ccsds-seqcount:activities[0].tc", DataType.INT, 0x3fff);
        attributes.addColumn("ccsds-seqcount:activities[1].tc", DataType.INT, 0);
        write(attributes);

        var identity = ScheduledRequestId.newBuilder().setApid(701).setSequenceCount(0).setSourceId(9)
                .setPacket(ByteString.copyFrom(secondOnWire)).build();
        var match = new ReportCommandHistory(ydb).findScheduledCommand(identity, TIME);
        assertEquals("1799999999900-origin-1", match.getId());
        assertEquals(1, match.getEmbeddedIndex());
        assertEquals("activities", match.getArguments(0).getName());
        assertEquals(2, match.getArguments(0).getValue().getUint32Value());
    }

    @Test
    void resolvesBatchedRowsInTheirOriginalOrderWithTheScheduledEpoch() throws Exception {
        addCommand(TIME - 100, TIME - 70, "OK", 2, packet(35, 90, 7, 17, 1, 9));
        var attributes = commandTuple(TIME - 100, 2);
        attributes.addColumn("pus11Binary", DataType.BINARY, packet(35, 91, 7, 11, 4, 0));
        attributes.addColumn("pus11ScheduleAt", DataType.TIMESTAMP, TIME);
        write(attributes);
        var identity = ScheduledRequestId.newBuilder().setApid(35).setSourceId(7).setSequenceCount(90)
                .setReleaseTime(TimeEncoding.toProtobufTimestamp(TIME));
        var matches = new ReportCommandHistory(ydb).findScheduledCommands(List.of(
                identity.clone().setReleaseTime(TimeEncoding.toProtobufTimestamp(TIME + 100)).build(),
                identity.build(),
                identity.clone().setSourceId(3).build()));
        assertNull(matches.get(0));
        assertEquals("1799999999900-origin-2", matches.get(1).getId());
        assertNull(matches.get(2));
    }

    private static ArgumentLocation location(String path, int offset, int length) {
        return ArgumentLocation.newBuilder().setPath(path).setBitPosition(8 * offset).setBitSize(8 * length).build();
    }

    private static Tuple commandTuple(long issued, int seqNum) {
        return new Tuple(StandardTupleDefinitions.TC.copy(), new Object[] { issued, "origin", seqNum, "/test/activity" });
    }

    private void write(Tuple tuple) {
        var table = ydb.getTable(TABLE);
        var writer = ydb.getStorageEngine(table).newTableWriter(ydb, table, InsertMode.UPSERT_APPEND);
        try {
            writer.onTuple(null, tuple);
        } finally {
            writer.close();
        }
    }

    private void addCommand(long issued, long sent, String status, int seqNum, byte[] packet) throws Exception {
        execute("insert into " + TABLE + " (gentime, origin, seqNum, cmdName, binary, " + SENT + "_Time, " + SENT
                + "_Status) values (?, ?, ?, ?, ?, ?, ?)", issued, "origin", seqNum, "/test/activity", packet, sent,
                status);
    }

    private static byte[] packet(int apid, int count, int source, int service, int subtype, int... payload) {
        byte[] packet = new byte[11 + payload.length];
        CcsdsPacket.wrap(packet).setHeader(apid, 1, 1, 3, count);
        packet[6] = 0x20;
        packet[7] = (byte) service;
        packet[8] = (byte) subtype;
        packet[9] = (byte) (source >> 8);
        packet[10] = (byte) source;
        for (int i = 0; i < payload.length; i++) {
            packet[11 + i] = (byte) payload[i];
        }
        return packet;
    }
}
