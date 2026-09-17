package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.utils.StringConverter;

/**
 * ST[22] position-based scheduling
 * <p>
 * The position-based scheduling service type provides the capability to command on-board application processes using
 * requests pre­loaded on-board the spacecraft and released when it reaches a given orbit position.
 * <p>
 * This service is the mirror of {@link Pus11Service} (time-based scheduling): the same sub-schedule / group semantics,
 * the same insert / delete / shift / report subtypes, just keyed on a {@link PusPosition} (orbit number + orbit angle)
 * instead of a {@link PusTime}. The orbit itself is a simple synthetic clock: the angle sweeps 0..360 degrees uniformly
 * over a fixed orbital period, and the orbit number increments on each wrap.
 */
public class Pus22Service extends AbstractPusService {
    // Maximum number of sub-schedules that can be contemporaneously managed (ECSS 6.22.6.6c/6.22.7.1a)
    static final int MAX_SUBSCHEDULES = 16;
    // Maximum number of scheduling groups that can be contemporaneously managed (ECSS 6.22.8.1a)
    static final int MAX_GROUPS = 16;

    static final long DEFAULT_ORBIT_PERIOD_NANOS = 90L * 60 * 1_000_000_000L; // 90 minutes, a realistic LEO period

    ScheduledThreadPoolExecutor executor;

    boolean enabled = true;
    PriorityQueue<ScheduledCommand> commands = new PriorityQueue<>();
    private ScheduledFuture<?> scheduledFuture;

    // sub-schedule id -> sub-schedule status (true = enabled, false = disabled)
    Map<Integer, Boolean> subschStatus = new HashMap<>();

    // scheduling group id -> group status (true = enabled, false = disabled).
    // Groups only exist after an explicit TC[22,22]; there is no auto-creation.
    Map<Integer, Boolean> groupStatus = new HashMap<>();

    // Synthetic orbit clock: at wall-clock t0Nanos, the position was (orbitNumberAtT0, angle 0).
    private final long orbitPeriodNanos;
    private long t0Nanos;
    private long orbitNumberAtT0;
    // set by TC[22,28]; applied (i.e. the clock is rebased) at the next orbit wrap - ECSS 6.22.6.4d.1
    private volatile Long pendingOrbitNumber;
    private ScheduledFuture<?> wrapFuture;

    Pus22Service(PusSimulator pusSimulator) {
        this(pusSimulator, DEFAULT_ORBIT_PERIOD_NANOS);
    }

    Pus22Service(PusSimulator pusSimulator, long orbitPeriodNanos) {
        super(pusSimulator, 22);
        this.orbitPeriodNanos = orbitPeriodNanos;
        this.t0Nanos = System.nanoTime();
        this.orbitNumberAtT0 = 1;
    }

    @Override
    public void start() {
        this.executor = pusSimulator.executor;
    }

    /** Current simulated position, derived from the wall clock. */
    public PusPosition currentPosition() {
        long elapsed = System.nanoTime() - t0Nanos;
        long orbitsElapsed = Math.floorDiv(elapsed, orbitPeriodNanos);
        long angleNanos = Math.floorMod(elapsed, orbitPeriodNanos);
        int angleTicks = (int) (angleNanos * PusPosition.TICKS_PER_ORBIT / orbitPeriodNanos);
        return new PusPosition(orbitNumberAtT0 + orbitsElapsed, angleTicks);
    }

