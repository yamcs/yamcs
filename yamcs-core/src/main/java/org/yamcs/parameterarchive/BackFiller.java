package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YProcessor;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;


public class BackFiller {
    List<Schedule> schedules;
    long t0;
    int runCount;
    ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(1);
    final ParameterArchive parchive;
    long warmupTime;
    final TimeService timeService;
    static AtomicInteger count = new AtomicInteger();
    private final Logger log = LoggerFactory.getLogger(BackFiller.class);

    BackFiller(ParameterArchive parchive, Map<String, Object> config) {
        this.parchive = parchive;
        if(config!=null) {
            parseConfig(config);
        }
        timeService = YamcsServer.getTimeService(parchive.getYamcsInstance());
    }

    void start() {
        if(schedules==null || schedules.isEmpty()) return;

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

    @SuppressWarnings("unchecked")
    private void parseConfig(Map<String, Object> config) {
        warmupTime = 1000L * YConfiguration.getInt(config, "warmupTime", 60);
        if(config.containsKey("schedule")) {
            List<Object> l = YConfiguration.getList(config, "schedule");
            schedules = new ArrayList<BackFiller.Schedule>(l.size());
            for(Object o: l) {
                if(!(o instanceof Map)) throw new ConfigurationException("Invalid schedule specification in "+config);
                Map<String, Object> m = (Map<String, Object>)o;
                int segstart = YConfiguration.getInt(m, "startSegment");
                int numseg = YConfiguration.getInt(m, "numSegments");
                long interval = YConfiguration.getInt(m, "interval", -1);
                Schedule s = new Schedule(segstart, numseg, interval);
                schedules.add(s);
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
            YProcessor yproc = ProcessorFactory.create(parchive.getYamcsInstance(), "ParameterArchive-backfilling_"+count.incrementAndGet(), "ParameterArchive", "internal", rrb.build());
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
        executor.shutdownNow();
    }
}
