package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorFactory;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.YConfiguration;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.tctm.TmDataLinkInitialiser;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Back-fills the parameter archive by triggering replays:
 *  - either regularly scheduled replays
 *  - or monitor data streams (tm, param) and keep track of which segments have to be rebuild
 * 
 * 
 * @author nm
 *
 */
public class BackFiller implements StreamSubscriber {
    List<Schedule> schedules;
    long t0;
    int runCount;
    ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(1);
    final ParameterArchive parchive;
    long warmupTime;
    final TimeService timeService;
    static AtomicInteger count = new AtomicInteger();
    private final Logger log = LoggerFactory.getLogger(BackFiller.class);

    //set of segments that have to be rebuilt following monitoring of streams
    private Set<Long> streamUpdates;
    //streams which are monitored
    private List<Stream> subscribedStreams;
    //how often (in seconds) the fillup based on the stream monitoring is started
    long streamUpdateFillFrequency;

    BackFiller(ParameterArchive parchive, Map<String, Object> config) {
        this.parchive = parchive;
        if(config!=null) {
            parseConfig(config);
        }
        timeService = YamcsServer.getTimeService(parchive.getYamcsInstance());
    }

    void start() {
        if(schedules!=null && !schedules.isEmpty()) {
            int c = 0;
            for(Schedule s:schedules) {
                if(s.interval==-1) {
                    c++;
                    continue;
                }

                executor.scheduleAtFixedRate(()-> {
                    runSchedule(s);
                }, 0, s.interval, TimeUnit.SECONDS);
            }
            if(c>0) {
                long now = timeService.getMissionTime();
                t0 = SortedTimeSegment.getNextSegmentStart(now); 

                executor.schedule(() -> {
                    runSegmentSchedules();
                }, t0-now, TimeUnit.MILLISECONDS);
            }
        }
        if(subscribedStreams!=null && !subscribedStreams.isEmpty()) {
            executor.scheduleAtFixedRate(() -> {
                checkStreamUpdates();
            }, streamUpdateFillFrequency, streamUpdateFillFrequency, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseConfig(Map<String, Object> config) {
        warmupTime = 1000L * YConfiguration.getInt(config, "warmupTime", 60);
        if(config.containsKey("schedule")) {
            List<Object> l = YConfiguration.getList(config, "schedule");
            schedules = new ArrayList<>(l.size());
            for(Object o: l) {
                if(!(o instanceof Map)) {
                    throw new ConfigurationException("Invalid schedule specification in "+config);
                }
                Map<String, Object> m = (Map<String, Object>)o;
                int segstart = YConfiguration.getInt(m, "startSegment");
                int numseg = YConfiguration.getInt(m, "numSegments");
                long interval = YConfiguration.getInt(m, "interval", -1);
                Schedule s = new Schedule(segstart, numseg, interval);
                schedules.add(s);
            }
        }

        streamUpdateFillFrequency = YConfiguration.getLong(config, "streamUpdateFillFrequency", 600);
        List<String> monitoredStreams;
        if(config.containsKey("monitorStreams")) {
            monitoredStreams = YConfiguration.getList(config, "monitorStreams");
        } else {
            StreamConfig sc = StreamConfig.getInstance(parchive.getYamcsInstance());
            monitoredStreams = new ArrayList<>();
            sc.getEntries(StandardStreamType.tm).forEach(sce -> monitoredStreams.add(sce.getName()));
            sc.getEntries(StandardStreamType.param).forEach(sce -> monitoredStreams.add(sce.getName()));
        }
        if(!monitoredStreams.isEmpty()) {
            streamUpdates = new HashSet<>();
            subscribedStreams = new ArrayList<>(monitoredStreams.size());
            YarchDatabase ydb = YarchDatabase.getInstance(parchive.getYamcsInstance());
            for(String streamName: monitoredStreams) {
                Stream s = ydb.getStream(streamName);
                if(s==null) {
                    throw new ConfigurationException("Cannot find stream '"+s+"' required for the parameter archive backfiller");
                }
                s.addSubscriber(this);
                subscribedStreams.add(s);
            }
        }
    }

    public Future<?> scheduleFillingTask(long start, long stop) {
        return executor.schedule(()->runTask(start,stop), 0, TimeUnit.SECONDS);
    }

    private void runTask(long start , long stop) {
        try {
            start = SortedTimeSegment.getSegmentStart(start);
            stop = SortedTimeSegment.getSegmentEnd(stop)+1;

            ArchiveFillerTask aft = new ArchiveFillerTask(parchive);
            aft.setCollectionSegmentStart(start);
            String timePeriod = '['+TimeEncoding.toString(start)+"-"+ TimeEncoding.toString(stop)+')';
            log.info("Starting an parameter archive fillup for interval {}", timePeriod );

            ReplayRequest.Builder rrb = ReplayRequest.newBuilder().setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
            rrb.setEndAction(EndAction.QUIT);
            rrb.setStart(start-warmupTime).setStop(stop);
            rrb.setPacketRequest(PacketReplayRequest.newBuilder().build());
            rrb.setPpRequest(PpReplayRequest.newBuilder().build());
            Processor yproc = ProcessorFactory.create(parchive.getYamcsInstance(), "ParameterArchive-backfilling_"+count.incrementAndGet(), "ParameterArchive", "internal", rrb.build());
            yproc.getParameterRequestManager().subscribeAll(aft);

            yproc.start();
            yproc.awaitTerminated();
            aft.flush();

            log.info("Parameter archive fillup for interval {} finished, number of processed parameter samples: {}", timePeriod, aft.getNumProcessedParameters() );
        }  catch (Exception e) {
            log.error("Error when running the archive filler task",e);
        }
    }

    private void runSchedule(Schedule s) {
        long start, stop;
        long segmentDuration = SortedTimeSegment.getSegmentDuration();
        if(s.interval==-1) {
            start = t0 + (runCount-s.segmentStart)*segmentDuration;
            stop = start + s.numSegments*segmentDuration-1;
        } else {
            long now = timeService.getMissionTime();
            start = now - s.segmentStart*segmentDuration;
            stop = start + s.numSegments*segmentDuration-1;
        }
        runTask(start, stop);
    }


    private void checkStreamUpdates() {
        long[] a;
        synchronized(streamUpdates) {
            if(streamUpdates.isEmpty()) {
                return;
            }
            a = new long[streamUpdates.size()];
            int i=0;
            for(Long l: streamUpdates) {
                a[i++]=l;
            }
            streamUpdates.clear();
        }
        Arrays.sort(a);
        for(int i = 0; i<a.length; i++) {
            int j;
            for(j=i; j<a.length-1; j++) {
                if(SortedTimeSegment.getNextSegmentStart(a[j])!=a[j+1]) {
                    break;  
                }
            }
            runTask(a[i], a[j]);
            i=j;
        }
    }



    //runs all schedules with interval -1
    private void runSegmentSchedules() {
        for(Schedule s: schedules) {
            if(s.interval==-1) {
                runSchedule(s);
            }
        }
        runCount++;
    }

    static class Schedule {
        public Schedule(int segmentStart, int numSegments, long interval) {
            this.segmentStart = segmentStart;
            this.numSegments = numSegments;
            this.interval = interval;
        }
        int segmentStart;
        int numSegments;
        long interval;
    }

    public void stop() {
        if(subscribedStreams!=null) {
            for(Stream s: subscribedStreams) {
                s.removeSubscriber(this);
            }
        }
        executor.shutdownNow();
    }


    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        long gentime = (Long) tuple.getColumn(TmDataLinkInitialiser.GENTIME_COLUMN);
        long t0 = SortedTimeSegment.getSegmentStart(gentime);
        synchronized(streamUpdates) {
            streamUpdates.add(t0);
        }
    }

    @Override
    public void streamClosed(Stream stream) {
    }
}
