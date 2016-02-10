package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.ProcessorFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;

class ArchiveFillerTask implements ParameterConsumer {
    final ParameterArchive parameterArchive;
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    
    final YProcessor yproc;
    
    
  //segment start -> ParameterGroup_id -> PGSegment
    protected TreeMap<Long, Map<Integer, PGSegment>> pgSegments = new TreeMap<>();
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;

    //ignore any data older than this
    long collectionSegmentStart;
    
    long threshold = 60000;
    static AtomicInteger count = new AtomicInteger();
    
    public ArchiveFillerTask(ParameterArchive parameterArchive, long start, long stop) throws org.yamcs.ConfigurationException, YProcessorException {
        this.parameterArchive = parameterArchive;
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
        
        collectionSegmentStart = SortedTimeSegment.getSegmentStart(start);
        long segmentEnd = SortedTimeSegment.getSegmentEnd(stop);
        //start ahead with one minute
        start = collectionSegmentStart-60000;
        String timePeriod = '['+TimeEncoding.toString(collectionSegmentStart)+"-"+ TimeEncoding.toString(segmentEnd)+']';
        log.info("Starting an parameter archive fillup for segment {}", timePeriod );
        
        
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder().setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        rrb.setEndAction(EndAction.QUIT);
        rrb.setStart(start).setStop(stop);
        rrb.setPacketRequest(PacketReplayRequest.newBuilder().build());
        rrb.setPpRequest(PpReplayRequest.newBuilder().build());
        yproc = ProcessorFactory.create(parameterArchive.getYamcsInstance(), "ParameterArchive-buildup_"+count.incrementAndGet(), "ParameterArchive", "internal", rrb.build());
        yproc.getParameterRequestManager().subscribeAll(this);
    }
    
    /**
     * adds the parameters to the pgSegments structure and return the highest timestamp or -1 if all parameters have been ignored (because they were too old)
     * 
     * parameters older than ignoreOlderThan are ignored.
     * 
     * 
     * @param items
     * @return
     */
    protected long processParameters(List<ParameterValue> items) {
        Map<Long, SortedParameterList> m = new HashMap<>();
        for(ParameterValue pv: items) {
            long t = pv.getGenerationTime();
            if(t<collectionSegmentStart) {
                continue;
            }
            if(pv.getParameterQualifiedNamed()==null) {
                log.warn("No qualified name for parameter value {}, ignoring", pv);
                continue;
            }
            
            SortedParameterList l = m.get(t);
            if(l==null) {
                l = new SortedParameterList();
                m.put(t, l);
            }
            l.add(pv);
        }
        long maxTimestamp = -1;
        for(Map.Entry<Long,SortedParameterList> entry: m.entrySet()) {
            long t = entry.getKey();
            SortedParameterList pvList = entry.getValue();
            processParameters(t, pvList);
            if(t>maxTimestamp) maxTimestamp = t;
        }
        return maxTimestamp;
    } 
    
    private void processParameters(long t, SortedParameterList pvList) {
        try {
            int parameterGroupId = parameterGroupIdMap.createAndGet(pvList.parameterIdArray);
            long segmentId = SortedTimeSegment.getSegmentId(t);
            Map<Integer, PGSegment> m = pgSegments.get(segmentId);
            if(m==null) {
                m = new HashMap<Integer, PGSegment>();
                pgSegments.put(segmentId, m);
            }
            PGSegment pgs = m.get(parameterGroupId);
            if(pgs==null) {
                pgs = new PGSegment(parameterGroupId, segmentId, pvList.parameterIdArray);
                m.put(parameterGroupId, pgs);
            }

            pgs.addRecord(t, pvList.sortedPvList);

        } catch (RocksDBException e) {
            log.error("Error processing parameters", e);
        }

    }
    public void run() {
        yproc.start();
        yproc.awaitTerminated();
        flush();
    }

    void flush() {
        log.info("Starting a consolidation process, number of intervals: "+pgSegments.size());
        for(Map<Integer, PGSegment> m: pgSegments.values()) {
            consolidateAndWriteToArchive(m.values());
        }
    }
    
    /**
     * writes data into the archive
     * @param pgs
     */
    protected void consolidateAndWriteToArchive(Collection<PGSegment> pgList) {
        for(PGSegment pgs: pgList) {
            pgs.consolidate();
        }
        try {
            parameterArchive.writeToArchive(pgList);
        } catch (RocksDBException e) {
            log.error("failed to write data to the archive", e);
        }
    }

   

   

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        long t = processParameters(items);
        if(t<0)return;
        
        long nextSegmentStart = SortedTimeSegment.getNextSegmentStart(collectionSegmentStart);
        
        while(t>nextSegmentStart + threshold) {
            Map<Integer, PGSegment> m = pgSegments.remove(collectionSegmentStart);
            if(m!=null) {
                log.debug("Writing to archive the segment: [{} - {})", TimeEncoding.toString(collectionSegmentStart), TimeEncoding.toString(nextSegmentStart));
                consolidateAndWriteToArchive(m.values());
            } 
            collectionSegmentStart = nextSegmentStart;
            nextSegmentStart = SortedTimeSegment.getNextSegmentStart(collectionSegmentStart);
        }
    }
    
    


    /*builds incrementally a list of parameter id and parameter value, sorted by parameter ids */
    class SortedParameterList {
        SortedIntArray parameterIdArray = new SortedIntArray();
        List<ParameterValue> sortedPvList = new ArrayList<ParameterValue>();

        void add(ParameterValue pv) {
            String fqn = pv.getParameterQualifiedNamed();
            Value.Type engType = pv.getEngValue().getType();
            Value.Type rawType = (pv.getRawValue()==null)? null: pv.getRawValue().getType();
            int parameterId = parameterIdMap.createAndGet(fqn, engType, rawType);

            int pos = parameterIdArray.insert(parameterId);
            sortedPvList.add(pos, pv);
        }

    }
}