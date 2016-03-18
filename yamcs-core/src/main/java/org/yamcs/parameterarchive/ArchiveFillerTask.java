package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;

class ArchiveFillerTask implements ParameterConsumer {
    final ParameterArchive parameterArchive;
    final Logger log;
    
    long numParams = 0;
    
  //segment start -> ParameterGroup_id -> PGSegment
    protected TreeMap<Long, Map<Integer, PGSegment>> pgSegments = new TreeMap<>();
    protected final ParameterIdDb parameterIdMap;
    protected final ParameterGroupIdDb parameterGroupIdMap;

    //ignore any data older than this
    protected long collectionSegmentStart;
    
    long threshold = 60000;

    public ArchiveFillerTask(ParameterArchive parameterArchive) {
        log = LoggerFactory.getLogger(this.getClass().getName()+"["+parameterArchive.getYamcsInstance()+"]");

        this.parameterArchive = parameterArchive;
        
        
        this.parameterIdMap = parameterArchive.getParameterIdDb();
        this.parameterGroupIdMap = parameterArchive.getParameterGroupIdDb();
    }
    

    void setCollectionSegmentStart(long collectionSegmentStart) {
        this.collectionSegmentStart = collectionSegmentStart;
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
        numParams+=pvList.size();
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

    void flush() {
        log.info("Starting a consolidation process, number of intervals: "+pgSegments.size());
        for(Map<Integer, PGSegment> m: pgSegments.values()) {
            consolidateAndWriteToArchive(m.values());
        }
    }
    
    /**
     * writes data into the archive
     * @param pgList
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
        
        while(t > nextSegmentStart + threshold) {
            Map<Integer, PGSegment> m = pgSegments.remove(collectionSegmentStart);
            if(m!=null) {
                log.debug("Writing to archive the segment: [{} - {})", TimeEncoding.toString(collectionSegmentStart), TimeEncoding.toString(nextSegmentStart));
                consolidateAndWriteToArchive(m.values());
            } 
            collectionSegmentStart = nextSegmentStart;
            nextSegmentStart = SortedTimeSegment.getNextSegmentStart(collectionSegmentStart);
        }
    }
    
    public long getNumProcessedParameters() {
        return numParams;
    }
    
    /*builds incrementally a list of parameter id and parameter value, sorted by parameter ids */
    class SortedParameterList {
        SortedIntArray parameterIdArray = new SortedIntArray();
        List<ParameterValue> sortedPvList = new ArrayList<ParameterValue>();

        void add(ParameterValue pv) {
            String fqn = pv.getParameterQualifiedNamed();
            Value engValue = pv.getEngValue();
            if(engValue==null) {
                log.warn("Ignoring parameter without engineering value: {} ", pv.getParameterQualifiedNamed());
                return;
            }
            Value rawValue = pv.getRawValue();
            Type engType = engValue.getType();
            Type rawType = (rawValue==null)? null: rawValue.getType();
            int parameterId = parameterIdMap.createAndGet(fqn, engType, rawType);

            int pos = parameterIdArray.insert(parameterId);
            sortedPvList.add(pos, pv);
        }

        public int size() {
            return parameterIdArray.size();
        }
    }
}