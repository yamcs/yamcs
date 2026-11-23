package org.yamcs.pus.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.ArgumentLocation;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.satellite.ReportCell;
import org.yamcs.protobuf.satellite.ReportCommand;
import org.yamcs.protobuf.satellite.ScheduledRequestId;
import org.yamcs.pus.PusCommandPostprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.protobuf.ByteString;

/** Reads successful sends and resolves S11 wire request IDs within an instance's command archive. */
public final class ReportCommandHistory {

    private static final String TABLE = CommandHistoryRecorder.TABLE_NAME;
    private static final String SENT_TIME = CommandHistoryPublisher.AcknowledgeSent_KEY
            + CommandHistoryPublisher.SUFFIX_TIME;
    private static final String SENT_STATUS = CommandHistoryPublisher.AcknowledgeSent_KEY
            + CommandHistoryPublisher.SUFFIX_STATUS;
    private static final String EMBEDDED_SEQ = PusCommandPostprocessor.CCSDS_SEQCOUNT_PARA_NAME + ':';
    private static final int TC_HEADER_LENGTH = 11;

    private final YarchDatabaseInstance db;

    public ReportCommandHistory(YarchDatabaseInstance db) {
        this.db = db;
    }

    /** Includes commands issued before the cutoff and held in a ground queue until a recent send. */
    public List<Tuple> sentSince(long cutoff) {
        List<Tuple> sent = new ArrayList<>();
        scanSentThrough(null, tuple -> {
            if (tuple.<Long>getColumn(SENT_TIME) >= cutoff) {
                sent.add(tuple);
            }
            return true;
        });
        return sent;
    }

    List<Tuple> acknowledgedSince(String key, long cutoff) {
        List<Tuple> commands = new ArrayList<>();
        scanSentThrough(null, tuple -> {
            Long time = tuple.getColumn(key + CommandHistoryPublisher.SUFFIX_TIME);
            if (time != null && time >= cutoff
                    && "OK".equals(tuple.getColumn(key + CommandHistoryPublisher.SUFFIX_STATUS))) {
                commands.add(tuple);
            }
            return true;
        });
        return commands;
    }

