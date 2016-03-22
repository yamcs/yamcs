package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import com.sun.management.UnixOperatingSystemMXBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.io.Files;

public class RdbPerformanceTest extends YarchTestCase {
    private TupleDefinition tdef;
    private TableWriter tw;
    int numPacketType = 20;
    int[] freq  = new int[] {
            1,     1,     1,     1,
            10,    10,    10,    10,
            300,   300,   300,   300, 
            3600,  3600,  3600,  3600,
            86400, 86400, 86400, 86400
    };
    String dir = "/storage/ptest";

    void populate(TableDefinition tblDef, int n, boolean timeFirst) throws Exception {        
        RdbStorageEngine rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        tw = rse.newTableWriter(tblDef, InsertMode.INSERT);

        long baseTime = TimeEncoding.parse("2015-01-01T00:00:00");

        ThreadLocalRandom r = ThreadLocalRandom.current();
        byte[] b = new byte[256];
        int numPackets=0;

        long t0 = System.currentTimeMillis();
        for(int i=0;i<n;i++) {
            for(int j=0; j<freq.length; j++) {
                if(i%freq[j]==0) {
                    r.nextBytes(b);
                    numPackets++;
                    Tuple t;
                    if(timeFirst) {
                        t = new Tuple(tdef, new Object[]{baseTime+i*1000L+j, "packet"+j, b});
                    } else {
                        t = new Tuple(tdef, new Object[]{ "packet"+j, baseTime+i*1000L+j, b});
                    }
                    tw.onTuple(null, t);
                }
            }
        }
        System.out.println("total numPackets: "+numPackets);
        long t1 = System.currentTimeMillis();
        System.out.println("time to populate "+((t1-t0)/1000)+" seconds");
    }
    

    void read(String tblName, String packetName) throws Exception {
        long t0 = System.currentTimeMillis();
        String q = "create stream s as select * from "+tblName;
        if(packetName!=null) {
            q =  q +" where pname='"+packetName+"'";
        }
        ydb.execute(q);
        Stream s = ydb.getStream("s");

        Semaphore semaphore = new Semaphore(0);
        AtomicInteger r = new AtomicInteger();
        s.addSubscriber(new StreamSubscriber() {
            int c =0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if(packetName!=null) {
                    assertEquals(packetName, tuple.getColumn("pname"));
                }
                c++;
            }

            @Override
            public void streamClosed(Stream stream) {
                r.set(c);
                semaphore.release();
            }
        });
        s.start();
        semaphore.acquire(); 

        long t1 = System.currentTimeMillis();
        System.out.println("time to read "+r.get()+" tuples with "+packetName+": "+(t1-t0)+" miliseconds");
    }

    
    void populateAndRead(TableDefinition tbldef, boolean timeFirst) throws Exception {
        String tblname = tbldef.getName();
        System.out.println("********************** "+tblname+" timeFirst:" +timeFirst+" **********************");
        
        //populate(tblDef, 365*24*60*60);
        populate(tbldef, 10*24*60*60, timeFirst);
       // Thread.sleep(1000);
        
        // populate(tblDef, 100);
        read(tblname, null);
        read(tblname, "packet1");
        read(tblname, "packet5");
        read(tblname, "packet9");
        read(tblname, "packet14");
        read(tblname, "packet19");
        read(tblname, null);
    }

    @Test
    public void testPartition() throws Exception {
        tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition("part_YYYY_MM_pname", tdef, Arrays.asList("gentime"));
       
        tblDef.setDataDir(dir);

        PartitioningSpec pspec = PartitioningSpec.timeAndValueSpec("gentime", "pname");
        pspec.setValueColumnType(DataType.ENUM);
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);
        populateAndRead(tblDef, true);
    }


    @Test
    public void testNoPnameYYYY() throws Exception {
        String tblname = "NoPname_YYYY";
        tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition(tblname, tdef, Arrays.asList("gentime"));

        tblDef.setDataDir(dir);

        PartitioningSpec pspec = PartitioningSpec.timeSpec("gentime");
        pspec.setTimePartitioningSchema("YYYY");
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);

        populateAndRead(tblDef, true);
    }

    
    @Test
    public void testNonePartition() throws Exception {
        String tblname = "NonePartition";
        tdef = new TupleDefinition();
        
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));        
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition(tblname, tdef, Arrays.asList("pname", "gentime"));

        tblDef.setDataDir(dir);

        PartitioningSpec pspec = PartitioningSpec.noneSpec();
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);
        
        populateAndRead(tblDef, false);
    }

    
    @SuppressWarnings("restriction")
    @Test
    public void testNumOpenFiles() throws Exception {
        String dir = "/storage/ptest/NonePartition";

        List<byte[]> cfl = RocksDB.listColumnFamilies(new Options(), dir);
        
        List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());
        ColumnFamilyOptions cfoptions = new ColumnFamilyOptions();
        cfoptions.setTargetFileSizeMultiplier(10);
        
        DBOptions dboptions = new DBOptions();
        dboptions.setMaxOpenFiles(22);
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if(os instanceof UnixOperatingSystemMXBean) {
            System.out.println("Before DB open, number of open fd: " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
        for(byte[] b: cfl) {
            cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));                                 
        }
        
        List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
        RocksDB db = RocksDB.open(dboptions, dir, cfdList, cfhList);
        if(os instanceof UnixOperatingSystemMXBean){
            System.out.println("after opening the db, number of open fd: " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
        RocksIterator[] its = new RocksIterator[30];
        int n = 100000;
        int c=0;
        for (int k=0;k<its.length; k++) {
         ///   System.out.println("opening iterator "+k);
            its[k] = db.newIterator();
            if(k==0) {
                its[k].seekToFirst();
            } else {
                its[k].seek(its[k-1].key());
            }
            while(c<k*n) {
                c++;
                its[k].next();
            }
        }
       
        
        if(os instanceof UnixOperatingSystemMXBean){
            System.out.println("after opening the iterators, number of open fd: " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
        for (int k=0;k<its.length; k++) {
            its[k].dispose();
        }
        if(os instanceof UnixOperatingSystemMXBean){
            System.out.println("after closing the iterators, number of open fd: " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
        db.close();
        if(os instanceof UnixOperatingSystemMXBean){
            System.out.println("after closing the db, number of open fd: " + ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
        }
    }


}
