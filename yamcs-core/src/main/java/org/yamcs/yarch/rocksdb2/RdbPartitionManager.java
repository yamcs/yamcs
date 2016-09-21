package org.yamcs.yarch.rocksdb2;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.TimePartitionSchema.PartitionInfo;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of table partitions in rocksdb storage. 
 * Types of partitions that are supported:
 *  time and time,value     - creates directories of shape yyyy/ddd/tblname
 *  value                   - creates directories of shape tblname
 * @author nm
 */
public class RdbPartitionManager extends PartitionManager {

    final YarchDatabase ydb;
    static Logger log=LoggerFactory.getLogger(RdbPartitionManager.class.getName());

    public RdbPartitionManager(YarchDatabase ydb, TableDefinition tableDefinition) {
        super(tableDefinition);
        this.ydb = ydb;
    }

    /** 
     * Called at startup to read existing partitions from disk
     */
    public void readPartitionsFromDisk() {
        readDir("");
    }

    private void readDir(String dir) {
        String tblName = tableDefinition.getName();
        String dataDir = tableDefinition.getDataDir();
        String[] files=new File(dataDir+"/"+dir).list();
        for(String s:files) {
            File f = new File(dataDir +"/"+dir+"/"+s);

            if(!f.isDirectory()) continue;
            if(s.equals(tblName)) {
                File currentf = new File(dataDir+"/"+dir+"/"+s+"/CURRENT");
                if(currentf.exists()) {
                    PartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().parseDir(dir);
                    try {
                        readDb(partitioningSpec, pinfo, dir);
                    } catch (Exception e) {
                        log.error("cannot open database partition for table "+tableDefinition.getName()+" at '"+dir, e );
                        continue;
                    }
                }	
            }
            if(dir.isEmpty()) {
                readDir(s);
            } else {
                readDir(dir+"/"+s);
            }
        }
    }

    /** 
     * Called at startup to read existing partitions from disk
     * @param partitioningSpec 
     */
    private void readDb(PartitioningSpec partitioningSpec, PartitionInfo pinfo, String dir) throws RocksDBException, IOException {
        log.trace("reading partition from {} pinfo: {}", dir, pinfo);
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        String tblName = tableDefinition.getName();
        String dataDir = tableDefinition.getDataDir();
        String absolutePath = dir.isEmpty()?dataDir+"/"+tblName:dataDir+"/"+dir+"/"+tblName;
        YRDB rdb = rdbFactory.getRdb(absolutePath, false);

        if((partitioningSpec.type==PartitioningSpec._type.TIME_AND_VALUE) ||  (partitioningSpec.type==PartitioningSpec._type.VALUE)) {
            int size = ColumnValueSerializer.getSerializedSize(partitioningSpec.getValueColumnType());
            ColumnValueSerializer cvs = new ColumnValueSerializer(partitioningSpec.getValueColumnType());
            List<byte[]> l = rdb.scanPartitions(size); 
            
            for(byte[] b: l) {
                Object value = cvs.byteArrayToObject(b);
                if(pinfo!=null) {
                    addPartitionByTimeAndValue(pinfo, value);
                } else {
                    addPartitionByValue(value);
                }
            }
        } else if(partitioningSpec.type==PartitioningSpec._type.TIME) {
            addPartitionByTime(pinfo);
        } else {
            addPartitionByNone();
        }
    }


    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTime(PartitionInfo pinfo) {               
        Interval intv = intervals.get(pinfo.partitionStart);      

        if(intv==null) {            
            intv=new Interval(pinfo.partitionStart, pinfo.partitionEnd);
            intervals.put(pinfo.partitionStart, intv);
        }
        Partition p = new RdbPartition(pinfo.partitionStart, pinfo.partitionEnd, null, null, pinfo.dir+"/"+tableDefinition.getName());
        intv.addTimePartition(p);
    }   

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTimeAndValue(PartitionInfo pinfo, Object v) {	   	   
        Interval intv = intervals.get(pinfo.partitionStart);	  

        if(intv==null) {	    
            intv = new Interval(pinfo.partitionStart, pinfo.partitionEnd);
            intervals.put(pinfo.partitionStart, intv);
        }
        ColumnValueSerializer cvs=   new ColumnValueSerializer(tableDefinition);
        Partition p=new RdbPartition(pinfo.partitionStart, pinfo.partitionEnd, v, cvs.objectToByteArray(v), pinfo.dir+"/"+tableDefinition.getName());
        intv.add(v, p);
    }	

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByValue(Object v) {
        ColumnValueSerializer cvs=   new ColumnValueSerializer(tableDefinition);
        Partition p = new RdbPartition(Long.MIN_VALUE, Long.MAX_VALUE, v, cvs.objectToByteArray(v), tableDefinition.getName());             
        pcache.add(v, p);
    }   

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByNone() {             
        Partition p = new RdbPartition(Long.MIN_VALUE, Long.MAX_VALUE, null, null, tableDefinition.getName());             
        pcache.add(null, p);
    }   

    @Override
    protected Partition createPartition(PartitionInfo pinfo, Object value) throws IOException {
        String tblName = tableDefinition.getName();
        String dataDir = tableDefinition.getDataDir();
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        File f= new File(dataDir+"/"+pinfo.dir+"/"+tblName);

        if(!f.exists()) {
            f.mkdirs();
        }
        ColumnValueSerializer cvs=   new ColumnValueSerializer(tableDefinition);
        byte[] bvalue = cvs.objectToByteArray(value);

        YRDB rdb = rdbFactory.getRdb(f.getAbsolutePath(), true);

        rdbFactory.dispose(rdb);
        return new RdbPartition(pinfo.partitionStart, pinfo.partitionEnd, value, bvalue, pinfo.dir+"/"+tableDefinition.getName());			
    }

    @Override
    protected Partition createPartition(Object value) throws IOException {
        String tblName = tableDefinition.getName();
        String dataDir = tableDefinition.getDataDir();
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());

        File f= new File(dataDir+"/"+tblName);

        if(!f.exists()) {
            f.mkdirs();
        }
        ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
        YRDB rdb = rdbFactory.getRdb(f.getAbsolutePath(), true);

        rdbFactory.dispose(rdb);
        return new RdbPartition(Long.MIN_VALUE, Long.MAX_VALUE, value, cvs.objectToByteArray(value), tableDefinition.getName());			
    }
}

class Interval {
    long start,  end;
    String dir;
    Set<Object> values=Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());

    public Interval(long start, long end) {
        this.start=start;
        this.end=end;
    }

    @Override
    public String toString() {
        return "["+TimeEncoding.toString(start)+" - "+TimeEncoding.toString(end)+"] dir:"+dir+" values: "+values;
    }
}