    public synchronized void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[22,1] enable the position-based schedule execution function
        case 1 -> {
            ack_start(tc);
            log.info("Enabling the position-based schedule execution");
            enabled = true;
            ack_completion(tc);
        }
        // TC[22,2] disable the position-based schedule execution function
        case 2 -> {
            ack_start(tc);
            log.info("Disabling the position-based schedule execution");
            enabled = false;
            ack_completion(tc);
        }
        // TC[22,3] reset the position-based schedule
        case 3 -> {
            ack_start(tc);
            log.info("Reseting the position-based schedule execution");
            enabled = false;
            commands.clear();
            // ECSS 6.22.6.5c.3-equivalent: enable all groups (kept, only their status is reset)
            synchronized (groupStatus) {
                groupStatus.replaceAll((id, status) -> true);
            }
            ack_completion(tc);
        }
        // TC[22,4] insert activities into the position-based schedule
        case 4 -> insertActivities(tc);
        // TC[22,5] delete position-based scheduled activities identified by request identifier
        case 5 -> deleteByRequestId(tc);
        // TC[22,6] delete the position-based scheduled activities identified by a filter
        case 6 -> deleteByFilter(tc);
        // TC[22,7] position-shift scheduled activities identified by request identifier
        case 7 -> positionShiftById(tc);
        // TC[22,8] position-shift the scheduled activities identified by a filter
        case 8 -> positionShiftByFilter(tc);
        // TC[22,9] detail-report position-based scheduled activities identified by request identifier
        case 9 -> detailReportById(tc);
        // TC[22,11] detail-report the position-based scheduled activities identified by a filter
        case 11 -> detailReportByFilter(tc);
        // TC[22,12] summary-report position-based scheduled activities identified by request identifier
        case 12 -> summaryReportById(tc);
        // TC[22,14] summary-report the position-based scheduled activities identified by a filter
        case 14 -> summaryReportByFilter(tc);
        // TC[22,15] position-shift all scheduled activities
        case 15 -> positionShiftAll(tc);
        // TC[22,16] detail-report all position-based scheduled activities
        case 16 -> detailReportAll(tc);
        // TC[22,17] summary-report all position-based scheduled activities
        case 17 -> summaryReportAll(tc);
        // TC[22,18] report the status of each position-based sub-schedule
        case 18 -> scheduleStatusReport(tc);
        // TC[22,20] enable position-based sub-schedules
        case 20 -> enableSubschedule(tc);
        // TC[22,21] disable position-based sub-schedules
        case 21 -> disableSubschedule(tc);
        // TC[22,22] create position-based scheduling groups
        case 22 -> createGroups(tc);
        // TC[22,23] delete position-based scheduling groups
        case 23 -> deleteGroups(tc);
        // TC[22,24] enable position-based scheduling groups
        case 24 -> enableGroups(tc);
        // TC[22,25] disable position-based scheduling groups
        case 25 -> disableGroups(tc);
        // TC[22,26] report the status of each position-based scheduling group
        case 26 -> groupStatusReport(tc);
        // TC[22,28] set the orbit number
        case 28 -> setOrbitNumber(tc);
        default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    // ECSS 6.22.6.4 - TC[22,28] set the orbit number. Takes effect at the end of the current orbit (6.22.6.4d.1),
    // not immediately.
    private void setOrbitNumber(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        long newOrbitNumber = bb.getInt() & 0xFFFFFFFFL;

        synchronized (this) {
            pendingOrbitNumber = newOrbitNumber;
            scheduleNextWrap();
        }
        log.info("Orbit number {} will apply at the next orbit wrap", newOrbitNumber);
        ack_completion(tc);
    }

