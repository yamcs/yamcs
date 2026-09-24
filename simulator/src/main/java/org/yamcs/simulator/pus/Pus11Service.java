package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.utils.StringConverter;

/**
 * ST[11] time-based scheduling
 * <p>
 * The time-based scheduling service type provides the capability to command on-board application processes using
 * requests preloaded on-board the spacecraft and released at their due time.
 * 
 */
public class Pus11Service extends AbstractPusService {
    ScheduledThreadPoolExecutor executor;

    // time-based schedule execution function status; disabled at start (§6.11.4.3.1.b)
    boolean enabled = false;
    PriorityQueue<ScheduledCommand> commands = new PriorityQueue<>();
    private ScheduledFuture<?> scheduledFuture;

    // subschedule id -> subschedule status (true = enabled, false = disabled)
    Map<Integer, Boolean> subschStatus = new HashMap<>();
    // group id -> group status (true = enabled, false = disabled)
    Map<Integer, Boolean> groupStatus = new HashMap<>();

    Pus11Service(PusSimulator pusSimulator) {
        super(pusSimulator, 11);
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
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int subschedule = bb.get() & 0xFF;
        int n = bb.get() & 0xFF;

        log.info("Received {} command(s) for subschedule {}", n, subschedule);

        var now = pusSimulator.timeEncoding.now();

        // parse and validate all activities before inserting any, so a rejected TC leaves the schedule untouched
        List<ScheduledCommand> toInsert = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // group id: always present in TC[11,4] as written by this ground segment; groups are bookkeeping-only here
            int group = bb.get() & 0xFF;
            PusTime releaseTime = pusSimulator.timeEncoding.read(bb);
            if (releaseTime.isBefore(now)) {
                log.warn("Command schedule time {} is before now {}, rejecting command", releaseTime, now);
                nack_completion(tc, COMPL_ERR_SCHEDULE_TIME_IN_THE_PAST);
                return;
            }

            int length = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;
            byte[] packet = new byte[length];
            bb.get(packet);
            log.info("Scheduling command {} at {} (group {})", StringConverter.arrayToHexString(packet), releaseTime, group);

            toInsert.add(new ScheduledCommand(releaseTime, subschedule, new PusTcPacket(packet)));
        }
        synchronized (subschStatus) {
            subschStatus.putIfAbsent(subschedule, true);
        }
        commands.addAll(toInsert);
        scheduleNext();

        ack_completion(tc);
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
            var pkt = newPacket(19, 4 + subschStatus.size() * 2);
            var bb = pkt.getUserDataBuffer();

