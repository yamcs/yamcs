package org.yamcs.pus.report;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.SUFFIX_STATUS;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.SUFFIX_TIME;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.protobuf.satellite.ReportSnapshot;
import org.yamcs.protobuf.satellite.ScheduledRequestId;
import org.yamcs.pus.report.ReportAssembler.Attempt;
import org.yamcs.pus.report.ReportDecoder.DecodedPage;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/** Optional instance service that archives paginated PUS report responses. */
public class SatelliteReportService extends AbstractYamcsService implements StreamSubscriber {
    public static final String CAPABILITY = "satellite-reports";
    private static final String SENT_STATUS = AcknowledgeSent_KEY + SUFFIX_STATUS;
    private static final String SENT_TIME = AcknowledgeSent_KEY + SUFFIX_TIME;
    private static final int QUEUE_CAPACITY = 8192;
    private static final int MAX_QUEUED_COMMANDS = 4096;

    private final ArrayBlockingQueue<Observation> input = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean lostInput = new AtomicBoolean();
    // These collections are owned by the service worker.
    private final Map<CommandKey, Tuple> prepared = new LinkedHashMap<>();
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final ArrayDeque<Observation> delayedPackets = new ArrayDeque<>();

    private ScheduledThreadPoolExecutor worker;
    private Mdb mdb;
    private Stream tmStream;
    private Stream commandStream;
    private TimeService timeService;
    private ReportDecoder decoder;
    private volatile ReportArchive archive;
    private ReportCommandHistory commandHistory;
    private CommandHistoryRecorder commandRecorder;
    private YarchDatabaseInstance db;
    private long timeout;
    private long reorderMillis;
    private long uncertainUntil;
    private String telemetryLink;
    private String scheduledExecutionAck;
    private int telemetryApid;
    private int commandApid;

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption("telemetryStream", OptionType.STRING).withRequired(true);
        spec.addOption("commandHistoryStream", OptionType.STRING).withRequired(true);
        spec.addOption("responseTimeoutMillis", OptionType.INTEGER).withRequired(true);
        spec.addOption("callbackReorderMillis", OptionType.INTEGER).withDefault(250);
        spec.addOption("telemetryLink", OptionType.STRING);
        spec.addOption("scheduledExecutionAck", OptionType.STRING);
        spec.addOption("telemetryApid", OptionType.INTEGER).withDefault(-1);
        spec.addOption("commandApid", OptionType.INTEGER).withDefault(-1);
        spec.addOption("correlation", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("reportProfiles", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String instance, String serviceName, YConfiguration args) throws InitException {
        super.init(instance, serviceName, args);
        timeout = args.getLong("responseTimeoutMillis");
        reorderMillis = args.getLong("callbackReorderMillis");
        if (timeout <= 0 || reorderMillis < 0) {
            throw new ConfigurationException("responseTimeoutMillis must be positive and callbackReorderMillis nonnegative");
        }
        telemetryLink = args.getString("telemetryLink", null);
        scheduledExecutionAck = args.getString("scheduledExecutionAck", null);
        if (scheduledExecutionAck != null && !scheduledExecutionAck.startsWith("Verifier_")) {
            throw new ConfigurationException("scheduledExecutionAck must name a Verifier_ command-history acknowledgement");
        }
        telemetryApid = configuredApid(args, "telemetryApid");
        commandApid = configuredApid(args, "commandApid");
        mdb = MdbFactory.getInstance(instance);
        timeService = YamcsServer.getTimeService(instance);
        db = YarchDatabase.getInstance(instance);
        var streamConfig = StreamConfig.getInstance(instance);
        tmStream = requireStream(args.getString("telemetryStream"));
        commandStream = requireStream(args.getString("commandHistoryStream"));
        var tmConfig = streamConfig.getTmEntry(tmStream.getName());
        if (tmConfig == null || tmStream == commandStream || streamConfig.getEntries(StandardStreamType.CMD_HIST)
                .stream().noneMatch(entry -> entry.getName().equals(commandStream.getName()))) {
            throw new ConfigurationException("Satellite reports require configured live TM and command-history streams");
        }
        var root = tmConfig.getRootContainer() != null ? tmConfig.getRootContainer() : mdb.getRootSequenceContainer();
        if (root == null) {
            throw new ConfigurationException("No root XTCE container for " + tmStream.getName());
        }
        decoder = new ReportDecoder(instance, mdb, root, args);
    }

    private static int configuredApid(YConfiguration args, String key) {
        int apid = args.getInt(key, -1);
        if (apid < -1 || apid > 0x7ff) {
            throw new ConfigurationException(key + " must be a CCSDS APID or -1");
        }
        return apid;
    }

    private Stream requireStream(String name) {
        var stream = db.getStream(name);
        if (stream == null) {
            throw new ConfigurationException("Satellite reports stream not found: " + name);
        }
        return stream;
    }

    @Override
    protected void doStart() {
        try {
            var instance = YamcsServer.getServer().getInstance(yamcsInstance);
            if (instance.getServices(SatelliteReportService.class).size() > 1) {
                throw new ConfigurationException("Only one SatelliteReportService is allowed per Yamcs instance");
            }
            commandRecorder = instance.getServices(CommandHistoryRecorder.class).stream()
                    .filter(recorder -> recorder.recordsStream(commandStream.getName()))
                    .findFirst().orElseThrow(() -> new ConfigurationException(
                            "Start CommandHistoryRecorder for " + commandStream.getName()
                                    + " before SatelliteReportService"));
            if (db.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
                throw new ConfigurationException("Command history archive is unavailable for satellite reports");
            }
            archive = new ReportArchive(db);
            commandHistory = new ReportCommandHistory(db);
            worker = new ScheduledThreadPoolExecutor(1, task -> {
                var thread = new Thread(task, "satellite-reports-" + yamcsInstance);
                thread.setDaemon(true);
                return thread;
            });
            commandStream.addSubscriber(this);
            tmStream.addSubscriber(this);
            recover();
            notifyStarted();
            worker.scheduleWithFixedDelay(this::process, 0, 25, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            unsubscribe();
            if (worker != null) {
                worker.shutdownNow();
            }
            notifyFailed(e);
        }
    }

    // A callback can arrive one interval late, then spend one interval waiting for its command.
    private long finalizationGrace() {
        return reorderMillis * 2;
    }

    private void recover() {
        long now = timeService.getMissionTime();
        long elapsed = elapsedMillis();
        for (ReportSnapshot snapshot : archive.open()) {
            var attempt = new Attempt(snapshot);
            saveFinal(attempt, now, true);
            protect(attempt, now, elapsed);
        }
        try {
            long cutoff = now - timeout - finalizationGrace();
            for (Tuple tuple : commandHistory.sentSince(cutoff)) {
                restore(createAttempt(tuple, false), now, elapsed);
            }
            if (scheduledExecutionAck != null) {
                for (Tuple tuple : commandHistory.acknowledgedSince(scheduledExecutionAck, cutoff)) {
                    restore(createAttempt(tuple, true), now, elapsed);
                }
            }
        } catch (RuntimeException e) {
            uncertainUntil = elapsed + timeout + reorderMillis;
            log.warn("Could not restore satellite report request windows", e);
        }
    }

    private void restore(Attempt attempt, long now, long elapsed) {
        if (attempt != null && attempt.deadline() + finalizationGrace() > now) {
            if (windows.containsKey(attempt.requestKey())) {
                return;
            }
            if (archive.get(attempt.id()) == null) {
                var previous = archive.findLegacyAttempt(attempt.checkpoint(), now - timeout - finalizationGrace());
                if (previous == null) {
                    saveFinal(attempt, now, true);
                } else {
                    attempt = new Attempt(previous);
                }
            }
            protect(attempt, now, elapsed);
        }
    }

    private void protect(Attempt attempt, long now, long elapsed) {
        if (attempt.deadline() + finalizationGrace() > now) {
            windows.putIfAbsent(attempt.requestKey(),
                    new Window(attempt, elapsed + attempt.deadline() - now + finalizationGrace(), true));
        }
    }

    @Override
    protected void doStop() {
        unsubscribe();
        worker.execute(() -> {
            try {
                for (Window window : windows.values()) {
                    if (!window.exclusion()) {
                        saveFinal(window.attempt(), timeService.getMissionTime(), true);
                    }
                }
                worker.shutdown();
                notifyStopped();
            } catch (RuntimeException e) {
                worker.shutdown();
                notifyFailed(e);
            }
        });
    }

    private void unsubscribe() {
        tmStream.removeSubscriber(this);
        commandStream.removeSubscriber(this);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        if (stream == tmStream) {
            byte[] bytes = tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
            if (bytes == null || bytes.length < 9
                    || !looksLikeReport(bytes) || (telemetryApid >= 0 && telemetryApid != apid(bytes))
                    || (telemetryLink != null && !telemetryLink.equals(
                            tuple.getColumn(StandardTupleDefinitions.TM_LINK_COLUMN)))) {
                return;
            }
        }
        if (!input.offer(new Observation(stream == tmStream, tuple, elapsedMillis()))) {
            lostInput.set(true);
        }
    }

    private boolean looksLikeReport(byte[] bytes) {
        int service = bytes[7] & 0xff;
        return (service == 11 || service == 15 || service == 130)
                && decoder.profile(service + "-" + (bytes[8] & 0xff)) != null;
    }

    private void process() {
        try {
            if (!isRunning()) {
                return;
            }
            long elapsed = elapsedMillis();
            if (!commandRecorder.recordsStream(commandStream.getName())) {
                interruptWindows(elapsed);
                throw new IllegalStateException("Command history recorder stopped for " + commandStream.getName());
            }
            if (lostInput.getAndSet(false)) {
                log.warn("Satellite report input queue overflowed; current attempts will be interrupted");
                input.clear();
                delayedPackets.clear();
                prepared.clear();
                interruptWindows(elapsed);
            }
            var observations = new ArrayList<Observation>();
            input.drainTo(observations);
            for (Observation observation : observations) {
                if (observation.telemetry()) {
                    if (delayedPackets.size() == QUEUE_CAPACITY) {
                        lostInput.set(true);
                        return;
                    }
                    delayedPackets.addLast(observation);
                } else {
                    ingestCommand(observation);
                }
            }
            while (!delayedPackets.isEmpty() && delayedPackets.peekFirst().arrival() + reorderMillis <= elapsed) {
                ingestPacket(delayedPackets.removeFirst().tuple(), elapsed);
            }
            var iterator = windows.values().iterator();
            while (iterator.hasNext()) {
                var window = iterator.next();
                if (window.due() <= elapsed) {
                    if (!window.exclusion()) {
                        saveFinal(window.attempt(), timeService.getMissionTime(), false);
                    }
                    iterator.remove();
                }
            }
        } catch (RuntimeException e) {
            unsubscribe();
            worker.shutdown();
            notifyFailed(e);
        }
    }

    private void interruptWindows(long elapsed) {
        long now = timeService.getMissionTime();
        uncertainUntil = elapsed + timeout + reorderMillis;
        windows.replaceAll((id, window) -> {
            if (!window.exclusion()) {
                saveFinal(window.attempt(), now, true);
            }
            return new Window(window.attempt(), window.due(), true);
        });
    }

    private void ingestCommand(Observation observation) {
        Tuple tuple = observation.tuple();
        var key = new CommandKey(tuple.getColumn(PreparedCommand.CNAME_GENTIME),
                tuple.getColumn(PreparedCommand.CNAME_ORIGIN), tuple.getColumn(PreparedCommand.CNAME_SEQNUM));
        boolean executed = scheduledExecutionAck != null && tuple.hasColumn(scheduledExecutionAck + SUFFIX_STATUS);
        if (!executed && !tuple.hasColumn(SENT_STATUS)) {
            byte[] binary = tuple.getColumn(PreparedCommand.CNAME_BINARY);
            var previous = prepared.get(key);
            if (previous != null || expectedProfile(binary) != null) {
                prepared.put(key, merge(previous, tuple));
                if (prepared.size() > MAX_QUEUED_COMMANDS) {
                    prepared.remove(prepared.keySet().iterator().next());
                }
            }
            return;
        }
        var previous = prepared.remove(key);
        String acknowledgement = executed ? scheduledExecutionAck + SUFFIX_STATUS : SENT_STATUS;
        if (!"OK".equals(tuple.getColumn(acknowledgement))) {
            return;
        }
        if (executed || previous == null || !previous.hasColumn(PreparedCommand.CNAME_ASSIGNMENTS)) {
            previous = merge(commandHistory.find(key.generation(), key.origin(), key.sequence()), previous);
        }
        addAttempt(createAttempt(merge(previous, tuple), executed), observation.arrival());
    }

    private boolean addAttempt(Attempt attempt, long elapsed) {
        if (attempt == null || windows.containsKey(attempt.requestKey()) || archive.get(attempt.id()) != null) {
            return false;
        }
        archive.save(attempt.checkpoint(), attempt.sortTime(), false);
        windows.put(attempt.requestKey(), new Window(attempt, elapsed + timeout + finalizationGrace(), false));
        return true;
    }

    private Attempt createAttempt(Tuple tuple, boolean executed) {
        if (executed != isScheduled(tuple) || !"OK".equals(tuple.getColumn(SENT_STATUS))) {
            return null;
        }
        var profile = profileForCommand(tuple);
        Long effectiveTime = tuple.getColumn(executed ? scheduledExecutionAck + SUFFIX_TIME : SENT_TIME);
        return profile == null || effectiveTime == null || effectiveTime == TimeEncoding.INVALID_INSTANT
                ? null : newAttempt(tuple, profile, effectiveTime);
    }

    private ReportProfile profileForCommand(Tuple tuple) {
        return matchesCommandApid(tuple) ? reportForPacket(tuple.getColumn(PreparedCommand.CNAME_BINARY)) : null;
    }

    private static boolean isScheduled(Tuple tuple) {
        return tuple.getColumn("pus11Binary") != null || tuple.getColumn("pus22Binary") != null;
    }

    private Attempt newAttempt(Tuple tuple, ReportProfile profile, long start) {
        var command = ReportCommandHistory.commandReference(tuple);
        byte[] packet = tuple.getColumn(PreparedCommand.CNAME_BINARY);
        var scope = ReportRequestScope.describe(packet[7] & 0xff, packet[8] & 0xff, command);
        if (scope != null) {
            command = command.toBuilder().setScope(scope).build();
        }
        var echo = commandEcho(tuple, profile);
        if (hasEcho(profile) && echo == null) {
            log.warn("Command {} lacks the configured echo value for {}", command.getId(), profile.type());
        }
        return new Attempt(profile.type(), yamcsInstance, profile.containerName(), command, start, start + timeout, echo);
    }

    private ReportProfile expectedProfile(byte[] bytes) {
        return bytes == null || (commandApid >= 0 && (bytes.length < 2 || apid(bytes) != commandApid))
                ? null : reportForPacket(bytes);
    }

    private ReportProfile reportForPacket(byte[] bytes) {
        if (bytes == null || bytes.length < 9 || (bytes[0] & 0x18) != 0x18) {
            return null;
        }
        return decoder.expectedFor(bytes[7] & 0xff, bytes[8] & 0xff);
    }

    private static int apid(byte[] packet) {
        return (packet[0] & 0x07) << 8 | packet[1] & 0xff;
    }

    private static boolean hasEcho(ReportProfile profile) {
        return profile.commandArgument() != null || profile.commandHistoryAttribute() != null;
    }

    private static String commandEcho(Tuple tuple, ReportProfile profile) {
        Value value = null;
        if (profile.commandArgument() != null) {
            AssignmentInfo assignments = tuple.getColumn(PreparedCommand.CNAME_ASSIGNMENTS);
            if (assignments != null) {
                value = assignments.getAssignmentList().stream()
                        .filter(assignment -> assignment.getName().equals(profile.commandArgument()))
                        .map(assignment -> assignment.getValue()).findFirst().orElse(null);
            }
        } else if (profile.commandHistoryAttribute() != null) {
            var name = profile.commandHistoryAttribute();
            var raw = tuple.getColumn(name);
            if (raw != null) {
                var column = tuple.getDefinition().getColumn(name);
                try {
                    var typed = column.getType().equals(DataType.LONG) ? ValueUtility.getSint64Value((Long) raw)
                            : ValueUtility.getColumnValue(column, raw);
                    value = typed == null ? null : ValueUtility.toGbp(typed);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return value == null ? null : ReportDecoder.canonicalEcho(value);
    }

    private void ingestPacket(Tuple tuple, long elapsed) {
        Integer flags = tuple.getColumn(StandardTupleDefinitions.TM_STATUS_COLUMN);
        if (flags != null && (flags & TmPacket.STATUS_MASK_INVALID) != 0) {
            logIgnored(tuple, "invalid packet status");
            return;
        }
        Long received = tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        if (received == null || received == TimeEncoding.INVALID_INSTANT) {
            logIgnored(tuple, "missing reception time");
            return;
        }
        String rootName = tuple.getColumn(StandardTupleDefinitions.TM_ROOT_CONTAINER_COLUMN);
        var override = rootName == null ? null : mdb.getSequenceContainer(rootName);
        if (rootName != null && override == null) {
            logIgnored(tuple, "unknown root container " + rootName);
            return;
        }
        Long generation = tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        Integer sequence = tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
        byte[] packet = tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        var page = decoder.decode(packet,
                generation == null ? TimeEncoding.INVALID_INSTANT : generation,
                received, sequence == null ? 0 : sequence, override);
        if (page == null) {
            return;
        }
        if (page.issue() != null) {
            logUnassigned(page, page.issue());
        }
        boolean needsEcho = hasEcho(page.profile());
        if (needsEcho && page.echo() == null) {
            logUnassigned(page, "missing or invalid request echo");
            return;
        }
        var candidates = matchingWindows(page);
        if (candidates.isEmpty() && needsEcho && recoverScheduledEcho(page, elapsed)) {
            candidates = matchingWindows(page);
        }
        if (candidates.isEmpty()) {
            logUnassigned(page, needsEcho ? "no eligible command with that request echo"
                    : "no eligible command window");
            return;
        }
        if (candidates.size() == 1 && candidates.get(0).exclusion()) {
            logUnassigned(page, "command window was interrupted");
            return;
        }
        boolean uncertain = !needsEcho && elapsed < uncertainUntil;
        for (Window window : candidates) {
            if (window.exclusion()) {
                continue;
            }
            var attempt = window.attempt();
            if (candidates.size() > 1 || uncertain) {
                attempt.withhold(received);
                if (uncertain) {
                    attempt.issue("A collection gap prevented attribution of received pages.");
                }
            } else {
                attempt.accept(new ReportAssembler.Page(page.pageNumber(), page.totalPages(), received,
                        page.generationTime(), apid(packet), CcsdsPacket.getSequenceCount(packet),
                        page.rows(), page.issue()));
            }
            archive.save(attempt.checkpoint(), attempt.sortTime(), false);
        }
    }

    private List<Window> matchingWindows(DecodedPage page) {
        return windows.values().stream()
                .filter(window -> window.attempt().matches(page.profile().type(), page.receptionTime(),
                        page.echo(), reorderMillis))
                .toList();
    }

    private boolean recoverScheduledEcho(DecodedPage page, long elapsed) {
        var profile = page.profile();
        Tuple tuple = commandHistory.findUniqueSent(command -> isScheduled(command)
                && profile.equals(profileForCommand(command))
                && page.echo().equals(commandEcho(command, profile))
                && page.receptionTime() + reorderMillis >= command.<Long>getColumn(SENT_TIME));
        if (tuple == null) {
            return false;
        }
        return addAttempt(newAttempt(tuple, profile, page.receptionTime()), elapsed);
    }

    private void logIgnored(Tuple tuple, String reason) {
        byte[] packet = tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        log.debug("Ignored satellite report {}-{} for {} at {}, packet {}: {}", packet[7] & 0xff,
                packet[8] & 0xff, yamcsInstance, tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN),
                tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN), reason);
    }

    private void logUnassigned(DecodedPage page, String reason) {
        log.debug("Satellite report {} for {} at {}, packet {}: {}", page.profile().type(), yamcsInstance,
                TimeEncoding.toString(page.receptionTime()), page.sequence(), reason);
    }

    private void saveFinal(Attempt attempt, long now, boolean interrupted) {
        var snapshot = attempt.finish(now, interrupted).toBuilder();
        var rows = new ArrayList<ReportRow.Builder>();
        var requests = new ArrayList<ScheduledRequestId>();
        for (var page : snapshot.getPagesBuilderList()) {
            for (var row : page.getRowsBuilderList()) {
                if (row.hasScheduledRequest() && row.getScheduledRequest().hasReleaseTime()) {
                    rows.add(row);
                    requests.add(row.getScheduledRequest());
                }
            }
        }
        if (!requests.isEmpty()) {
            var commands = commandHistory.findScheduledCommands(requests, this::matchesCommandApid);
            for (int i = 0; i < rows.size(); i++) {
                var command = commands.get(i);
                if (command != null) {
                    rows.get(i).setScheduledCommand(command);
                }
            }
        }
        archive.save(snapshot.build(), attempt.sortTime(), true);
    }

    private boolean matchesCommandApid(Tuple tuple) {
        if (commandApid < 0) {
            return true;
        }
        byte[] packet = tuple.getColumn("pus11Binary");
        if (packet == null) {
            packet = tuple.getColumn("pus22Binary");
        }
        if (packet == null) {
            packet = tuple.getColumn(PreparedCommand.CNAME_BINARY);
        }
        return packet != null && packet.length >= 2 && apid(packet) == commandApid;
    }

    private static Tuple merge(Tuple older, Tuple newer) {
        if (older == null || newer == null) {
            return newer == null ? older : newer;
        }
        var copy = new Tuple(older.getDefinition().copy(), new ArrayList<>(older.getColumns()));
        for (int i = 0; i < newer.size(); i++) {
            var column = newer.getColumnDefinition(i);
            if (copy.hasColumn(column.getName())) {
                copy.setColumn(column.getName(), newer.getColumn(i));
            } else {
                copy.addColumn(column.getName(), column.getType(), newer.getColumn(i));
            }
        }
        return copy;
    }

    public Collection<ReportProfile> profiles() {
        return decoder.profiles();
    }

    public ReportProfile profile(String type) {
        return decoder.profile(type);
    }

    public ReportArchive archive() {
        return archive;
    }

    private static long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private record Observation(boolean telemetry, Tuple tuple, long arrival) {
    }

    private record CommandKey(long generation, String origin, int sequence) {
    }

    private record Window(Attempt attempt, long due, boolean exclusion) {
    }
}
