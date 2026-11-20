package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.StringConverter;

/**
 * ST[11] time-based scheduling
 * <p>
 * The time-based scheduling service type provides the capability to command on-board application processes using
 * requests preloaded on-board the spacecraft and released at their due time.
 * 
 */
public class Pus11Service extends AbstractPusService {
    // Maximum number of sub-schedules that can be contemporaneously managed (ECSS 6.11.4.5c/6.11.5.1a)
    static final int MAX_SUBSCHEDULES = 16;
    // Maximum number of scheduling groups that can be contemporaneously managed (ECSS 6.11.6.1a)
    static final int MAX_GROUPS = 16;

    ScheduledThreadPoolExecutor executor;

    // time-based schedule execution function status; disabled at start (§6.11.4.3.1.b)
    boolean enabled = false;
    PriorityQueue<ScheduledCommand> commands = new PriorityQueue<>();
    private ScheduledFuture<?> scheduledFuture;

    // subschedule id -> subschedule status (true = enabled, false = disabled)
    Map<Long, Boolean> subschStatus = new HashMap<>();

    // scheduling group id -> group status (true = enabled, false = disabled).
    // Groups only exist after an explicit TC[11,22]; there is no auto-creation.
    Map<Long, Boolean> groupStatus = new HashMap<>();

    private final Pus11ScheduleConfig config;

    Pus11Service(PusSimulator pusSimulator) {
        super(pusSimulator, 11);
        config = pusSimulator.scheduleConfig;
    }

    private static long readId(ByteBuffer data, int width) {
        long value = 0;
        for (int i = 0; i < width; i++) {
            value = (value << 8) | Byte.toUnsignedInt(data.get());
        }
        return value;
    }

    private static void writeId(ByteBuffer data, long value, int width) {
        for (int i = width - 1; i >= 0; i--) {
            data.put((byte) (value >>> (i * 8)));
        }
    }

