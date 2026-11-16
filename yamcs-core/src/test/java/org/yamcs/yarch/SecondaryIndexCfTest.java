package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.streamsql.StreamSqlResult;

/**
 * Secondary index on tables stored in a non-default column family (like the tm table)
 */
public class SecondaryIndexCfTest extends YarchTestCase {
    static final String CF_OPTS = " table_format=compressed,column_family:rt_data";

    void populate(String tblName, String partitionBy) throws Exception {
        ydb.execute("create table " + tblName
                + "(gentime timestamp, seq int, rectime timestamp, "
                + "primary key(gentime, seq), index(rectime)) " + partitionBy + CF_OPTS);

        ydb.execute("create stream " + tblName + "_in(gentime timestamp, seq int, rectime timestamp)");
        ydb.execute("insert into " + tblName + " select * from " + tblName + "_in");
        Stream s = ydb.getStream(tblName + "_in");

        long t0 = TimeEncoding.parse("2020-01-15T00:00:00Z");
        for (int i = 0; i < 6; i++) {
            long gentime = t0 + i * 40L * 86400_000L; // spans several months
            for (int j = 0; j < 3; j++) {
                Tuple t = new Tuple(s.getDefinition(), Arrays.asList(gentime, j, (long) (1000 + i)));
                s.emitTuple(t);
            }
        }
        execute("close stream " + tblName + "_in");
    }

    void verify(String tblName) throws Exception {
        StreamSqlResult res = ydb.execute("select * from " + tblName + " where rectime=?", 1002L);
        for (int j = 0; j < 3; j++) {
            assertTrue(res.hasNext());
            Tuple t = res.next();
            assertEquals(1002L, (long) (Long) t.getColumn("rectime"));
            assertEquals(j, (int) (Integer) t.getColumn("seq"));
        }
        assertFalse(res.hasNext());
        res.close();

        res = ydb.execute("select * from " + tblName + " where rectime>=? and rectime<?", 1001L, 1004L);
        int count = 0;
        while (res.hasNext()) {
            res.next();
            count++;
        }
        assertEquals(9, count);
    }

    @Test
    public void testNotPartitioned() throws Exception {
        populate("test_np", "");
        verify("test_np");
    }

    @Test
    public void testPartitioned() throws Exception {
        populate("test_p", "partition by time(gentime('YYYY/MM'))");
        verify("test_p");
    }

    @Test
    public void testDropTable() throws Exception {
        populate("test_drop", "partition by time(gentime('YYYY/MM'))");
        ydb.execute("drop table test_drop");

        ydb.execute("create table test_drop"
                + "(gentime timestamp, seq int, rectime timestamp, "
                + "primary key(gentime, seq), index(rectime)) " + CF_OPTS);
        StreamSqlResult res = ydb.execute("select * from test_drop where rectime=?", 1002L);
        assertFalse(res.hasNext());
        res.close();
    }
}
