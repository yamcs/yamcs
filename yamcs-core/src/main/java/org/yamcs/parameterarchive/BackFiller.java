package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.logging.Log;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LongArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Back-fills the parameter archive by triggering replays: - either regularly scheduled replays - or monitor data
 * streams (tm, param) and keep track of which segments have to be rebuild
 * 
 */
public class BackFiller implements StreamSubscriber {
    List<Schedule> schedules;
    long t0;
    int runCount;

    final ParameterArchive parchive;

    long warmupTime;
    final TimeService timeService;
    static AtomicInteger count = new AtomicInteger();
    private final Log log;
    final ScheduledThreadPoolExecutor executor;

    // set of segments that have to be rebuilt following monitoring of streams
    private Map<Long, StreamUpdate> streamUpdates;
    // streams which are monitored
    private List<Stream> subscribedStreams;

    // after how many backfilling tasks to trigger a parchive.compact()
    int compactFrequency = -1;

    int compactCount = 0;
    long quietPeriodThreshold;

    private List<BackFillerListener> listeners = new CopyOnWriteArrayList<>();
    private List<StreamUpdatePolicyEnter> streamUpdatePolicy = new ArrayList<>();

    private boolean automaticBackfillingEnabled = true;
    private List<Future<?>> scheduledFutures = new ArrayList<>();

