package org.yamcs.parameterarchive;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
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

import com.google.common.util.concurrent.AbstractService;

public class ParameterArchive  extends AbstractService {
    static final byte[] CF_NAME_meta_p2pid = "meta_p2pid".getBytes(StandardCharsets.US_ASCII);
    static final byte[] CF_NAME_meta_pgid2pg = "meta_pgid2pg".getBytes(StandardCharsets.US_ASCII); 
    static final byte[] CF_NAME_data_prefix = "data_".getBytes(StandardCharsets.US_ASCII);

    private final Logger log = LoggerFactory.getLogger(ParameterArchive.class);
    private ParameterIdDb parameterIdMap;
    private ParameterGroupIdDb parameterGroupIdMap;
    RocksDB rdb;

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
    
    public ParameterArchive(String instance, Map<String, Object> args) throws RocksDBException {
      
        this.yamcsInstance = instance;
        this.timeService = YamcsServer.getTimeService(instance);
        String dbpath = YarchDatabase.getInstance(instance).getRoot() +"/ParameterArchive";
        File f = new File(dbpath+"/IDENTITY");
        if(f.exists()) {
            openExistingDb(dbpath);
        } else {
            createDb(dbpath);
        }
        parameterIdMap = new ParameterIdDb(rdb, p2pid_cfh);
        parameterGroupIdMap = new ParameterGroupIdDb(rdb, pgid2pg_cfh);
        
        if(args!=null) {
            processConfig(args);
        } else {
            backFiller = new BackFiller(this, null);
        }
       // HttpServer.getInstance().registerRouteHandler(yamcsInstance, new ArchiveParameter2RestHandler());
    }