    private static Set<Long> readIds(ByteBuffer data, int width) {
        int count = Byte.toUnsignedInt(data.get());
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(readId(data, width));
        }
        return ids;
    }

    @Override
    public void start() {
        this.executor = pusSimulator.executor;
    }

    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[11,1] enable the time-based schedule execution function
        case 1 -> {
            ack_start(tc);
            log.info("Enabling the time-based schedule execution");
            enabled = true;
            ack_completion(tc);
        }
        // TC[11,2] disable the time-based schedule execution function
        case 2 -> {
            ack_start(tc);
            log.info("Disabling the time-based schedule execution");
            enabled = false;
            ack_completion(tc);
        }
        // TC[11,3] reset the time-based schedule
        case 3 -> {
            ack_start(tc);
            log.info("Reseting the time-based schedule execution");
            enabled = false;
            commands.clear();
            // ECSS 6.11.4.4c.4: enable all groups (they are kept, only their status is reset)
            synchronized (groupStatus) {
                groupStatus.replaceAll((id, status) -> true);
            }
            ack_completion(tc);
        }
        // TC[11,4] insert activities into the time-based schedule
        case 4 -> insertActivities(tc);
        // TC[11,5] delete time-based scheduled activities identified by request identifier
        case 5 -> deleteByRequestId(tc);
        // TC[11,6] delete the time-based scheduled activities identified by a filter
        case 6 -> deleteByFilter(tc);
        // TC[11,7] time-shift scheduled activities identified by request identifier
        case 7 -> timeShiftById(tc);
        // TC[11,8] time-shift the scheduled activities identified by a filter
        case 8 -> timeShiftByFilter(tc);
        // TC[11,9] detail-report time-based scheduled activities identified by request identifier
        case 9 -> detailReportById(tc);
        // TC[11,11] detail-report the time-based scheduled activities identified by a filter
        case 11 -> detailReportByFilter(tc);
        // TC[11,12] summary-report time-based scheduled activities identified by request identifier
        case 12 -> summaryReportById(tc);
        // TC[11,14] summary-report the time-based scheduled activities identified by a filter
        case 14 -> summaryReportByFilter(tc);
        // TC[11,15] time-shift all scheduled activities
        case 15 -> timeShiftAll(tc);
        // TC[11,16] detail-report all time-based scheduled activities
        case 16 -> detailReportAll(tc);
        // TC[11,17] summary-report all time-based scheduled activities
        case 17 -> summaryReportAll(tc);
        // TC[11,18] report the status of each time-based sub-schedule
        case 18 -> scheduleStatusReport(tc);
        // TC[11,20] enable time-based sub-schedules
        case 20 -> enableSubschedule(tc);
        // TC[11,21] disable time-based sub-schedules
        case 21 -> disableSubschedule(tc);
        // TC[11,22] create time-based scheduling groups
        case 22 -> createGroups(tc);
        // TC[11,23] delete time-based scheduling groups
        case 23 -> deleteGroups(tc);
        // TC[11,24] enable time-based scheduling groups
        case 24 -> enableGroups(tc);
        // TC[11,25] disable time-based scheduling groups
        case 25 -> disableGroups(tc);
        // TC[11,26] report the status of each time-based scheduling group
        case 26 -> groupStatusReport(tc);
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    private void insertActivities(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        long subschedule = readId(bb, config.subScheduleBytes());
        int n = bb.get() & 0xFF;

        log.info("Received {} command(s) for subschedule {}", n, subschedule);

        // ECSS 6.11.4.5c/d: reject the whole request if it would create a new sub-schedule
        // beyond the maximum number that can be contemporaneously managed.
        synchronized (subschStatus) {
            if (!subschStatus.containsKey(subschedule) && subschStatus.size() >= MAX_SUBSCHEDULES) {
                log.warn("Rejecting insert request: maximum number of sub-schedules ({}) already reached",
                        MAX_SUBSCHEDULES);
                nack_start(tc, START_ERR_MAX_SUBSCHEDULES_REACHED);
                return;
            }
        }

        var now = pusSimulator.timeEncoding.now();

        // Parse and validate all activities before applying any change, so that a request referencing an
        // unknown group (ECSS 6.11.4.5g.3) or a release time in the past is rejected as a whole and leaves the
        // schedule untouched.
        if (n == 0) {
            nack_start(tc, START_ERR_INVALID_EMBEDDED_TC);
            return;
        }
        List<ScheduledCommand> parsed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long group = readId(bb, config.groupBytes());
            PusTime releaseTime = config.tcTime().read(bb);
            if (bb.remaining() < 7) {
                nack_start(tc, START_ERR_INVALID_EMBEDDED_TC);
                return;
            }
            int length = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;
            if (length > bb.remaining()) {
                nack_start(tc, START_ERR_INVALID_EMBEDDED_TC);
                return;
            }
            byte[] packet = new byte[length];
            bb.get(packet);
            PusTcPacket embedded = pusSimulator.embeddedTc(packet);
            if (!embedded.isValid()) {
                nack_start(tc, START_ERR_INVALID_EMBEDDED_TC);
                return;
            }

            synchronized (groupStatus) {
                if (!groupStatus.containsKey(group)) {
                    log.warn("Rejecting insert request: scheduling group {} does not exist", group);
                    nack_start(tc, START_ERR_UNKNOWN_GROUP);
                    return;
                }
            }
            parsed.add(new ScheduledCommand(releaseTime, subschedule, group, embedded));
        }

        if (bb.hasRemaining()) {
            nack_start(tc, START_ERR_INVALID_EMBEDDED_TC);
            return;
        }
        ack_start(tc);

        for (var sc : parsed) {
            if (sc.releaseTime.isBefore(now)) {
                log.warn("Command schedule time {} is before now {}, rejecting command", sc.releaseTime, now);
                nack_completion(tc, COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST);
                return;
            }
        }

        synchronized (subschStatus) {
            // ECSS 6.11.4.5j.1b: a sub-schedule created as a side effect of an insert starts disabled
            subschStatus.putIfAbsent(subschedule, false);
        }
        for (var sc : parsed) {
            log.info("Scheduling command {} at {} (subschedule {}, group {}, seq count {}, CRC {})",
                    StringConverter.arrayToHexString(sc.tc.getBytes()), sc.releaseTime, sc.subschedule, sc.group,
                    sc.tc.getSequenceCount(), sc.tc.isChecksumValid() ? "valid" : "INVALID");
            commands.add(sc);
        }
        scheduleNext();

        ack_completion(tc);
    }

    // ECSS 6.11.6.2.1 - TC[11,22] create time-based scheduling groups.
    // Note: unlike the standard, which processes the valid instructions and reports the faulty ones,
    // this simulator rejects the whole request on the first faulty instruction, consistently with
    // the rest of this service.
    private void createGroups(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        long[] ids = new long[n];
        boolean[] groupEnabled = new boolean[n];
        Set<Long> seen = new HashSet<>();

        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                ids[i] = readId(bb, config.groupBytes());
                groupEnabled[i] = (bb.get() & 0xFF) != 0;
                if (groupStatus.containsKey(ids[i]) || !seen.add(ids[i])) {
                    log.warn("Rejecting TC[11,22]: scheduling group {} already exists", ids[i]);
                    nack_start(tc, START_ERR_GROUP_EXISTS);
                    return;
                }
                if (groupStatus.size() + i >= MAX_GROUPS) {
                    log.warn("Rejecting TC[11,22]: maximum number of scheduling groups ({}) already reached",
                            MAX_GROUPS);
                    nack_start(tc, START_ERR_MAX_GROUPS_REACHED);
                    return;
                }
            }
            ack_start(tc);
            for (int i = 0; i < n; i++) {
                groupStatus.put(ids[i], groupEnabled[i]);
                log.info("Created scheduling group {} ({})", ids[i], groupEnabled[i] ? "enabled" : "disabled");
            }
        }
        ack_completion(tc);
    }

    // ECSS 6.11.6.2.2 - TC[11,23] delete time-based scheduling groups.
    // N == 0 means "delete all groups that have no associated activity".
    private void deleteGroups(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        synchronized (groupStatus) {
            if (n == 0) {
                ack_start(tc);
                var it = groupStatus.keySet().iterator();
                while (it.hasNext()) {
                    long group = it.next();
                    if (groupHasActivities(group)) {
                        log.warn("Not deleting scheduling group {}: it has associated activities", group);
                    } else {
                        it.remove();
                        log.info("Deleted scheduling group {}", group);
                    }
                }
            } else {
                long[] ids = new long[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = readId(bb, config.groupBytes());
                    if (!groupStatus.containsKey(ids[i])) {
                        log.warn("Rejecting TC[11,23]: scheduling group {} does not exist", ids[i]);
                        nack_start(tc, START_ERR_UNKNOWN_GROUP);
                        return;
                    }
                    if (groupHasActivities(ids[i])) {
                        log.warn("Rejecting TC[11,23]: scheduling group {} has associated activities", ids[i]);
                        nack_start(tc, START_ERR_GROUP_HAS_ACTIVITIES);
                        return;
                    }
                }
                ack_start(tc);
                for (long id : ids) {
                    groupStatus.remove(id);
                    log.info("Deleted scheduling group {}", id);
                }
            }
        }
        ack_completion(tc);
    }

    // ECSS 6.11.6.3.1 - TC[11,24] enable time-based scheduling groups
    private void enableGroups(PusTcPacket tc) {
        setGroupsStatus(tc, true);
    }

    // ECSS 6.11.6.3.2 - TC[11,25] disable time-based scheduling groups
    private void disableGroups(PusTcPacket tc) {
        setGroupsStatus(tc, false);
    }

    // N == 0 means "apply to all groups".
    private void setGroupsStatus(PusTcPacket tc, boolean groupEnabled) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        synchronized (groupStatus) {
            if (n == 0) {
                ack_start(tc);
                groupStatus.replaceAll((id, status) -> groupEnabled);
                log.info("{} all scheduling groups", groupEnabled ? "Enabled" : "Disabled");
            } else {
                long[] ids = new long[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = readId(bb, config.groupBytes());
                    if (!groupStatus.containsKey(ids[i])) {
                        log.warn("Rejecting TC[11,{}]: scheduling group {} does not exist",
                                groupEnabled ? 24 : 25, ids[i]);
                        nack_start(tc, START_ERR_UNKNOWN_GROUP);
                        return;
                    }
                }
                ack_start(tc);
                for (long id : ids) {
                    groupStatus.put(id, groupEnabled);
                    log.info("{} scheduling group {}", groupEnabled ? "Enabled" : "Disabled", id);
                }
            }
        }
        ack_completion(tc);
    }

    // ECSS 6.11.6.3.3 - TC[11,26] report the status of each time-based scheduling group -> TM[11,27]
    private void groupStatusReport(PusTcPacket tc) {
        ack_start(tc);

        synchronized (groupStatus) {
            var pkt = newPacket(27, 4 + groupStatus.size() * (config.groupBytes() + 1));
            var bb = pkt.getUserDataBuffer();

            bb.putInt(groupStatus.size());
            for (var me : groupStatus.entrySet()) {
                writeId(bb, me.getKey(), config.groupBytes());
                bb.put((byte) (me.getValue() ? 1 : 0));
            }
            pusSimulator.transmitRealtimeTM(pkt);
        }

        ack_completion(tc);
    }

    private boolean groupHasActivities(long group) {
        for (var cmd : commands) {
            if (cmd.group == group) {
                return true;
            }
        }
        return false;
    }

    private void deleteByRequestId(PusTcPacket tc) {
        ack_start(tc);

        ByteBuffer bb = tc.getUserDataBuffer();
        filterById(bb, true);

        ack_completion(tc);
    }

    private void deleteByFilter(PusTcPacket tc) {
        ack_start(tc);

        ByteBuffer bb = tc.getUserDataBuffer();
        filterByFilter(bb, true);

        ack_completion(tc);
    }

    private void timeShiftById(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int timeShiftMillis = bb.getInt();

        var toShift = filterById(bb, false);
        if (timeShift(tc, toShift, timeShiftMillis)) {
            ack_completion(tc);
        }
    }

    private void timeShiftByFilter(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int timeShiftMillis = bb.getInt();

        var toShift = filterByFilter(bb, false);
        if (timeShift(tc, toShift, timeShiftMillis)) {
            ack_completion(tc);
        }
    }

    private void timeShiftAll(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int timeShiftMillis = bb.getInt();

        if (timeShift(tc, new ArrayList<>(commands), timeShiftMillis)) {
            ack_completion(tc);
        }
    }

    /**
     * Shifts the release time of the given (queued) commands. If any of them would end up in the past, the TC is
     * NACKed and the schedule is left untouched.
     *
     * @return true if the commands were shifted, false if the TC was rejected
     */
    private boolean timeShift(PusTcPacket tc, Collection<ScheduledCommand> toShift, int timeShiftMillis) {
        // the same activity may be selected more than once (e.g. repeated request ID)
        Set<ScheduledCommand> distinct = new LinkedHashSet<>(toShift);
        var now = pusSimulator.timeEncoding.now();
        for (var cmd : distinct) {
            var shifted = cmd.releaseTime.shiftByMillis(timeShiftMillis);
            if (shifted.isBefore(now)) {
                log.warn("Time-shifting command {} by {} milliseconds would move it to {} before now {}, rejecting",
                        cmd.tc, timeShiftMillis, shifted, now);
                nack_completion(tc, COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST);
                return false;
            }
        }
        if (distinct.isEmpty()) {
            return true;
        }
        // the release time is the queue ordering key, so the commands have to be taken out before changing it
        commands.removeAll(distinct);
        for (var cmd : distinct) {
            cmd.releaseTime = cmd.releaseTime.shiftByMillis(timeShiftMillis);
            log.info("Time-shifted command {} by {} milliseconds", cmd.tc, timeShiftMillis);
        }
        commands.addAll(distinct);
        scheduleNext();
        return true;
    }

    private void scheduleStatusReport(PusTcPacket tc) {
        ack_start(tc);

        synchronized (subschStatus) {
            var pkt = newPacket(19, 4 + subschStatus.size() * (config.subScheduleBytes() + 1));
            var bb = pkt.getUserDataBuffer();

            bb.putInt(subschStatus.size());
            for (var me : subschStatus.entrySet()) {
                writeId(bb, me.getKey(), config.subScheduleBytes());
                bb.put((byte) (me.getValue() ? 1 : 0));
            }
            pusSimulator.transmitRealtimeTM(pkt);
        }

        ack_completion(tc);
    }

    private void detailReportByFilter(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        var cmds = filterByFilter(bb, false);
        sendDetailReport(cmds);
        ack_completion(tc);
    }

    private void summaryReportById(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        var cmds = filterById(bb, false);
        sendSummaryReport(cmds);
        ack_completion(tc);
    }

    private void summaryReportByFilter(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        var cmds = filterByFilter(bb, false);
        sendSummaryReport(cmds);
        ack_completion(tc);
    }

    private void detailReportAll(PusTcPacket tc) {
        ack_start(tc);
        sendDetailReport(commands);
        ack_completion(tc);
    }

    private void summaryReportAll(PusTcPacket tc) {
        ack_start(tc);
        sendSummaryReport(commands);
        ack_completion(tc);
    }

    private void detailReportById(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        var cmds = filterById(bb, false);
        sendDetailReport(cmds);
        ack_completion(tc);
    }

    private void enableSubschedule(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (subschStatus) {
            for (int i = 0; i < n; i++) {
                long subschedule = readId(bb, config.subScheduleBytes());
                subschStatus.put(subschedule, true);
                log.info("Enabled subschedule {}", subschedule);
            }
        }
        ack_completion(tc);
    }

    private void disableSubschedule(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (subschStatus) {
            for (int i = 0; i < n; i++) {
                long subschedule = readId(bb, config.subScheduleBytes());
                subschStatus.put(subschedule, false);
                log.info("Disabled subschedule {}", subschedule);
            }
        }
        ack_completion(tc);
    }

    private static final int MAX_REPORT_BYTES = 1400;

    private void sendSummaryReport(Collection<ScheduledCommand> commands) {
        List<ScheduledCommand> rows = List.copyOf(commands);
        var timeEncoding = pusSimulator.timeEncoding;
        int timeBits = timeEncoding.getEncodedLength() * 8;
        int entryBits = (config.subScheduleBytes() + config.groupBytes()) * 8 + timeBits
                + 16 + PusPackedFields.APID_BITS + PusPackedFields.SEQCOUNT_BITS;
        int rowsPerPage = (MAX_REPORT_BYTES * 8 - 16) / entryBits;
        int pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        ByteBuffer encodedTime = ByteBuffer.allocate(timeEncoding.getEncodedLength());
        for (int page = 0; page < pages; page++) {
            int begin = page * rowsPerPage;
            int count = Math.min(rowsPerPage, rows.size() - begin);
            var packet = newPacket(13, PusPackedFields.bytesForBits(16L + (long) count * entryBits));
            packet.setPage(page + 1, pages);
            BitBuffer bits = PusPackedFields.bitBuffer(packet.getUserDataBuffer());
            bits.putBits(count, 16);
            for (var cmd : rows.subList(begin, begin + count)) {
                bits.putBits(cmd.subschedule, config.subScheduleBytes() * 8);
                bits.putBits(cmd.group, config.groupBytes() * 8);
                encodedTime.clear();
                cmd.releaseTime.encode(encodedTime, timeEncoding);
                PusPackedFields.putBytes(bits, encodedTime.array());
                encodeRequestId(bits, cmd.tc);
            }
            pusSimulator.transmitRealtimeTM(packet);
        }
    }

    private void sendDetailReport(Collection<ScheduledCommand> commands) {
        var timeEncoding = pusSimulator.timeEncoding;
        int rowHeader = config.subScheduleBytes() + config.groupBytes() + timeEncoding.getEncodedLength();
        List<List<ScheduledCommand>> pages = new ArrayList<>();
        List<ScheduledCommand> page = new ArrayList<>();
        pages.add(page);
        int size = 1;
        for (var cmd : commands) {
            int rowSize = rowHeader + cmd.tc.getLength();
            if (!page.isEmpty() && (size + rowSize > MAX_REPORT_BYTES || page.size() == 255)) {
                page = new ArrayList<>();
                pages.add(page);
                size = 1;
            }
            page.add(cmd);
            size += rowSize;
        }
        for (int i = 0; i < pages.size(); i++) {
            var rows = pages.get(i);
            int length = 1 + rows.size() * rowHeader;
            for (var cmd : rows) {
                length += cmd.tc.getLength();
            }
            var packet = newPacket(10, length);
            packet.setPage(i + 1, pages.size());
            ByteBuffer data = packet.getUserDataBuffer();
            data.put((byte) rows.size());
            for (var cmd : rows) {
                writeId(data, cmd.subschedule, config.subScheduleBytes());
                writeId(data, cmd.group, config.groupBytes());
                cmd.releaseTime.encode(data, timeEncoding);
                data.put(cmd.tc.getBytes());
            }
            pusSimulator.transmitRealtimeTM(packet);
        }
    }

    private List<ScheduledCommand> filterById(ByteBuffer bb, boolean remove) {
        List<ScheduledCommand> cmds = new ArrayList<>();
        BitBuffer bits = PusPackedFields.bitBuffer(bb);
        int n = PusPackedFields.readUnsigned(bits, 16);
        log.info("Filtering by {} id filters", n);
        for (int i = 0; i < n; i++) {
            int sourceId = PusPackedFields.readUnsigned(bits, 16);
            int apid = PusPackedFields.readUnsigned(bits, PusPackedFields.APID_BITS);
            int seqCount = PusPackedFields.readUnsigned(bits, PusPackedFields.SEQCOUNT_BITS);
            log.info("Filter by ID source: {}, apid: {}, seqCount: {}", sourceId, apid, seqCount);
            Iterator<ScheduledCommand> it = commands.iterator();

            while (it.hasNext()) {
                var cmd = it.next();
                if (cmd.tc.getSourceId() == sourceId && cmd.tc.getAPID() == apid
                        && cmd.tc.getSequenceCount() == seqCount) {
                    cmds.add(cmd);
                    if (remove) {
                        it.remove();
                    }
                }
            }
        }
        log.info("{} commands matched the filters", cmds.size());
        return cmds;
    }

    private List<ScheduledCommand> filterByFilter(ByteBuffer bb, boolean remove) {

        int type = bb.get() & 0xFF; // Type of time window (enumerated)

        // First time tag (for "from time tag" types)
        PusTime timeTag1 = (type == 1 || type == 2) ? config.tcTime().read(bb) : null;
        // Second time tag (for "to time tag" types)
        PusTime timeTag2 = (type == 1 || type == 3) ? config.tcTime().read(bb) : null;

        Set<Long> subschedules = readIds(bb, config.subScheduleBytes());
        Set<Long> groups = readIds(bb, config.groupBytes());
        if (bb.hasRemaining()) {
            throw new IllegalArgumentException("Unexpected bytes after PUS 11 schedule filter");
        }
        log.info("Filter start_time: {}, end_time: {}, subschedules: {}, groups: {}",
                timeTag1, timeTag2, subschedules, groups);

        // Iterate over scheduled commands and remove matching ones
        Iterator<ScheduledCommand> iterator = commands.iterator();
        List<ScheduledCommand> result = new ArrayList<>();
        while (iterator.hasNext()) {
            ScheduledCommand cmd = iterator.next();
            if ((subschedules.isEmpty() || subschedules.contains(cmd.subschedule))
                    && (groups.isEmpty() || groups.contains(cmd.group))) {
                boolean matches = switch (type) {
                // "select all"
                case 0 -> true;
                // "from time tag to time tag"
                case 1 -> !cmd.releaseTime.isBefore(timeTag1) && !cmd.releaseTime.isAfter(timeTag2);
                // "from time tag"
                case 2 -> !cmd.releaseTime.isBefore(timeTag1);
                // "to time tag"
                case 3 -> !cmd.releaseTime.isAfter(timeTag2);
                default -> {
                    log.warn("Unknown time window type: {}", type);
                    yield false; // Default case for unknown type
                }
                };

                if (matches) {
                    result.add(cmd);
                    if (remove) {
                        log.info("Removing command {} scheduled at {}", cmd.tc, cmd.releaseTime);
                        iterator.remove();
                    }
                }
            }
        }

        log.info("{} commands matched the filter", result.size());
        return result;
    }

    static void encodeRequestId(BitBuffer bits, PusTcPacket tc) {
        bits.putBits(tc.getSourceId(), 16);
        bits.putBits(tc.getAPID(), PusPackedFields.APID_BITS);
        bits.putBits(tc.getSequenceCount(), PusPackedFields.SEQCOUNT_BITS);
    }

    private void runSchedule(PusTime now) {
        while (true) {
            var cmd = commands.peek();
            if (cmd == null) {
                break;
            }
            int c = cmd.releaseTime.compareTo(now);
            if (c < 0) {
                log.warn("Dropping command {} because its release time {} has passed (now: {})", cmd.tc,
                        cmd.releaseTime, now);
                commands.remove();
            } else if (c == 0) {
                // a disabled activity is deleted, not kept, when its release time is reached (§6.11.4.6)
                if (!enabled) {
                    log.warn("Dropping command {} because the time-based schedule execution function is disabled",
                            cmd.tc);
                    commands.remove();
                    continue;
                }
                synchronized (subschStatus) {
                    if (!subschStatus.getOrDefault(cmd.subschedule, false)) {
                        log.warn("Dropping command {} because the subschedule {} is disabled", cmd.tc,
                                cmd.subschedule);
                        commands.remove();
                        continue;
                    }
                }
                synchronized (groupStatus) {
                    if (!groupStatus.getOrDefault(cmd.group, false)) {
                        log.warn("Dropping command {} because the scheduling group {} is disabled or deleted",
                                cmd.tc, cmd.group);
                        commands.remove();
                        continue;
                    }
                }
                log.info("Executing command {}", cmd.tc);
                commands.remove();
                pusSimulator.processTc(cmd.tc);
            } else {
                scheduleNext();
                break;
            }
        }
    }

    private void scheduleNext() {
        var cmd = commands.peek();
        if (cmd == null) {
            return;
        }
        long millis = cmd.releaseTime.deltaMillis(pusSimulator.timeEncoding.now());
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = executor.schedule(() -> runSchedule(cmd.releaseTime), millis, TimeUnit.MILLISECONDS);
    }

    static class ScheduledCommand implements Comparable<ScheduledCommand> {
        PusTime releaseTime;
        final long subschedule;
        final long group;
        final PusTcPacket tc;

        public ScheduledCommand(PusTime releaseTime, long subschedule, long group, PusTcPacket tc) {
            super();
            this.releaseTime = releaseTime;
            this.subschedule = subschedule;
            this.group = group;
            this.tc = tc;
        }

        @Override
        public int compareTo(ScheduledCommand o) {
            return this.releaseTime.compareTo(o.releaseTime);
        }
    }

}
