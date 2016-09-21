package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
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
@Ignore
public class RdbSelectPerfTest extends YarchTestCase {
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
        populate(tbldef, 90*24*60*60, timeFirst);
        // populate(tblDef, 100);
        read(tblname, null);
        read(tblname, "packet1");
        read(tblname, "packet5");
        read(tblname, "packet9");
        read(tblname, "packet14");
        read(tblname, "packet19");
    }

    @Test
    public void testPartition() throws Exception {
        tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition("part_YYYY_pname", tdef, Arrays.asList("gentime"));
       
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

    
    
    @Test
    public void test() throws Exception {
        String dir = "/storage/ptest/2015/NoPnameYYYY";

        List<byte[]> cfl = RocksDB.listColumnFamilies(new Options(), dir);
        
        List<ColumnFamilyDescriptor> cfdList = new ArrayList<ColumnFamilyDescriptor>(cfl.size());
        ColumnFamilyOptions cfoptions = new ColumnFamilyOptions();
        cfoptions.setTargetFileSizeMultiplier(10);
       
        for(byte[] b: cfl) {
            cfdList.add(new ColumnFamilyDescriptor(b, cfoptions));                                 
        }
        
        List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>(cfl.size());
        RocksDB db = RocksDB.open(dir, cfdList, cfhList);
        String s = db.getProperty(cfhList.get(1), "rocksdb.stats");
        System.out.println(s);
        Thread.sleep(100000);
        db.close();
    }


}

/* results
writeBufferSize: 50240        #in KB

HW config: Intel NUC
Intel(R) Core(TM) i7-5557U CPU @ 3.10GHz 4 cores
Disk: Crucial_CT240M500SSD1

 ********************** NonePartition timeFirst:false **********************
total numPackets: 34327080
time to populate 567 seconds
time to read 23937270 tuples with null: 33557 miliseconds
time to read 7776000 tuples with packet1: 10678 miliseconds
time to read 777600 tuples with packet5: 1055 miliseconds
time to read 25920 tuples with packet9: 46 miliseconds
time to read 2160 tuples with packet14: 4 miliseconds
time to read 90 tuples with packet19: 1 miliseconds

ls -ltrh NonePartition/|wc -l
142
ls -ltrh NonePartition/*sst|wc -l
134
du -h NonePartition/
9,2G    NonePartition/


********************** part_YYYY_pname timeFirst:true **********************
total numPackets: 34327080
time to populate 245 seconds
time to read 34327080 tuples with null: 127798 miliseconds
time to read 7776000 tuples with packet1: 11268 miliseconds
time to read 777600 tuples with packet5: 1006 miliseconds
time to read 25920 tuples with packet9: 34 miliseconds
time to read 2160 tuples with packet14: 4 miliseconds
time to read 90 tuples with packet19: 1 miliseconds

ls -lh 2015/part_YYYY_pname/|wc -l
261
mache@sancho:/storage/ptest$ ls -lh 2015/part_YYYY_pname/*sst|wc -l
216
du -h 2015/part_YYYY_pname/
11G     2015/part_YYYY_pname/

 
 */ 
