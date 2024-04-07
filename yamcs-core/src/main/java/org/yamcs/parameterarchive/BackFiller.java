package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * 
 * @author nm
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
    private Set<Long> streamUpdates;
    // streams which are monitored
    private List<Stream> subscribedStreams;
    // how often (in seconds) the fillup based on the stream monitoring is started
    long streamUpdateFillFrequency;

    // after how many backfilling tasks to trigger a parchive.compact()
    int compactFrequency = 5;

    int compactCount = 0;

    private List<BackFillerListener> listeners = new CopyOnWriteArrayList<>();

    BackFiller(ParameterArchive parchive, YConfiguration config) {
        this.parchive = parchive;
        this.log = new Log(BackFiller.class, parchive.getYamcsInstance());
        parseConfig(config);
        timeService = YamcsServer.getTimeService(parchive.getYamcsInstance());
        executor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactoryBuilder().setNameFormat("ParameterArchive-BackFiller-" + parchive.getYamcsInstance())
                        .build());

    }

    public static Spec getSpec() {
        Spec spec = new Spec();

        spec.addOption("enabled", OptionType.BOOLEAN);
        spec.addOption("warmupTime", OptionType.INTEGER).withDefault(60);
        spec.addOption("monitorStreams", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("streamUpdateFillFrequency", OptionType.INTEGER).withDefault(600);

        Spec schedSpec = new Spec();
        schedSpec.addOption("startInterval", OptionType.INTEGER);
        schedSpec.addOption("numIntervals", OptionType.INTEGER);

        spec.addOption("schedule", OptionType.MAP).withSpec(schedSpec);
        spec.addOption("compactFrequency", OptionType.INTEGER).withDefault(5);

        return spec;

    }

    void start() {
        if (schedules != null && !schedules.isEmpty()) {
            int c = 0;
            for (Schedule s : schedules) {
                if (s.frequency == -1) {
                    c++;
                    continue;
                }

                executor.scheduleAtFixedRate(() -> {
                    runSchedule(s);
                }, 0, s.frequency, TimeUnit.SECONDS);
            }
            if (c > 0) {
                long now = timeService.getMissionTime();
                t0 = ParameterArchive.getIntervalStart(now);

                executor.schedule(() -> {
                    runSegmentSchedules();
                }, t0 - now, TimeUnit.MILLISECONDS);
            }
        }
        if (subscribedStreams != null && !subscribedStreams.isEmpty()) {
            executor.scheduleAtFixedRate(() -> {
                checkStreamUpdates();
            }, streamUpdateFillFrequency, streamUpdateFillFrequency, TimeUnit.SECONDS);
        }
    }

    private void parseConfig(YConfiguration config) {
        warmupTime = 1000L * config.getInt("warmupTime", 60);

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

        streamUpdateFillFrequency = config.getLong("streamUpdateFillFrequency", 600);
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
            streamUpdates = new HashSet<>();
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
        this.compactFrequency = config.getInt("compactFrequency", 5);
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
        long[] a;
        synchronized (streamUpdates) {
            if (streamUpdates.isEmpty()) {
                return;
            }
            a = new long[streamUpdates.size()];
            int i = 0;
            for (Long l : streamUpdates) {
                a[i++] = l;
            }
            streamUpdates.clear();
        }
        Arrays.sort(a);
        for (int i = 0; i < a.length; i++) {
            int j;
            for (j = i; j < a.length - 1; j++) {
                if (ParameterArchive.getIntervalStart(a[j]) != a[j + 1]) {
                    break;
                }
            }
            runTask(a[i], a[j]);
            i = j;
        }
    }

    // runs all schedules with interval -1
    private void runSegmentSchedules() {
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
            streamUpdates.add(t0);
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
}