            bb.putInt(subschStatus.size());
            for (var me : subschStatus.entrySet()) {
                bb.put(me.getKey().byteValue());
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
                int subschedule = bb.get() & 0xFF;
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
                int subschedule = bb.get() & 0xFF;
                subschStatus.put(subschedule, false);
                log.info("Disabled subschedule {}", subschedule);
            }
        }
        ack_completion(tc);
    }

    private void createGroups(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                int groupId = bb.get() & 0xFF;
                boolean enabled = (bb.get() & 0xFF) == 1;
                groupStatus.put(groupId, enabled);
                log.info("Created group {} status={}", groupId, enabled);
            }
        }
        ack_completion(tc);
    }

    private void deleteGroups(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                int groupId = bb.get() & 0xFF;
                groupStatus.remove(groupId);
                log.info("Deleted group {}", groupId);
            }
        }
        ack_completion(tc);
    }

    private void enableGroups(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                int groupId = bb.get() & 0xFF;
                groupStatus.put(groupId, true);
                log.info("Enabled group {}", groupId);
            }
        }
        ack_completion(tc);
    }

    private void disableGroups(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                int groupId = bb.get() & 0xFF;
                groupStatus.put(groupId, false);
                log.info("Disabled group {}", groupId);
            }
        }
        ack_completion(tc);
    }

    private void groupStatusReport(PusTcPacket tc) {
        ack_start(tc);
        synchronized (groupStatus) {
            var pkt = newPacket(27, 4 + groupStatus.size() * 2);
            var bb = pkt.getUserDataBuffer();
            bb.putInt(groupStatus.size());
            for (var me : groupStatus.entrySet()) {
                bb.put(me.getKey().byteValue());
                bb.put((byte) (me.getValue() ? 1 : 0));
            }
            pusSimulator.transmitRealtimeTM(pkt);
        }
        ack_completion(tc);
    }

    private void sendSummaryReport(Collection<ScheduledCommand> cmds) {
        var pkt = newPacket(13, 4 + cmds.size() * (7 + pusSimulator.timeEncoding.getEncodedLength()));
        var bb = pkt.getUserDataBuffer();
        bb.putShort((short) cmds.size());
        for (var cmd : cmds) {
            bb.put((byte) cmd.subschedule);
            cmd.releaseTime.encode(bb, pusSimulator.timeEncoding);
            encodeRequestId(bb, cmd.tc);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private static final int MAX_DETAIL_REPORT_SIZE = 1400;

    private void sendDetailReport(Collection<ScheduledCommand> cmds) {
        Iterator<ScheduledCommand> iterator = cmds.iterator();
        // command taken from the iterator that did not fit in the previous batch
        ScheduledCommand carry = null;

        while (carry != null || iterator.hasNext()) {
            int totalSize = 4;
            List<ScheduledCommand> batch = new ArrayList<>();

            while (carry != null || iterator.hasNext()) {
                ScheduledCommand cmd = carry != null ? carry : iterator.next();
                carry = null;
                int cmdSize = 1 + pusSimulator.timeEncoding.getEncodedLength() + cmd.tc.getLength();

                // an oversized command still goes out, alone in its own report
                if (!batch.isEmpty() && totalSize + cmdSize > MAX_DETAIL_REPORT_SIZE) {
                    carry = cmd;
                    break;
                }

                batch.add(cmd);
                totalSize += cmdSize;
            }

            var pkt = newPacket(10, totalSize);
            var bb = pkt.getUserDataBuffer();

            bb.putShort((short) batch.size());
            for (var cmd : batch) {
                bb.put((byte) cmd.subschedule);
                cmd.releaseTime.encode(bb, pusSimulator.timeEncoding);
                bb.put(cmd.tc.getBytes());
            }
            pusSimulator.transmitRealtimeTM(pkt);
        }
    }


    private List<ScheduledCommand> filterById(ByteBuffer bb, boolean remove) {
        List<ScheduledCommand> cmds = new ArrayList<>();
        int n = bb.getShort() & 0xFFFF;
        log.info("Filtering by {} id filters", n);
        for (int i = 0; i < n; i++) {
            int sourceId = bb.getShort() & 0xFFFF;
            int apid = bb.getShort() & 0x07FF;
            int seqCount = bb.getShort() & 0xFFFF;
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
        PusTime timeTag1 = (type == 1 || type == 2) ? pusSimulator.timeEncoding.read(bb) : null;
        // Second time tag (for "to time tag" types)
        PusTime timeTag2 = (type == 1 || type == 3) ? pusSimulator.timeEncoding.read(bb) : null;

        BitSet subschedules = null;
        // Read the number of sub-schedules
        int n = bb.get() & 0xFF;
        if (n > 0) {
            subschedules = new BitSet();
            for (int i = 0; i < n; i++) {
                int subschedule = bb.get() & 0xFF;
                subschedules.set(subschedule);
            }
        }
        log.info("Filter start_time: {}, end_time: {}, subschedules: {}", timeTag1, timeTag2, subschedules);

        // Iterate over scheduled commands and remove matching ones
        Iterator<ScheduledCommand> iterator = commands.iterator();
        List<ScheduledCommand> result = new ArrayList<>();
        while (iterator.hasNext()) {
            ScheduledCommand cmd = iterator.next();
            if (subschedules == null || subschedules.get(cmd.subschedule)) {
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

    static void encodeRequestId(ByteBuffer bb, PusTcPacket tc) {
        bb.putShort((short) tc.getSourceId());
        bb.putShort((short) tc.getAPID());
        bb.putShort((short) tc.getSequenceCount());
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
        final int subschedule;
        final PusTcPacket tc;

        public ScheduledCommand(PusTime releaseTime, int subschedule, PusTcPacket tc) {
            super();
            this.releaseTime = releaseTime;
            this.subschedule = subschedule;
            this.tc = tc;
        }

        @Override
        public int compareTo(ScheduledCommand o) {
            return this.releaseTime.compareTo(o.releaseTime);
        }
    }

}
