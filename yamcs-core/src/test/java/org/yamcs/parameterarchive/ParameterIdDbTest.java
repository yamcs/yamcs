package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.Tablespace;

public class ParameterIdDbTest {
    Tablespace tablespace;

    @BeforeEach
    public void before() throws Exception {
        Path f = Path.of(System.getProperty("java.io.tmpdir"), "TestParameterIdDb");
        FileUtils.deleteRecursivelyIfExists(f);

        tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.toString());

        tablespace.loadDb(false);
    }

    @AfterEach
    public void after() {
        tablespace.close();
    }

    ParameterIdDb pdb(String name) throws RocksDBException, IOException {
        return new ParameterIdDb(name, tablespace, false, 0);
    }

    @Test
    public void test1() throws Exception {

        ParameterIdDb pidDb = pdb("test1");
        assertEquals(0, pidDb.size());

        int p1 = pidDb.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        assertEquals(p1, p2);

        int p3 = pidDb.createAndGet("/test1/bla", Value.Type.DOUBLE);
        assertTrue(p3 > p1);
        int p10 = pidDb.createAndGet("/test1/bla", Value.Type.DOUBLE, Value.Type.SINT32);
        assertTrue(p10 > p3);

        tablespace.close();
        tablespace.loadDb(false);

        pidDb = pdb("test1");
        int p4 = pidDb.createAndGet("/test1/bla", Value.Type.BOOLEAN);
        assertEquals(p1, p4);
        int p5 = pidDb.createAndGet("/test1/bla", Value.Type.DOUBLE);
        assertEquals(p3, p5);

        int p6 = pidDb.createAndGet("/test2/bla", Value.Type.DOUBLE);
        assertTrue(p6 > p3);

        int p11 = pidDb.createAndGet("/test1/bla", Value.Type.DOUBLE, Value.Type.SINT32);
        assertEquals(p10, p11);

    }

    @Test
    public void test2() throws Exception {
        Value.Type vt = Value.Type.AGGREGATE;
        ParameterIdDb pidDb = pdb("test2");
        int p1 = pidDb.createAndGet("/test2/bp1", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test2/bp2", Value.Type.BOOLEAN);

        int aggp1 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p1, p2));

        int p3 = pidDb.createAndGet("/test2/bp3", Value.Type.BOOLEAN);

        int aggp2 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p3, p1, p2));
        assertEquals(aggp1, aggp2);

        tablespace.close();
        tablespace.loadDb(false);
        pidDb = pdb("test2");

        int p4 = pidDb.createAndGet("/test2/bp4", Value.Type.BOOLEAN);
        int aggp3 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p1, p4));

        assertTrue(aggp1 != aggp3);

        int aggp4 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p1, p2));
        assertEquals(aggp1, aggp4);
    }

    @Test
    public void test3() throws Exception {
        ParameterIdDb pidDb = pdb("test3");
        int n = 1000;
        int[] pid = new int[n];
        for (int i = 0; i < n; i++) {
            pid[i] = pidDb.createAndGet("/test3/bp[" + i + "]", Value.Type.BOOLEAN);
        }

        tablespace.close();
        tablespace.loadDb(false);
        pidDb = pdb("test3");

        for (int i = 0; i < n; i++) {
            String fqn = "/test3/bp[" + i + "]";
            int p = pidDb.createAndGet(fqn, Value.Type.BOOLEAN);
            assertEquals(pid[i], p);
            ParameterId[] pids = pidDb.get(fqn);

            assertEquals(1, pids.length);
            assertEquals(p, pids[0].getPid());
            assertEquals(fqn, pids[0].getParamFqn());

            assertEquals(p, pidDb.getParameterId(p).getPid());
        }
    }

    @Test
    public void test4() throws Exception {
        ParameterIdDb pidDb = pdb("test4");
        int p1 = pidDb.createAndGet("/test4/p", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test4/p", Value.Type.UINT32);
        ParameterId[] pids = pidDb.get("/test4/p");
        assertEquals(2, pids.length);
        assertEquals(p1, pids[0].getPid());
        assertEquals(p2, pids[1].getPid());
    }

    @Test
    public void testSimpleToAggregate() throws Exception {
        Value.Type vt = Value.Type.AGGREGATE;
        ParameterIdDb pidDb = pdb("testSimpleToAggregate");

        // first /test2/aggregate1 is a simple parameter
        int p0 = pidDb.createAndGet("/test2/aggregate1", Value.Type.SINT32);

        // but then it becomes an aggregate with two members
        int p1 = pidDb.createAndGet("/test2/aggregate1.bp1", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test2/aggregate1.bp2", Value.Type.BOOLEAN);
        int aggp1 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p1, p2));

        tablespace.close();
        tablespace.loadDb(false);
        pidDb = pdb("testSimpleToAggregate");

        int p0_1 = pidDb.createAndGet("/test2/aggregate1", Value.Type.SINT32);
        assertEquals(p0, p0_1);

        int aggp3 = pidDb.createAndGetAggrray("/test2/aggregate1", vt, vt, IntArray.wrap(p1, p2));
        assertEquals(aggp1, aggp3);
    }
}