    /**
     * 
     * Constructs a new BackFiller
     * <p>
     * The backfiller is used for manual requests and also by automatic backfilling.
     * <p>
     * defaultAutomaticBackfilling is used as default for whether to schedule or not backfillings. If the realtime
     * filler is enabled, the automatic backfilling is disabled by default.
     */
    BackFiller(ParameterArchive parchive, YConfiguration config, boolean defaultAutomaticBackfilling) {
        this.parchive = parchive;
        this.log = new Log(BackFiller.class, parchive.getYamcsInstance());
        parseConfig(config, defaultAutomaticBackfilling);
        timeService = YamcsServer.getTimeService(parchive.getYamcsInstance());
        executor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("ParameterArchive-BackFiller-" + parchive.getYamcsInstance())
                        .build());

    }

    public static Spec getSpec() {
        Spec spec = new Spec();

        spec.addOption("warmupTime", OptionType.INTEGER).withDefault(60);
        spec.addOption("automaticBackfilling", OptionType.BOOLEAN).withAliases("enabled").withRequired(false);
        spec.addOption("monitorStreams", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("streamUpdateFillFrequency", OptionType.INTEGER)
                .withDeprecationMessage("Please use the streamUpdateFillPolicy").withDefault(3600);

        Spec policyEntry = new Spec();
        policyEntry.addOption("dataAge", OptionType.FLOAT).withRequired(true);
        policyEntry.addOption("fillFrequency", OptionType.INTEGER).withDefault(3600);
        policyEntry.addOption("quietThreshold", OptionType.INTEGER).withDefault(60);
        spec.addOption("streamUpdateFillPolicy", OptionType.LIST).withElementType(OptionType.MAP).withSpec(policyEntry);

        Spec schedSpec = new Spec();
        schedSpec.addOption("startInterval", OptionType.INTEGER);
        schedSpec.addOption("numIntervals", OptionType.INTEGER);

        spec.addOption("schedule", OptionType.MAP).withSpec(schedSpec);
        spec.addOption("compactFrequency", OptionType.INTEGER).withDefault(-1);

        return spec;
    }

    synchronized void scheduleAutoFillers() {
        if (!this.automaticBackfillingEnabled) {
            return;
        }

        if (schedules != null && !schedules.isEmpty()) {
            int c = 0;
            for (Schedule s : schedules) {
                if (s.frequency == -1) {
                    c++;
                    continue;
                }
                var f = executor.scheduleAtFixedRate(() -> {
                    runSchedule(s);
                }, 0, s.frequency, TimeUnit.SECONDS);
                scheduledFutures.add(f);
            }
            if (c > 0) {
                long now = timeService.getMissionTime();
                t0 = ParameterArchive.getIntervalStart(now);

                var f = executor.schedule(() -> {
                    runSegmentSchedules();
                }, t0 - now, TimeUnit.MILLISECONDS);

                scheduledFutures.add(f);
            }
        }

        if (subscribedStreams != null && !subscribedStreams.isEmpty()) {
            var f = executor.scheduleAtFixedRate(() -> {
                checkStreamUpdates();
            }, 5, 5, TimeUnit.SECONDS);
            scheduledFutures.add(f);
        }
    }

    public synchronized void enableAutomaticBackfilling(boolean enable) {
        if (this.automaticBackfillingEnabled == enable) {
            log.debug("automatic backfilling is already {}", automaticBackfillingEnabled ? "enabled" : "disabled");
            return;
        }
        for (var f : scheduledFutures) {
            f.cancel(true);
        }
        scheduledFutures.clear();
        this.automaticBackfillingEnabled = enable;

        if (enable) {
            scheduleAutoFillers();
        }

        log.debug("automatic backfilling has been {}", automaticBackfillingEnabled ? "enabled" : "disabled");
    }

    private void parseConfig(YConfiguration config, boolean defaultAutomaticBackfilling) {
        this.warmupTime = 1000L * config.getInt("warmupTime", 60);

        this.compactFrequency = config.getInt("compactFrequency", -1);

        if (config.containsKey("schedule")) {
            List<YConfiguration> l = config.getConfigList("schedule");
            schedules = new ArrayList<>(l.size());
            for (YConfiguration sch : l) {
                int segstart = sch.getInt("startSegment");
                int numseg = sch.getInt("numSegments");
                long interval = sch.getInt("interval", -1);
                Schedule s = new Schedule(segstart, numseg, interval);
                schedules.add(s);
            }
        }

        List<String> monitoredStreams;
        if (config.containsKey("monitorStreams")) {
            monitoredStreams = config.getList("monitorStreams");
        } else {
            StreamConfig sc = StreamConfig.getInstance(parchive.getYamcsInstance());
            monitoredStreams = new ArrayList<>();
            sc.getEntries(StandardStreamType.TM).forEach(sce -> monitoredStreams.add(sce.getName()));
            sc.getEntries(StandardStreamType.PARAM).forEach(sce -> monitoredStreams.add(sce.getName()));
        }

        if (!monitoredStreams.isEmpty()) {
            if (config.containsKey("streamUpdateFillPolicy")) {
                if (monitoredStreams.isEmpty()) {
                    log.warn("Monitored streams is empty, the streamUpdateFillPolicy will not be used");
                }
                List<YConfiguration> l = config.getConfigList("streamUpdateFillPolicy");
                for (YConfiguration sch : l) {
                    long dataAge = (long) (sch.getDouble("dataAge") * 3600_000);
                        long fillFrequency = sch.getLong("fillFrequency", 3600) * 1000;
                        long quietThreshold = sch.getLong("quietThreshold") * 1000;
                        streamUpdatePolicy.add(new StreamUpdatePolicyEnter(dataAge, fillFrequency, quietThreshold));
                }
                streamUpdatePolicy.sort(Comparator.comparingLong(StreamUpdatePolicyEnter::dataAge));
            } else if (config.containsKey("streamUpdateFillFrequency")) {
                var streamUpdateFillFrequency = 1000 * config.getLong("streamUpdateFillFrequency", 3600);
                streamUpdatePolicy.add(new StreamUpdatePolicyEnter(-1, streamUpdateFillFrequency, -1));
            } else {
                streamUpdatePolicy.add(new StreamUpdatePolicyEnter(-3600_000, 600_000, 10_000));
                streamUpdatePolicy.add(new StreamUpdatePolicyEnter(7200_000, -1, 60_000));
            }

            streamUpdates = new HashMap<>();
            subscribedStreams = new ArrayList<>(monitoredStreams.size());
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(parchive.getYamcsInstance());
            for (String streamName : monitoredStreams) {
                Stream s = ydb.getStream(streamName);
                if (s == null) {
                    throw new ConfigurationException(
                            "Cannot find stream '" + s + "' required for the parameter archive backfiller");
                }
                s.addSubscriber(this);
                subscribedStreams.add(s);
            }
        }
    }

    public Future<?> scheduleFillingTask(long start, long stop) {
        return executor.schedule(() -> runTask(start, stop), 0, TimeUnit.SECONDS);
    }

    private void runTask(long start, long stop) {
        try {
            start = ParameterArchive.getIntervalStart(start);
            stop = ParameterArchive.getIntervalEnd(stop) + 1;

            BackFillerTask bft = new BackFillerTask(parchive);
            bft.setCollectionStart(start);
            String timePeriod = '[' + TimeEncoding.toString(start) + "-" + TimeEncoding.toString(stop) + ')';
            log.debug("Starting parameter archive fillup for interval {}", timePeriod);
            long t0 = System.nanoTime();
            ReplayOptions rrb = ReplayOptions.getAfapReplay(start - warmupTime, stop, false);
            Processor proc = ProcessorFactory.create(parchive.getYamcsInstance(),
                    "ParameterArchive-backfilling_" + count.incrementAndGet(), "ParameterArchive", "internal",
                    rrb);
            bft.setProcessor(proc);
            proc.getParameterRequestManager().subscribeAll(bft);

            proc.start();
            proc.awaitTerminated();
            if (bft.aborted) {
                log.warn("Parameter archive fillup for interval {} aborted", timePeriod);
            } else {
                bft.flush();
                long t1 = System.nanoTime();
                log.debug("Parameter archive fillup for interval {} finished, processed {} samples in {} millisec",
                        timePeriod, bft.getNumProcessedParameters(), (t1 - t0) / 1_000_000);
                for (BackFillerListener listener : listeners) {
                    listener.onBackfillFinished(start, stop, bft.getNumProcessedParameters());
                }
            }
            if (compactFrequency != -1 && ++compactCount >= compactFrequency) {
                compactCount = 0;
                parchive.compact();
            }
        } catch (Exception e) {
            log.error("Error when running the archive filler task", e);
        }
    }

    private void runSchedule(Schedule s) {
        if (!automaticBackfillingEnabled) {
            return;
        }
        long start, stop;
        long intervalDuration = ParameterArchive.getIntervalDuration();
        if (s.frequency == -1) {
            start = t0 + (runCount - s.intervalStart) * intervalDuration;
            stop = start + s.numIntervals * intervalDuration - 1;
        } else {
            long now = timeService.getMissionTime();
            start = now - s.intervalStart * intervalDuration;
            stop = start + s.numIntervals * intervalDuration - 1;
        }
        runTask(start, stop);
    }

    private void checkStreamUpdates() {
        if (!automaticBackfillingEnabled) {
            return;
        }

        LongArray rebuildIntervals;
        synchronized (streamUpdates) {
            if (streamUpdates.isEmpty()) {
                return;
            }
            // wall clock time is used to compare with the lastUpdate and lastRebuild since these are set by the
            // System.currentTime
            var nowWc = System.currentTimeMillis();
            // mission time is used to compare with the interval time to get the data age
            var nowMt = timeService.getMissionTime();
            rebuildIntervals = new LongArray(streamUpdates.size());

            var it = streamUpdates.entrySet().iterator();

            while (it.hasNext()) {
                var streamUpdateEntry = it.next();
                long age = nowMt - streamUpdateEntry.getKey();

                var applicablePolicyEntry = streamUpdatePolicy.get(0);
                for (int i = 1; i < streamUpdatePolicy.size(); i++) {
                    var supe = streamUpdatePolicy.get(i);
                    if (age < supe.dataAge) {
                        break;
                    }
                    applicablePolicyEntry = supe;
                }
                var streamUpdate = streamUpdateEntry.getValue();
                if (applicablePolicyEntry.fillFrequency > 0
                        && nowWc - streamUpdate.lastRebuild > applicablePolicyEntry.fillFrequency) {
                    rebuildIntervals.add(streamUpdateEntry.getKey());
                    streamUpdate.lastRebuild = nowWc;
                    it.remove();
                } else if (applicablePolicyEntry.quietThreshold > 0
                        && nowWc - streamUpdate.lastUpdate > applicablePolicyEntry.quietThreshold) {
                    rebuildIntervals.add(streamUpdateEntry.getKey());
                    it.remove();
                }
            }
        }
        rebuildIntervals.sort();

        for (int i = 0; i < rebuildIntervals.size(); i++) {
            int j;
            for (j = i; j < rebuildIntervals.size() - 1; j++) {
                if (ParameterArchive.getIntervalEnd(rebuildIntervals.get(j)) != rebuildIntervals.get(j + 1)) {
                    break;
                }
            }
            runTask(rebuildIntervals.get(i), ParameterArchive.getIntervalEnd(rebuildIntervals.get(j)));
            i = j;
        }
    }

    // runs all schedules with interval -1
    private void runSegmentSchedules() {
        if (!automaticBackfillingEnabled) {
            return;
        }
        for (Schedule s : schedules) {
            if (s.frequency == -1) {
                runSchedule(s);
            }
        }
        runCount++;
    }

    static class Schedule {
        public Schedule(int intervalStart, int numIntervals, long frequency) {
            this.intervalStart = intervalStart;
            this.numIntervals = numIntervals;
            this.frequency = frequency;
        }

        int intervalStart;
        int numIntervals;
        long frequency;
    }

    public void shutDown() throws InterruptedException {
        if (subscribedStreams != null) {
            for (Stream s : subscribedStreams) {
                s.removeSubscriber(this);
            }
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        long gentime = tuple.getTimestampColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        if (gentime == TimeEncoding.INVALID_INSTANT) {
            log.warn("Ignorning tuple with invalid gentime {}", tuple);
            return;
        }
        long t0 = ParameterArchive.getIntervalStart(gentime);
        synchronized (streamUpdates) {
            long now = System.currentTimeMillis();
            var streamUpdate = streamUpdates.computeIfAbsent(t0, t -> new StreamUpdate(now));
            streamUpdate.lastUpdate = now;
        }
    }

    @Override
    public void streamClosed(Stream stream) {
        log.debug("Stream {} closed", stream.getName());
    }

    public void addListener(BackFillerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BackFillerListener listener) {
        listeners.remove(listener);
    }



    static class StreamUpdate {
        long lastUpdate;
        long lastRebuild;

        StreamUpdate(long lastRebuild) {
            this.lastRebuild = lastRebuild;
        }
    }

    /**
     * all values are milliseconds
     */
    static record StreamUpdatePolicyEnter(long dataAge, long fillFrequency, long quietThreshold) {
        @Override
        public String toString() {
            return String.format("StreamUpdatePolicyEnter{dataAge=%.2f h, fillFrequency=%.2f s, quietThreshold=%.2f s}",
                    dataAge / 3600000.0, fillFrequency / 1000.0, quietThreshold / 1000.0);
        }
    }

}
