package org.yamcs.yarch;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runners.Parameterized.Parameters;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public abstract class YarchTestCase {
    protected StreamSqlParser parser;
    protected ExecutionContext context;
    protected YarchDatabaseInstance ydb;
    static boolean littleEndian;
    protected String instance;
    Random random = new Random();

    @BeforeClass
    public static void setUpYarch() throws Exception {
        YConfiguration.setup(); // reset the prefix if maven runs multiple tests
                                // in the same java
        YConfiguration config = YConfiguration.getConfiguration("yamcs");
        if (config.containsKey("littleEndian")) {
            littleEndian = config.getBoolean("littleEndian");
        } else {
            littleEndian = false;
        }
        //org.yamcs.LoggingUtils.enableLogging();
    }

    @Before
    public void setUp() throws Exception {
        YConfiguration config = YConfiguration.getConfiguration("yamcs");
        String dir = config.getString("dataDir");
        instance = "yarchtest_" + this.getClass().getSimpleName();
        context = new ExecutionContext(instance);

        if (YarchDatabase.hasInstance(instance)) {
            YarchDatabase.removeInstance(instance);
            RdbStorageEngine rse = RdbStorageEngine.getInstance();
            if (rse.getTablespace(instance) != null) {
                rse.dropTablespace(instance);
            }
        }

        File ytdir = new File(dir + "/" + instance);
        File rdbdir = new File(dir + "/" + instance + ".rdb");

        FileUtils.deleteRecursively(ytdir.toPath());
        FileUtils.deleteRecursively(rdbdir.toPath());

        if (!ytdir.mkdirs())
            throw new IOException("Cannot create directory " + ytdir);

        ydb = YarchDatabase.getInstance(instance);
    }

   
    protected StreamSqlResult execute(String cmd, Object...args) throws StreamSqlException, ParseException {
        return ydb.execute(cmd, args);
    }

    protected List<Tuple> fetchAllFromTable(String tableName) throws Exception {
        String sname = tableName + "_out_" + random.nextInt(10000);
        ydb.execute("create stream " + sname + " as select * from " + tableName);
        return fetchAll(sname);
    }

    protected List<Tuple> fetchAll(String streamName) throws InterruptedException {
        final List<Tuple> tuples = new ArrayList<Tuple>();
        final Semaphore semaphore = new Semaphore(0);
        Stream s = ydb.getStream(streamName);
        if (s == null)
            throw new IllegalArgumentException("No stream named '" + streamName + "' in instance " + instance);
        s.addSubscriber(new StreamSubscriber() {

            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        s.start();
        semaphore.acquire();
        return tuples;
    }

    @Parameters
    public static List<String> getStorageEngineConfigs() {
        return Arrays.asList("engine rocksdb partition_storage=IN_KEY",
                "engine rocksdb partition_storage=COLUMN_FAMILY", 
                "engine rocksdb2");
    }
    
    protected void assertNumElementsEqual(Iterator<?> iter, int k) {
        int num =0;
        while(iter.hasNext()) {
            num++;
            iter.next();
        }
        assertEquals(k, num);
    }
}
