package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests a table structured like the non partitioned tm table: the enum column is part of the primary key instead of
 * being used for partitioning.
 */
public class EnumInKeyTest extends YarchTestCase {

    private void populate(String tblname) throws Exception {
        execute("create table " + tblname
                + "(gentime timestamp, seqNum int, packet binary, pname enum, primary key(gentime, seqNum, pname))"
                + " histogram(pname)");
        execute("create stream " + tblname + "_in(gentime timestamp, seqNum int, packet binary, pname enum)");
        execute("insert into " + tblname + " select * from " + tblname + "_in");

        Stream s = ydb.getStream(tblname + "_in");
        TupleDefinition td = s.getDefinition();
        // same gentime and seqNum, different pname
        s.emitTuple(new Tuple(td, new Object[] { 1000L, 1, new byte[] { 1 }, "pn1" }));
        s.emitTuple(new Tuple(td, new Object[] { 1000L, 1, new byte[] { 2 }, "pn2" }));
        // duplicate of the first one: should not be stored
        s.emitTuple(new Tuple(td, new Object[] { 1000L, 1, new byte[] { 3 }, "pn1" }));
        s.emitTuple(new Tuple(td, new Object[] { 2000L, 2, new byte[] { 4 }, "pn1" }));
        s.emitTuple(new Tuple(td, new Object[] { 3000L, 3, new byte[] { 5 }, "pn2" }));
        execute("close stream " + tblname + "_in");
    }

    @Test
    public void testUniqueness() throws Exception {
        populate("tm1");
        List<Tuple> tuples = fetchAllFromTable("tm1");
        assertEquals(4, tuples.size());

        assertEquals(1000L, tuples.get(0).getLongColumn("gentime"));
        assertEquals("pn1", tuples.get(0).getColumn("pname"));
        assertEquals(1, ((byte[]) tuples.get(0).getColumn("packet"))[0]);

        assertEquals(1000L, tuples.get(1).getLongColumn("gentime"));
        assertEquals("pn2", tuples.get(1).getColumn("pname"));

        assertEquals(2000L, tuples.get(2).getLongColumn("gentime"));
        assertEquals(3000L, tuples.get(3).getLongColumn("gentime"));
    }

    @Test
    public void testFilterOnPname() throws Exception {
        populate("tm2");
        ydb.execute("create stream tm2_out as select * from tm2 where pname in ('pn2')");
        List<Tuple> tuples = fetchAll("tm2_out");
        assertEquals(2, tuples.size());
        assertEquals(1000L, tuples.get(0).getLongColumn("gentime"));
        assertEquals(3000L, tuples.get(1).getLongColumn("gentime"));
        for (Tuple t : tuples) {
            assertEquals("pn2", t.getColumn("pname"));
        }
    }

    @Test
    public void testFilterOnPnameEqualDesc() throws Exception {
        populate("tm3");
        ydb.execute("create stream tm3_out as select * from tm3 where pname = 'pn1' order desc");
        List<Tuple> tuples = fetchAll("tm3_out");
        assertEquals(2, tuples.size());
        assertEquals(2000L, tuples.get(0).getLongColumn("gentime"));
        assertEquals(1000L, tuples.get(1).getLongColumn("gentime"));
    }

    @Test
    public void testHistogram() throws Exception {
        populate("tm4");
        ydb.execute("create stream tm4_out as select * from tm4 histogram(pname)");
        List<Tuple> tuples = fetchAll("tm4_out");
        Map<String, Integer> counts = new HashMap<>();
        for (Tuple t : tuples) {
            counts.merge((String) t.getColumn(0), (Integer) t.getColumn(3), Integer::sum);
        }
        // the duplicate is not counted
        assertEquals(Map.of("pn1", 2, "pn2", 2), counts);
    }
}