    private void scheduleNextWrap() {
        long elapsed = System.nanoTime() - t0Nanos;
        long orbitsElapsed = Math.floorDiv(elapsed, orbitPeriodNanos);
        long nextWrapElapsed = (orbitsElapsed + 1) * orbitPeriodNanos;
        long delayMillis = Math.max(0, (nextWrapElapsed - elapsed) / 1_000_000L);

        if (wrapFuture != null) {
            wrapFuture.cancel(false);
        }
        wrapFuture = executor.schedule(this::applyPendingOrbitNumber, delayMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void applyPendingOrbitNumber() {
        if (pendingOrbitNumber != null) {
            t0Nanos = System.nanoTime();
            orbitNumberAtT0 = pendingOrbitNumber;
            pendingOrbitNumber = null;
            log.info("Orbit wrap: orbit number is now {}", orbitNumberAtT0);
        }
    }

    private void insertActivities(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int subschedule = bb.get() & 0xFF;
        int n = bb.get() & 0xFF;

        log.info("Received {} command(s) for subschedule {}", n, subschedule);

        // ECSS 6.22.6.6c/d: reject the whole request if it would create a new sub-schedule beyond
        // the maximum number that can be contemporaneously managed.
        synchronized (subschStatus) {
            if (!subschStatus.containsKey(subschedule) && subschStatus.size() >= MAX_SUBSCHEDULES) {
                log.warn("Rejecting insert request: maximum number of sub-schedules ({}) already reached",
                        MAX_SUBSCHEDULES);
                nack_start(tc, START_ERR_MAX_SUBSCHEDULES_REACHED);
                return;
            }
        }

        var now = currentPosition();

        // Parse all activities before applying any change so that a request referencing an
        // unknown group is rejected as a whole (ECSS 6.22.6.6g.2).
        List<ScheduledCommand> parsed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int group = bb.get() & 0xFF;
            PusPosition positionTag = PusPosition.read(bb);
            int length = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7;
            byte[] packet = new byte[length];
            bb.get(packet);

            synchronized (groupStatus) {
                if (!groupStatus.containsKey(group)) {
                    log.warn("Rejecting insert request: scheduling group {} does not exist", group);
                    nack_start(tc, START_ERR_UNKNOWN_GROUP);
                    return;
                }
            }
            parsed.add(new ScheduledCommand(positionTag, subschedule, group, new PusTcPacket(packet)));
        }

        ack_start(tc);

        synchronized (subschStatus) {
            if (!subschStatus.containsKey(subschedule)) {
                // ECSS 6.22.6.6j.1b: a sub-schedule created as a side effect of an insert starts disabled
                subschStatus.put(subschedule, false);
            }
        }

        for (var sc : parsed) {
            if (sc.positionTag.isBefore(now)) {
                log.warn("Command position {} is before current position {}, rejecting command", sc.positionTag,
                        now);
                nack_completion(tc, COMPL_ERR_SCHEDULE_POSITION_IN_THE_PAST);
                return;
            }
            log.info("Scheduling command {} at {} (subschedule {}, group {})",
                    StringConverter.arrayToHexString(sc.tc.getBytes()), sc.positionTag, sc.subschedule, sc.group);
            commands.add(sc);
        }
        scheduleNext();

        ack_completion(tc);
    }

    // ECSS 6.22.8.2.1 - TC[22,22] create position-based scheduling groups.
    // Note: unlike the standard, which processes the valid instructions and reports the faulty ones,
    // this simulator rejects the whole request on the first faulty instruction, consistently with
    // the rest of this service (and with Pus11Service).
    private void createGroups(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;
        int[] ids = new int[n];
        boolean[] groupEnabled = new boolean[n];

        synchronized (groupStatus) {
            for (int i = 0; i < n; i++) {
                ids[i] = bb.get() & 0xFF;
                groupEnabled[i] = (bb.get() & 0xFF) != 0;
                if (groupStatus.containsKey(ids[i])) {
                    log.warn("Rejecting TC[22,22]: scheduling group {} already exists", ids[i]);
                    nack_start(tc, START_ERR_GROUP_EXISTS);
                    return;
                }
                if (groupStatus.size() + i >= MAX_GROUPS) {
                    log.warn("Rejecting TC[22,22]: maximum number of scheduling groups ({}) already reached",
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

    // ECSS 6.22.8.2.2 - TC[22,23] delete position-based scheduling groups.
    // N == 0 means "delete all groups that have no associated activity".
    private void deleteGroups(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int n = bb.get() & 0xFF;

        synchronized (groupStatus) {
            if (n == 0) {
                ack_start(tc);
                var it = groupStatus.keySet().iterator();
                while (it.hasNext()) {
                    int group = it.next();
                    if (groupHasActivities(group)) {
                        log.warn("Not deleting scheduling group {}: it has associated activities", group);
                    } else {
                        it.remove();
                        log.info("Deleted scheduling group {}", group);
                    }
                }
            } else {
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = bb.get() & 0xFF;
                    if (!groupStatus.containsKey(ids[i])) {
                        log.warn("Rejecting TC[22,23]: scheduling group {} does not exist", ids[i]);
                        nack_start(tc, START_ERR_UNKNOWN_GROUP);
                        return;
                    }
                    if (groupHasActivities(ids[i])) {
                        log.warn("Rejecting TC[22,23]: scheduling group {} has associated activities", ids[i]);
                        nack_start(tc, START_ERR_GROUP_HAS_ACTIVITIES);
                        return;
                    }
                }
                ack_start(tc);
                for (int id : ids) {
                    groupStatus.remove(id);
                    log.info("Deleted scheduling group {}", id);
                }
            }
        }
        ack_completion(tc);
    }

    // ECSS 6.22.8.3.1 - TC[22,24] enable position-based scheduling groups
    private void enableGroups(PusTcPacket tc) {
        setGroupsStatus(tc, true);
    }

    // ECSS 6.22.8.3.2 - TC[22,25] disable position-based scheduling groups
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
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = bb.get() & 0xFF;
                    if (!groupStatus.containsKey(ids[i])) {
                        log.warn("Rejecting TC[22,{}]: scheduling group {} does not exist",
                                groupEnabled ? 24 : 25, ids[i]);
                        nack_start(tc, START_ERR_UNKNOWN_GROUP);
                        return;
                    }
                }
                ack_start(tc);
                for (int id : ids) {
                    groupStatus.put(id, groupEnabled);
                    log.info("{} scheduling group {}", groupEnabled ? "Enabled" : "Disabled", id);
                }
            }
        }
        ack_completion(tc);
    }

    // ECSS 6.22.8.3.3 - TC[22,26] report the status of each position-based scheduling group -> TM[22,27]
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

    private boolean groupHasActivities(int group) {
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

    private void positionShiftById(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int deltaTicks = bb.getInt();

        var toShift = filterById(bb, true);

        if (!toShift.isEmpty()) {
            for (var cmd : toShift) {
                cmd.positionTag = cmd.positionTag.shiftByTicks(deltaTicks);
                commands.add(cmd);
                log.info("Position-shifted command {} by {} ticks", cmd.tc, deltaTicks);
            }
            scheduleNext();
        }

        ack_completion(tc);
    }

    private void positionShiftByFilter(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int deltaTicks = bb.getInt();

        var toShift = filterByFilter(bb, true);

        if (!toShift.isEmpty()) {
            for (var cmd : toShift) {
                cmd.positionTag = cmd.positionTag.shiftByTicks(deltaTicks);
                commands.add(cmd);
                log.info("Position-shifted command {} by {} ticks", cmd.tc, deltaTicks);
            }
            scheduleNext();
        }

        ack_completion(tc);
    }

    private void positionShiftAll(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();

        int deltaTicks = bb.getInt();

        List<ScheduledCommand> updatedCommands = new ArrayList<>();

        while (!commands.isEmpty()) {
            ScheduledCommand cmd = commands.poll();
            cmd.positionTag = cmd.positionTag.shiftByTicks(deltaTicks);
            updatedCommands.add(cmd);
        }

        commands.addAll(updatedCommands);
        scheduleNext();

        ack_completion(tc);
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
        int subschedule = bb.get() & 0xFF;
        synchronized (subschStatus) {
            subschStatus.put(subschedule, true);
        }
        log.info("Enabled subschedule {}", subschedule);
        ack_completion(tc);
    }

    private void disableSubschedule(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        int subschedule = bb.get() & 0xFF;
        synchronized (subschStatus) {
            subschStatus.put(subschedule, false);
        }
        log.info("Disabled subschedule {}", subschedule);
        ack_completion(tc);
    }

    private void sendSummaryReport(Collection<ScheduledCommand> cmds) {
        var pkt = newPacket(13, 4 + cmds.size() * 14);
        var bb = pkt.getUserDataBuffer();
        bb.putShort((short) cmds.size());
        for (var cmd : cmds) {
            bb.put((byte) cmd.subschedule);
            bb.put((byte) cmd.group);
            cmd.positionTag.encode(bb);
            encodeRequestId(bb, cmd.tc);
        }
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private static final int MAX_DETAIL_REPORT_SIZE = 1400;

    private void sendDetailReport(Collection<ScheduledCommand> cmds) {
        Iterator<ScheduledCommand> iterator = cmds.iterator();

        while (iterator.hasNext()) {
            int totalSize = 4;
            List<ScheduledCommand> batch = new ArrayList<>();

            while (iterator.hasNext()) {
                ScheduledCommand cmd = iterator.next();
                int cmdSize = 8 + cmd.tc.getLength();

                if (totalSize + cmdSize > MAX_DETAIL_REPORT_SIZE) {
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
                bb.put((byte) cmd.group);
                cmd.positionTag.encode(bb);
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

        int type = bb.get() & 0xFF; // Type of position window (enumerated)

        // First position tag (for "from position tag" types)
        PusPosition tag1 = (type == 1 || type == 2) ? PusPosition.read(bb) : null;
        // Second position tag (for "to position tag" types)
        PusPosition tag2 = (type == 1 || type == 3) ? PusPosition.read(bb) : null;

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
        log.info("Filter start_position: {}, end_position: {}, subschedules: {}", tag1, tag2, subschedules);

        // Iterate over scheduled commands and remove matching ones
        Iterator<ScheduledCommand> iterator = commands.iterator();
        List<ScheduledCommand> result = new ArrayList<>();
        while (iterator.hasNext()) {
            ScheduledCommand cmd = iterator.next();
            if (subschedules == null || subschedules.get(cmd.subschedule)) {
                boolean matches = switch (type) {
                // "select all"
                case 0 -> true;
                // "from position tag to position tag"
                case 1 -> !cmd.positionTag.isBefore(tag1) && !cmd.positionTag.isAfter(tag2);
                // "from position tag"
                case 2 -> !cmd.positionTag.isBefore(tag1);
                // "to position tag"
                case 3 -> !cmd.positionTag.isAfter(tag2);
                default -> {
                    log.warn("Unknown position window type: {}", type);
                    yield false; // Default case for unknown type
                }
                };

                if (matches) {
                    result.add(cmd);
                    if (remove) {
                        log.info("Removing command {} scheduled at {}", cmd.tc, cmd.positionTag);
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

    private void runSchedule(PusPosition now) {
        while (true) {
            var cmd = commands.peek();
            if (cmd == null) {
                break;
            }
            int c = cmd.positionTag.compareTo(now);
            if (c < 0) {
                log.warn("Dropping command {} because its position {} has passed (now: {})", cmd.tc,
                        cmd.positionTag, now);
                commands.remove();
            } else if (c == 0) {
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
        long deltaTicks = cmd.positionTag.deltaTicks(currentPosition());
        long delayMillis = Math.max(0, deltaTicks * orbitPeriodNanos / PusPosition.TICKS_PER_ORBIT / 1_000_000L);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = executor.schedule(() -> runSchedule(cmd.positionTag), delayMillis, TimeUnit.MILLISECONDS);
    }

    static class ScheduledCommand implements Comparable<ScheduledCommand> {
        PusPosition positionTag;
        final int subschedule;
        final int group;
        final PusTcPacket tc;

        public ScheduledCommand(PusPosition positionTag, int subschedule, int group, PusTcPacket tc) {
            super();
            this.positionTag = positionTag;
            this.subschedule = subschedule;
            this.group = group;
            this.tc = tc;
        }

        @Override
        public int compareTo(ScheduledCommand o) {
            return this.positionTag.compareTo(o.positionTag);
        }
    }

}
