package org.yamcs.parameterarchive;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.StringColumnFamilySerializer;
import org.yamcs.yarch.rocksdb.YRDB;

import com.google.common.util.concurrent.AbstractService;

public class ParameterArchive  extends AbstractService {
    static final String CF_NAME_meta_p2pid = "meta_p2pid";
    static final String CF_NAME_meta_pgid2pg = "meta_pgid2pg"; 
    static final String CF_NAME_data_prefix = "data_";

    private final Logger log = LoggerFactory.getLogger(ParameterArchive.class);
    private ParameterIdDb parameterIdMap;
    private ParameterGroupIdDb parameterGroupIdMap;
    YRDB yrdb;

    ColumnFamilyHandle p2pid_cfh;
    ColumnFamilyHandle pgid2pg_cfh;

    final private String yamcsInstance;
    private TreeMap<Long, Partition> partitions = new TreeMap<Long, ParameterArchive.Partition>();
    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();
    public final static boolean STORE_RAW_VALUES = true; 

    final TimeService timeService;
    private Map<String, Object> backFillerConfig;
    private Map<String, Object> realtimeFillerConfig;
    private boolean realtimeFillerEnabled = false;
    private BackFiller backFiller;
    private RealtimeArchiveFiller realtimeFiller;
    static StringColumnFamilySerializer cfSerializer = new StringColumnFamilySerializer();
    
    public ParameterArchive(String instance, Map<String, Object> args) throws IOException, RocksDBException {

        this.yamcsInstance = instance;
        this.timeService = YamcsServer.getTimeService(instance);
        String dbpath = YarchDatabase.getInstance(instance).getRoot() +"/ParameterArchive";
        File f = new File(dbpath+"/IDENTITY");
        if(f.exists()) {
            openExistingDb(dbpath);
        } else {
            createDb(dbpath);
        }
        parameterIdMap = new ParameterIdDb(yrdb.getDb(), p2pid_cfh);
        parameterGroupIdMap = new ParameterGroupIdDb(yrdb.getDb(), pgid2pg_cfh);

        if(args!=null) {
            processConfig(args);
        } else {
            backFiller = new BackFiller(this, null);
        }
        // HttpServer.getInstance().registerRouteHandler(yamcsInstance, new ArchiveParameter2RestHandler());
    }

    public ParameterArchive(String instance) throws RocksDBException, IOException {
        this(instance, null);
    }


    private void processConfig(Map<String, Object> args) {
        for(String s:args.keySet()) {
            if("backFiller".equals(s)) {
                backFillerConfig = YConfiguration.getMap(args, s);
                boolean backFillerEnabled = true;
                log.debug("backFillerConfig: {}", backFillerConfig);
                if(backFillerConfig.containsKey("enabled")) {
                    backFillerEnabled  = YConfiguration.getBoolean(backFillerConfig, "enabled");
                }
                if(backFillerEnabled) {
                    backFiller = new BackFiller(this, backFillerConfig);
                }
            } else if("realtimeFiller".equals(s)) {
                realtimeFillerConfig = YConfiguration.getMap(args, s);
                realtimeFillerEnabled = YConfiguration.getBoolean(realtimeFillerConfig, "enabled", false);
                log.debug("realtimeFillerConfig: {}", realtimeFillerConfig);
                if(realtimeFillerEnabled) {
                    realtimeFiller = new RealtimeArchiveFiller(this, realtimeFillerConfig);
                }
            } else {
                throw new ConfigurationException("Unkwnon keyword '"+s+"' in parameter archive configuration: "+args);
            }
        }
    }

    private void createDb(String dbpath) throws RocksDBException, IOException {
        log.info("Creating new ParameterArchive RocksDb at {}", dbpath);
        yrdb = RDBFactory.getInstance(yamcsInstance).getRdb(dbpath, cfSerializer, false);
        p2pid_cfh = yrdb.createColumnFamily(CF_NAME_meta_p2pid);
        pgid2pg_cfh = yrdb.createColumnFamily(CF_NAME_meta_pgid2pg);
    }

    private void openExistingDb(String dbpath) throws IOException {
        log.info("Opening existing ParameterArchive RocksDb at {}", dbpath);
        yrdb = RDBFactory.getInstance(yamcsInstance).getRdb(dbpath, cfSerializer, false);
        
        p2pid_cfh = yrdb.getColumnFamilyHandle(CF_NAME_meta_p2pid);
        pgid2pg_cfh = yrdb.getColumnFamilyHandle(CF_NAME_meta_pgid2pg);
        
        Collection<Object> l = yrdb.getColumnFamilies();
        for(Object o: l) {
            String cn = (String)o;
            if(cn.startsWith(CF_NAME_data_prefix)) {
                long partitionId = decodePartitionId(CF_NAME_data_prefix, cn);
                Partition p = partitions.get(partitionId);
                if(p==null) {
                    p = new Partition(partitionId);
                    partitions.put(partitionId, p);
                }
                p.dataCfh = yrdb.getColumnFamilyHandle(o);
            } else if(!"default".equals(cn) && !CF_NAME_meta_p2pid.equals(cn) && !CF_NAME_meta_pgid2pg.equals(cn)){
                log.warn("Unknown column family '"+cn+"'");
            }
        }
      
        if(p2pid_cfh==null) {
            throw new ParameterArchiveException("Cannot find column family '"+CF_NAME_meta_p2pid+"' in database at "+dbpath);
        }
        if(pgid2pg_cfh==null) {
            throw new ParameterArchiveException("Cannot find column family '"+CF_NAME_meta_pgid2pg+"' in database at "+dbpath);
        }      
    }

