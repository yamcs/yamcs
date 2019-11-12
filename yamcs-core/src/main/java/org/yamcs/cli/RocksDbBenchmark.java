package org.yamcs.cli;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Benchmark rocksdb storage engine. The benchmark consists of a table load and a few selects.\n"
        + "The table is loaded with telemetry packets received at frequencies of [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour].\n"
        + "The table will be identical to the tm table and will contain a histogram on pname (=packet name).\n"
        + "It is possible to specify how many partitions (i.e. how many different pnames) to be loaded for each frequency\n"
        + "and the time duration of the data." + "")
class RocksDbBenchmark extends Command {
    @Parameter(names = "--dbDir", description = "the directory where the database will be created.\n"
            + "A \"rocksbench\" archive instance will be created in this directory", required = true)
    String dbDir;

    @Parameter(names = "--count", description = "The partition counts for the 5 frequencies: [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour].\n"
            + "It has to be specified as a string (use quotes).\n"
            + "By default, it is \"5 5 5 5 5\"", required = false)
    String counts = "5 5 5 5 5";

    @Parameter(names = "--duration", description = "The duration in hours of the simulated data. By default it's 24 hours", required = false)
    int durationHours = 24;

    @Parameter(names = "--baseTime", description = "Start inserting data with this time. By default it's 2017-01-01T00:00:00", required = false)
    String baseTime = "2017-01-01T00:00:00";

    // frequencies in 100ms
    private long freq[] = { 1, 10, 100, 600, 36000 };

    private int count[];

    private String tableName = "tm";

    private YarchDatabaseInstance ydb;

    public RocksDbBenchmark(RocksDbCli rocksDbCli) {
        super("bench", rocksDbCli);
    }

    @Override
    void validate() {
        String[] a = counts.split("\\s+");
        if (a.length != freq.length) {
            throw new ParameterException(
                    "Invalid count specified; please provide " + freq.length + " numbers (e.g. \"1 2 3 4 5\"");
        }
        count = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            try {
                count[i] = Integer.valueOf(a[i]);
            } catch (NumberFormatException e) {
                throw new ParameterException("Cannot parse '" + a[i] + "' to integer.");
            }
        }
    }

    @Override
    public void execute() throws Exception {
        YarchDatabase.setHome(dbDir);
        this.ydb = YarchDatabase.getInstance("rocksbench");
        TableDefinition tblDef = ydb.getTable(tableName);
        if (tblDef == null) {
            TupleDefinition tdef = XtceTmRecorder.RECORDED_TM_TUPLE_DEFINITION;
            tblDef = new TableDefinition(tableName, tdef,
                    Arrays.asList(StandardTupleDefinitions.GENTIME_COLUMN,
                            StandardTupleDefinitions.SEQNUM_COLUMN));
            tblDef.setHistogramColumns(Arrays.asList(XtceTmRecorder.PNAME_COLUMN));

            PartitioningSpec pspec = PartitioningSpec.valueSpec(XtceTmRecorder.PNAME_COLUMN);
            pspec.setValueColumnType(DataType.ENUM);
            tblDef.setPartitioningSpec(pspec);

            tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

            ydb.createTable(tblDef);
        } else {
            console.println("Table " + tableName + " already exists!. Old data will not be overwritten.");
        }
        populate(tblDef, durationHours * 36000l);

        console.println("*********************** reading data ********************");

        read(tableName, null, -1);

        for (int j = 0; j < freq.length; j++) {
            if (count[j] == 0) {
                continue;
            }
            read(tableName, "/rocksbench/packet_" + j + "_0", freq[j]);
        }

        read(tableName, null, -1);
    }

    void populate(TableDefinition tblDef, long duration100ms) throws Exception {
        RdbStorageEngine rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);

        long baseTime = TimeEncoding.parse("2017-01-01T00:00:00");
        console.println("writing " + durationHours + " hours of data starting with " + TimeEncoding.toString(baseTime));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        byte[] b = new byte[256];
        int numPackets = 0;
        TupleDefinition tdef = tblDef.getTupleDefinition();

        long t0 = System.currentTimeMillis();
        long genTime = baseTime;
        for (long i = 0; i < duration100ms; i++) {
            for (int j = 0; j < freq.length; j++) {
                if (i % freq[j] == 0) {
                    for (int k = 0; k < count[j]; k++) {
                        r.nextBytes(b);
                        numPackets++;

                        Tuple t;
                        int seqNum = (int) i;
                        genTime = baseTime + i * 100L + j;
                        long recTime = TimeEncoding.getWallclockTime();
                        t = new Tuple(tdef,
                                new Object[] { genTime, seqNum, recTime, b, "/rocksbench/packet_" + j + "_" + k });
                        tw.onTuple(null, t);
                        if (numPackets % 1000000 == 0) {
                            console.println(String.format("%3dM packets written; %d%% completed", numPackets / 1000000,
                                    i * 100 / duration100ms));
                        }
                    }
                }
            }
        }
        console.println("write finished; last packet time: " + TimeEncoding.toString(genTime) + "; total numPackets: "
                + numPackets);
        long t1 = System.currentTimeMillis();
        long d = t1 - t0;
        console.println(
                "time to populate " + (d / 1000.0) + " seconds; speed: " + (numPackets * 1000l / d) + " packets/sec");
    }

    void read(String tblName, String packetName, long rate100ms) throws Exception {
        long t0 = System.currentTimeMillis();
        String q = "create stream s as select * from " + tblName;
        if (packetName != null) {
            q = q + " where pname='" + packetName + "'";
        }
        ydb.execute(q);
        Stream s = ydb.getStream("s");

        Semaphore semaphore = new Semaphore(0);
        AtomicInteger count = new AtomicInteger();
        s.addSubscriber(new StreamSubscriber() {
            int c = 0;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (packetName != null) {
                    if (!packetName.equals(tuple.getColumn("pname"))) {
                        throw new RuntimeException("invalid tuple received");
                    }
                }
                c++;
            }

            @Override
            public void streamClosed(Stream stream) {
                count.set(c);
                semaphore.release();
            }
        });
        s.start();
        semaphore.acquire();

        long t1 = System.currentTimeMillis();
        long d = t1 - t0;
        long speed = 1000 * count.get() / d;
        if (packetName == null) {
            console.println(String.format("time to read all %d packets: %.3f seconds, speed: %d packets/second",
                    count.get(), d / 1000.0, speed));
        } else {
            console.println(String.format(
                    "time to read %8d %s (pkt rate: %.2f sec) packets: %.3f seconds, speed: %d packets/second",
                    count.get(), packetName, rate100ms / 10.0, d / 1000.0, speed));
        }
    }
}
