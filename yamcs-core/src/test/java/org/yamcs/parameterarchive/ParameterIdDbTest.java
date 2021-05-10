package org.yamcs.parameterarchive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.IntArray;
import org.yamcs.yarch.rocksdb.Tablespace;

public class ParameterIdDbTest {
    Tablespace tablespace;

    @Before
    public void before() throws Exception {
        File f = new File("/tmp/TestParameterIdDb");
        FileUtils.deleteRecursivelyIfExists(f.toPath());

        tablespace = new Tablespace("test1");
        tablespace.setCustomDataDir(f.getAbsolutePath());

        tablespace.loadDb(false);
    }

    @After
    public void after() {
        tablespace.close();
    }

    @Test
    public void test1() throws Exception {

        ParameterIdDb pidDb = new ParameterIdDb("test1", tablespace);
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

        pidDb = new ParameterIdDb("test1", tablespace);
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
        ParameterIdDb pidDb = new ParameterIdDb("test2", tablespace);
        int p1 = pidDb.createAndGet("/test2/bp1", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test2/bp2", Value.Type.BOOLEAN);

        int aggp1 = pidDb.createAndGetAggrray("/test2/aggregate1", IntArray.wrap(p1, p2));

        int p3 = pidDb.createAndGet("/test2/bp3", Value.Type.BOOLEAN);

        int aggp2 = pidDb.createAndGetAggrray("/test2/aggregate1", IntArray.wrap(p3, p1, p2));
        assertEquals(aggp1, aggp2);

        tablespace.close();
        tablespace.loadDb(false);
        pidDb = new ParameterIdDb("test2", tablespace);

        int p4 = pidDb.createAndGet("/test2/bp4", Value.Type.BOOLEAN);
        int aggp3 = pidDb.createAndGetAggrray("/test2/aggregate1", IntArray.wrap(p1, p4));

        assertTrue(aggp1 != aggp3);

        int aggp4 = pidDb.createAndGetAggrray("/test2/aggregate1", IntArray.wrap(p1, p2));
        assertEquals(aggp1, aggp4);
    }

    @Test
    public void test3() throws Exception {
        ParameterIdDb pidDb = new ParameterIdDb("test3", tablespace);
        int n = 1000;
        int[] pid = new int[n];
        for (int i = 0; i < n; i++) {
            pid[i] = pidDb.createAndGet("/test3/bp[" + i + "]", Value.Type.BOOLEAN);
        }

        tablespace.close();
        tablespace.loadDb(false);
        pidDb = new ParameterIdDb("test3", tablespace);

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
        ParameterIdDb pidDb = new ParameterIdDb("test4", tablespace);
        int p1 = pidDb.createAndGet("/test4/p", Value.Type.BOOLEAN);
        int p2 = pidDb.createAndGet("/test4/p", Value.Type.UINT32);
        ParameterId[] pids = pidDb.get("/test4/p");
        assertEquals(2, pids.length);
        assertEquals(p1, pids[0].getPid());
        assertEquals(p2, pids[1].getPid());

    }
}