    static long decodePartitionId(String prefix, String cfname) {
        try {
            return Long.parseLong(cfname.substring(prefix.length()), 16);
        } catch (NumberFormatException e) {
            throw new ParameterArchiveException("Cannot decode partition id from column family: "+cfname);
        }
    }

    public ParameterIdDb getParameterIdDb() {
        return parameterIdMap;
    }

    public ParameterGroupIdDb getParameterGroupIdDb() {
        return parameterGroupIdMap;
    }


    /**
     * used from unit tests to force closing the database
     */
    void closeDb() {
        RDBFactory.getInstance(yamcsInstance).close(yrdb);
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public void writeToArchive(Collection<PGSegment> pgList) throws RocksDBException {
        try (WriteBatch writeBatch = new WriteBatch();
                WriteOptions wo = new WriteOptions()) {

            for(PGSegment pgs: pgList) {
                writeToBatch(writeBatch, pgs);
            }
            yrdb.getDb().write(wo, writeBatch);
        }
    }

    private void writeToBatch(WriteBatch writeBatch, PGSegment pgs) throws RocksDBException {
        long segStart = pgs.getSegmentStart();
        long partitionId = Partition.getPartitionId(segStart);
        Partition p = createAndGetPartition(partitionId);

        //write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(ParameterIdDb.TIMESTAMP_PARA_ID, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] timeValue = vsEncoder.encode(timeSegment);
        writeBatch.put(p.dataCfh, timeKey, timeValue);

        //and then the consolidated value segments
        List<BaseSegment> consolidated = pgs.getConsolidatedValueSegments();
        List<BaseSegment> consolidatedRawValues = pgs.getConsolidatedRawValueSegments();
        List<ParameterStatusSegment> satusSegments = pgs.getConsolidatedParameterStatusSegments();

        for(int i=0; i<consolidated.size(); i++) {
            BaseSegment vs = consolidated.get(i);
            int parameterId = pgs.getParameterId(i);
            String pname = parameterIdMap.getParameterbyId(parameterId);
            if(vs.size()!=timeSegment.size()) {
                throw new RuntimeException("Trying to write to archive an engineering value segment whose size ("+vs.size()+") is different than the time segment ("+timeSegment.size()+") "
                        +"for parameterId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
            }
            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = vsEncoder.encode(vs);
            writeBatch.put(p.dataCfh, engKey, engValue);

            if(STORE_RAW_VALUES && consolidatedRawValues!=null) {
                BaseSegment rvs = consolidatedRawValues.get(i);
                if(rvs!=null) {
                    if(rvs.size()!=timeSegment.size()) {
                        throw new RuntimeException("Trying to write to archive an raw value segment whose size ("+rvs.size()+") is different than the time segment ("+timeSegment.size()+") "
                                +"for parameerId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
                    }
                    byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_RAW_VALUE).encode();
                    byte[] rawValue = vsEncoder.encode(rvs);
                    writeBatch.put(p.dataCfh, rawKey, rawValue);

                }
            }
            ParameterStatusSegment pss = satusSegments.get(i);
            if(pss.size()!=timeSegment.size()) {
                throw new RuntimeException("Trying to write to archive an parameter status segment whose size ("+pss.size()+") is different than the time segment ("+timeSegment.size()+") "
                        +"for parameterId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
            }
            byte[] pssKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_PARAMETER_STATUS).encode();
            byte[] pssValue = vsEncoder.encode(pss);
            writeBatch.put(p.dataCfh, pssKey, pssValue);
        }
    }


    /**
     * get partition for id, creating it if it doesn't exist
     * @param partitionId
     * @return
     * @throws RocksDBException 
     */
    private Partition createAndGetPartition(long partitionId) throws RocksDBException {
        synchronized(partitions) {
            Partition p = partitions.get(partitionId);
            if(p==null) {
                p = new Partition(partitionId);
                String cfname = CF_NAME_data_prefix + Long.toHexString(partitionId);
                p.dataCfh = yrdb.createColumnFamily(cfname);
                partitions.put(partitionId, p);
            }
            return p;
        }
    }

    public Future<?> reprocess(long start, long stop) {
        log.debug("Scheduling a reprocess for interval [{} - {}]", TimeEncoding.toString(start), TimeEncoding.toString(stop));
        if(backFiller==null) {
            throw new ConfigurationException("backFilling is not enabled");
        }
        return backFiller.scheduleFillingTask(start, stop);
    }


    /** 
     * a copy of the partitions from start to stop inclusive
     * @param startPartitionId
     * @param stopPartitionId
     * @return a navigable map of partitions sorted by their id
     */
    public NavigableMap<Long, Partition> getPartitions(long startPartitionId, long stopPartitionId) {
        if((startPartitionId& Partition.TIMESTAMP_MASK) != 0) {
            throw new IllegalArgumentException(startPartitionId+" is not a valid partition id");
        }
        if((stopPartitionId& Partition.TIMESTAMP_MASK) != 0) {
            throw new IllegalArgumentException(stopPartitionId+" is not a valid partition id");
        }

        synchronized(partitions) {
            TreeMap<Long, Partition> r = new TreeMap<Long, ParameterArchive.Partition>();
            r.putAll(partitions.subMap(startPartitionId, true, stopPartitionId, true));
            return r;
        }
    }

    static class Partition {
        public static final int NUMBITS_MASK=31; //2^31 millisecons =~ 24 days per partition    
        public static final long TIMESTAMP_MASK = (0xFFFFFFFF>>>(32-NUMBITS_MASK));
        public static final long PARTITION_MASK = ~TIMESTAMP_MASK;

        final long partitionId;
        ColumnFamilyHandle dataCfh;

        Partition(long partitionId) {
            this.partitionId = partitionId;
        }        


        static long getPartitionId(long instant) {
            return instant & PARTITION_MASK;
        }

        static long getPartitionStart(long instant) {
            return getPartitionId(instant);
        }

        public static long getPartitionEnd(long partitionId) {
            return partitionId  | TIMESTAMP_MASK;
        }

        public String toString() {
            return "partition: ["+TimeEncoding.toString(partitionId)+" - "+TimeEncoding.toString(getPartitionEnd(partitionId))+"]";
        }
    }

    public RocksIterator getIterator(Partition p) {
        return yrdb.getDb().newIterator(p.dataCfh);
    }

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart,  int parameterGroupId) throws RocksDBException, DecodingException {
        byte[] timeKey = new SegmentKey(ParameterIdDb.TIMESTAMP_PARA_ID, parameterGroupId, segmentStart, SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] tv = yrdb.getDb().get(p.dataCfh, timeKey);
        if(tv==null) {
            return null;
        }
        return (SortedTimeSegment) vsEncoder.decode(tv, segmentStart);
    }

    Partition getPartitions(long partitionId) {
        synchronized(partitions) {
            return partitions.get(partitionId);
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
        if(backFiller!=null) {
            backFiller.start();
        }
        if(realtimeFiller!=null) {
            realtimeFiller.start();
        }
    }

    @Override
    protected void doStop() {
        log.debug("Stopping ParameterArchive service for instance {}", yamcsInstance);
        
        if(backFiller!=null) {
            backFiller.stop();
        }

        if(realtimeFiller!=null) {
            realtimeFiller.stop();
        }
        RDBFactory.getInstance(yamcsInstance).dispose(yrdb);
        notifyStopped();
    }


    public void printStats(PrintStream out)  {
        try {
            for(Partition p:partitions.values()) {
                out.println("---------- Partition starting at "+TimeEncoding.toString(p.partitionId)+" -------------");
                out.println(yrdb.getDb().getProperty(p.dataCfh, "rocksdb.stats"));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }
    public void printKeys(PrintStream out) {
        out.println("pid\t pgid\t type\tSegmentStart\tcount\tsize\tstype");
        SegmentEncoderDecoder decoder = new SegmentEncoderDecoder();
        for(Partition p:partitions.values()) {
            try(RocksIterator it = getIterator(p)) {
                it.seekToFirst();
                while(it.isValid()) {
                    SegmentKey key = SegmentKey.decode(it.key());
                    byte[] v = it.value();
                    BaseSegment s;
                    try {
                        s = decoder.decode(it.value(), key.segmentStart);
                    } catch (DecodingException e) {
                        it.close();
                        throw new RuntimeException(e);
                    }
                    out.println(key.parameterId+"\t "+key.parameterGroupId+"\t "+key.type+"\t"+TimeEncoding.toString(key.segmentStart)+"\t"+s.size()+"\t"+v.length+"\t"+s.getClass().getSimpleName());
                    it.next();
                }
            }
        }
    }

    /**
     * Delete all partitions that overlap with [start, stop) segment.
     * @param start
     * @param stop
     * @throws RocksDBException 
     * @return all the partitions removed
     */
    public NavigableMap<Long, Partition> deletePartitions(long start, long stop) throws RocksDBException {
        long startPartitionId = Partition.getPartitionId(start);
        long stopPartitionId = Partition.getPartitionId(stop);
        NavigableMap<Long, Partition> parts = getPartitions(startPartitionId, stopPartitionId);
        for(Partition p: parts.values()) {
            yrdb.dropColumnFamily(p.dataCfh);
            partitions.remove(p.partitionId);
        }

        return parts;
    }
}