    Tuple findUniqueSent(Predicate<Tuple> matches) {
        List<Tuple> candidates = new ArrayList<>(2);
        scanSentThrough(null, tuple -> {
            if (matches.test(tuple)) {
                candidates.add(tuple);
            }
            return candidates.size() < 2;
        });
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    Tuple find(long gentime, String origin, int seq) {
        var result = db.executeUnchecked("select * from " + TABLE + " where gentime = ? and origin = ? and seqNum = ?"
                + " limit 1", gentime, origin, seq);
        try {
            return result.hasNext() ? result.next() : null;
        } finally {
            result.close();
        }
    }

    public static ReportCommand commandReference(Tuple tuple) {
        var id = PreparedCommand.getCommandId(tuple);
        var command = ReportCommand.newBuilder()
                .setId(new PreparedCommand(id).getId())
                .setName(id.getCommandName());
        var assignments = tuple.<AssignmentInfo>getColumn(PreparedCommand.CNAME_ASSIGNMENTS);
        if (assignments != null) {
            for (var assignment : assignments.getAssignmentList()) {
                command.addArguments(ReportCell.newBuilder().setName(assignment.getName())
                        .setValue(assignment.getValue()));
            }
        }
        return command.build();
    }

    /** Returns a command reference only when precisely one archived occurrence matches the S11 row. */
    public ReportCommand findScheduledCommand(ScheduledRequestId identity, long releaseTime) {
        return findMatches(List.of(new Lookup(identity, releaseTime)), tuple -> true).getFirst();
    }

    /** Resolves all rows with one command archive scan. Unmatched rows have a null result at their index. */
    public List<ReportCommand> findScheduledCommands(List<ScheduledRequestId> identities) {
        return findScheduledCommands(identities, tuple -> true);
    }

    List<ReportCommand> findScheduledCommands(List<ScheduledRequestId> identities, Predicate<Tuple> commandScope) {
        return findMatches(identities.stream().map(identity -> new Lookup(identity,
                identity.hasReleaseTime() ? TimeEncoding.fromProtobufTimestamp(identity.getReleaseTime())
                        : TimeEncoding.INVALID_INSTANT)).toList(), commandScope);
    }

    private List<ReportCommand> findMatches(List<Lookup> lookups, Predicate<Tuple> commandScope) {
        Map<WireId, List<Lookup>> byWireId = new HashMap<>();
        long latestRelease = TimeEncoding.INVALID_INSTANT;
        for (var lookup : lookups) {
            var id = lookup.identity;
            if (id.hasSourceId() && id.hasApid() && id.hasSequenceCount()
                    && lookup.releaseTime != TimeEncoding.INVALID_INSTANT) {
                var wireId = new WireId(id.getSourceId(), id.getApid(), id.getSequenceCount());
                byWireId.computeIfAbsent(wireId, ignored -> new ArrayList<>()).add(lookup);
                latestRelease = Math.max(latestRelease, lookup.releaseTime);
            }
        }
        if (!byWireId.isEmpty()) {
            scanSentThrough(latestRelease, tuple -> {
                if (commandScope.test(tuple)) {
                    matchCommand(tuple, byWireId);
                }
                return lookups.size() > 1 || !lookups.getFirst().ambiguous;
            });
        }
        return lookups.stream().map(lookup -> lookup.match).toList();
    }

    private static void matchCommand(Tuple tuple, Map<WireId, List<Lookup>> byWireId) {
        var packet = tuple.<byte[]>getColumn(PreparedCommand.CNAME_BINARY);
        if (packet == null || packetLength(packet, 0) != packet.length || tuple.hasColumn("pus22Binary")) {
            return;
        }
        Long scheduledAt = tuple.getColumn(PusCommandPostprocessor.OPTION_SCHEDULE_TIME.getId());
        if (scheduledAt != null && !tuple.hasColumn("pus11Binary")) {
            return;
        }
        long sent = tuple.getColumn(SENT_TIME);
        matchPacket(byWireId, tuple, sent, scheduledAt, packet, 0, packet.length, -1);
        if (packet[7] == 11 && packet[8] == 4 && scheduledAt == null) {
            var embedded = findEmbeddedPackets(tuple, packet);
            for (int i = 0; i < embedded.size(); i++) {
                var occurrence = embedded.get(i);
                matchPacket(byWireId, tuple, sent, null, packet, occurrence.offset(), occurrence.length(), i);
            }
        }
    }

    private static void matchPacket(Map<WireId, List<Lookup>> byWireId, Tuple tuple, long sent, Long scheduledAt,
            byte[] packet, int offset, int length, int embeddedIndex) {
        var wire = new WireId(ByteArrayUtils.decodeUnsignedShort(packet, offset + 9),
                ByteArrayUtils.decodeUnsignedShort(packet, offset) & 0x7ff,
                ByteArrayUtils.decodeUnsignedShort(packet, offset + 2) & 0x3fff);
        var lookups = byWireId.get(wire);
        if (lookups == null) {
            return;
        }
        ByteString exact = null;
        ReportCommand command = null;
        for (var lookup : lookups) {
            if (lookup.ambiguous || sent > lookup.releaseTime || (scheduledAt != null && scheduledAt != lookup.releaseTime)) {
                continue;
            }
            if (lookup.identity.hasPacket()) {
                if (exact == null) {
                    exact = ByteString.copyFrom(packet, offset, length);
                }
                if (!lookup.identity.getPacket().equals(exact)) {
                    continue;
                }
            }
            if (lookup.match != null) {
                lookup.ambiguous = true;
                lookup.match = null;
            } else {
                if (command == null) {
                    command = commandReference(tuple);
                    if (embeddedIndex >= 0) {
                        command = command.toBuilder().setEmbeddedIndex(embeddedIndex).build();
                    }
                }
                lookup.match = command;
            }
        }
    }

    private static List<EmbeddedPacket> findEmbeddedPackets(Tuple tuple, byte[] processed) {
        var assignments = tuple.<AssignmentInfo>getColumn(PreparedCommand.CNAME_ASSIGNMENTS);
        var original = tuple.<byte[]>getColumn(PreparedCommand.CNAME_UNPROCESSED_BINARY);
        if (assignments == null || original == null) {
            return List.of();
        }
        var locations = assignments.getLocationList().stream()
                .filter(location -> tuple.hasColumn(EMBEDDED_SEQ + location.getPath()))
                .sorted(Comparator.comparingInt(ArgumentLocation::getBitPosition)).toList();
        List<EmbeddedPacket> packets = new ArrayList<>();
        int shift = 0;
        int previousEnd = TC_HEADER_LENGTH;
        for (var location : locations) {
            int startBit = location.getBitPosition();
            int endBit = startBit + location.getBitSize();
            if (startBit < previousEnd * 8 || endBit < startBit || endBit % 8 != 0 || endBit / 8 > original.length) {
                return List.of();
            }
            previousEnd = endBit / 8;
            int sequence = tuple.<Number>getColumn(EMBEDDED_SEQ + location.getPath()).intValue();
            int offset = findProcessedOffset(original, processed, (startBit + 7) / 8, previousEnd, shift, sequence);
            if (offset == -1) {
                return List.of();
            }
            int length = packetLength(processed, offset + shift);
            packets.add(new EmbeddedPacket(offset + shift, length));
            shift += length - (previousEnd - offset);
        }
        return packets;
    }

    // The archived location includes the leading size tag, when present. The postprocessor changes the CCSDS
    // sequence count and length and may append a two-byte checksum to each embedded packet.
    private static int findProcessedOffset(byte[] original, byte[] processed, int start, int end, int shift,
            int sequence) {
        int match = -1;
        for (int offset = start; offset <= end - TC_HEADER_LENGTH; offset++) {
            int mapped = offset + shift;
            int length = packetLength(processed, mapped);
            int originalLength = end - offset;
            if ((length == originalLength || length == originalLength + 2)
                    && (ByteArrayUtils.decodeUnsignedShort(processed, mapped + 2) & 0x3fff) == sequence
                    && Arrays.equals(original, offset, offset + 2, processed, mapped, mapped + 2)
                    && Arrays.equals(original, offset + 6, end, processed, mapped + 6, mapped + originalLength)) {
                if (match != -1) {
                    return -1;
                }
                match = offset;
            }
        }
        return match;
    }

    private static int packetLength(byte[] packet, int offset) {
        if (offset > packet.length - TC_HEADER_LENGTH || (packet[offset] & 0xf8) != 0x18
                || (packet[offset + 6] & 0xf0) != 0x20) {
            return -1;
        }
        int length = ByteArrayUtils.decodeUnsignedShort(packet, offset + 4) + 7;
        return length >= TC_HEADER_LENGTH && length <= packet.length - offset ? length : -1;
    }

    private void scanSentThrough(Long latest, Predicate<Tuple> consume) {
        TableDefinition table = db.getTable(TABLE);
        if (table == null) {
            throw new YarchException("Command history archive is unavailable");
        }
        if (table.getColumnDefinition(SENT_STATUS) == null || table.getColumnDefinition(SENT_TIME) == null) {
            return;
        }
        try (var context = new ExecutionContext(db)) {
            var walker = db.getStorageEngine(table).newTableWalker(context, table, false, false);
            if (latest != null) {
                var range = new DbRange();
                range.rangeEnd = table.getColumnDefinition(PreparedCommand.CNAME_GENTIME).getSerializer()
                        .toByteArray(latest);
                walker.setPrimaryIndexRange(range);
            }
            try {
                walker.walk((key, value) -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new YarchException("Interrupted while reading command history");
                    }
                    var tuple = table.deserialize(key, value);
                    var sent = tuple.<Long>getColumn(SENT_TIME);
                    if (sent != null && (latest == null || sent <= latest)
                            && AckStatus.OK.name().equals(tuple.getColumn(SENT_STATUS)) && !consume.test(tuple)) {
                        return TableVisitor.ACTION_STOP;
                    }
                    return TableVisitor.ACTION_CONTINUE;
                });
            } finally {
                walker.close();
            }
        } catch (StreamSqlException e) {
            throw new YarchException(e);
        }
    }

    private record WireId(int source, int apid, int sequenceCount) {
    }

    private record EmbeddedPacket(int offset, int length) {
    }

    private static final class Lookup {
        final ScheduledRequestId identity;
        final long releaseTime;
        ReportCommand match;
        boolean ambiguous;

        Lookup(ScheduledRequestId identity, long releaseTime) {
            this.identity = identity;
            this.releaseTime = releaseTime;
        }
    }
}