    public ParameterArchive(String instance) throws RocksDBException {
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
            } else {
                throw new ConfigurationException("Unkwnon keyword '"+s+"' in parameter archive configuration: "+args);
            }
        }
    }

    private void createDb(String dbpath) throws RocksDBException {
        log.info("Creating new ParameterArchive RocksDb at {}", dbpath);
        ColumnFamilyDescriptor cfd_p2pid = new ColumnFamilyDescriptor(CF_NAME_meta_p2pid);
        ColumnFamilyDescriptor cfd_pgid2pg = new ColumnFamilyDescriptor(CF_NAME_meta_pgid2pg);
        ColumnFamilyDescriptor cfd_default = new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY);


        List<ColumnFamilyDescriptor> cfdList = Arrays.asList(cfd_p2pid, cfd_pgid2pg, cfd_default);
        List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfdList.size());
        DBOptions options = new DBOptions();
        options.setCreateIfMissing(true);
        options.setCreateMissingColumnFamilies(true);
        rdb = RocksDB.open(options, dbpath, cfdList, cfhList);
        p2pid_cfh = cfhList.get(0);
        pgid2pg_cfh = cfhList.get(1);
    }

    private void openExistingDb(String dbpath) throws RocksDBException {
        log.info("Opening existing ParameterArchive RocksDb at {}", dbpath);
        List<byte[]> cfList = RocksDB.listColumnFamilies(new Options(), dbpath);
        List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfList.size());
        for(byte[] cf:cfList) {
            ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(cf);
            cfdList.add(cfd);
        }

        List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfList.size());
        DBOptions options = new DBOptions();
        options.setCreateIfMissing(true);
        options.setCreateMissingColumnFamilies(true);
        rdb = RocksDB.open(options, dbpath, cfdList, cfhList);
        for(int i=0; i<cfdList.size(); i++) {
            byte[] cf = cfList.get(i);

            if(Arrays.equals(CF_NAME_meta_p2pid, cf)) {
                p2pid_cfh = cfhList.get(i);
            } else if(Arrays.equals(CF_NAME_meta_pgid2pg, cf)) {
                pgid2pg_cfh = cfhList.get(i);
            }  else if(startsWith(cf, CF_NAME_data_prefix)) {
                long partitionId = decodePartitionId(CF_NAME_data_prefix, cf);
                Partition p = partitions.get(partitionId);
                if(p==null) {
                    p = new Partition(partitionId);
                    partitions.put(partitionId, p);
                }
                p.dataCfh = cfhList.get(i);
            } else {
                if(!Arrays.equals(RocksDB.DEFAULT_COLUMN_FAMILY, cf)) {
                    log.warn("Ignoring unknown column family "+new String(cf, StandardCharsets.US_ASCII));
                }
            }
        }
        if(p2pid_cfh==null) {
            throw new ParameterArchiveException("Cannot find column family '"+new String(CF_NAME_meta_p2pid, StandardCharsets.US_ASCII)+"' in database at "+dbpath);
        }
        if(pgid2pg_cfh==null) {
            throw new ParameterArchiveException("Cannot find column family '"+new String(CF_NAME_meta_pgid2pg, StandardCharsets.US_ASCII)+"' in database at "+dbpath);
        }      
    }

    static long decodePartitionId(byte[] prefix, byte[] cf) {
        int l = prefix.length;
        try {
            return Long.parseLong(new String(cf, l, cf.length-l, StandardCharsets.US_ASCII), 16);
        } catch (NumberFormatException e) {
            byte[] b = new byte[cf.length-l];
            System.arraycopy(cf, l, b, 0, b.length);
            throw new ParameterArchiveException("Cannot decode partition id from column family: "+Arrays.toString(b)+" string: "+new String(b, StandardCharsets.US_ASCII));
        }
    }

    static byte[] encodePartitionId(byte[] prefix, long partitionId) {
        byte[] pb = Long.toHexString(partitionId).getBytes(StandardCharsets.US_ASCII);
        byte[] cf = Arrays.copyOf(prefix, prefix.length+pb.length);
        System.arraycopy(pb, 0, cf, prefix.length, pb.length);
        return cf;
    }


    public ParameterIdDb getParameterIdDb() {
        return parameterIdMap;
    }

    public ParameterGroupIdDb getParameterGroupIdDb() {
        return parameterGroupIdMap;
    }


    /**
     * returns true if a starts with prefix
     * @param a
     * @param prefix
     * @return
     */
    private boolean startsWith(byte[] a, byte[] prefix) {
        int n = prefix.length;
        if(a.length<n) return false;

        for(int i=0;i<n;i++) {
            if(a[i]!=prefix[i]) return false;
        }
        return true;
    }

    public void close() {
        rdb.close();
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public void writeToArchive(Collection<PGSegment> pgList) throws RocksDBException {
        WriteBatch writeBatch = new WriteBatch();
        for(PGSegment pgs: pgList) {
            writeToBatch(writeBatch, pgs);
        }
        WriteOptions wo = new WriteOptions();

        rdb.write(wo, writeBatch);
    }

    private void writeToBatch(WriteBatch writeBatch, PGSegment pgs) throws RocksDBException{
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
            BaseSegment vs= consolidated.get(i);
            int parameterId = pgs.getParameterId(i);
            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = vsEncoder.encode(vs);
            writeBatch.put(p.dataCfh, engKey, engValue);

            if(STORE_RAW_VALUES && consolidatedRawValues!=null) {
                BaseSegment rvs = consolidatedRawValues.get(i);
                if(rvs!=null) {
                    byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_RAW_VALUE).encode();
                    byte[] rawValue = vsEncoder.encode(rvs);
                    writeBatch.put(p.dataCfh, rawKey, rawValue);

                }
            }
            ParameterStatusSegment pss = satusSegments.get(i);
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
                byte[] cfname = encodePartitionId(CF_NAME_data_prefix, partitionId);
                ColumnFamilyDescriptor cfd = new ColumnFamilyDescriptor(cfname);
                p.dataCfh = rdb.createColumnFamily(cfd);

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
     * @param startPartition
     * @param stopPartition
     * @return
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
    }

    public RocksIterator getIterator(Partition p) {
        return rdb.newIterator(p.dataCfh);
    }

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart,  int parameterGroupId) throws RocksDBException, DecodingException {
        byte[] timeKey = new SegmentKey(ParameterIdDb.TIMESTAMP_PARA_ID, parameterGroupId, segmentStart, SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] tv = rdb.get(p.dataCfh, timeKey);
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
    }

    @Override
    protected void doStop() {
        rdb.close();
        if(backFiller!=null) {
            backFiller.stop();
        }
        notifyStopped();
    }

 
    public void printStats(PrintStream out)  {
        try {
            for(Partition p:partitions.values()) {
                out.println("---------- Partition starting at "+TimeEncoding.toString(p.partitionId)+" -------------");
                out.println(rdb.getProperty(p.dataCfh, "rocksdb.stats"));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }
    public void printKeys(PrintStream out) {
        out.println("pid\t pgid\t type\tSegmentStart\tcount\tsize\tstype");
        SegmentEncoderDecoder decoder = new SegmentEncoderDecoder();
        for(Partition p:partitions.values()) {
            RocksIterator it = getIterator(p);
            it.seekToFirst();
            while(it.isValid()) {
                SegmentKey key = SegmentKey.decode(it.key());
                byte[] v = it.value();
                BaseSegment s;
                try {
                    s = decoder.decode(it.value(), key.segmentStart);
                } catch (DecodingException e) {
                    throw new RuntimeException(e);
                }
                out.println(key.parameterId+"\t "+key.parameterGroupId+"\t "+key.type+"\t"+TimeEncoding.toString(key.segmentStart)+"\t"+s.size()+"\t"+v.length+"\t"+s.getClass().getSimpleName());
                it.next();
            }
        }
    }
}
