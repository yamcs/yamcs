package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class UUIDTest extends YarchTestCase {

    int n = 10;

    private void populate(String tblName) throws Exception {
        execute("create table " + tblName
                + "(start timestamp, stop timestamp, id UUID, data string, primary key(stop, start, id))");
        execute("create stream " + tblName + "_in(start timestamp, stop timestamp, id UUID, data string)");
        execute("upsert into " + tblName + " select * from " + tblName + "_in");
        Stream s = ydb.getStream(tblName + "_in");
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(i + 0l, i + 5l, id, id.toString())));
        }
    }

    @Test
    public void test1() throws Exception {
        populate("test1");
        List<Tuple> tlist = fetchAllFromTable("test1");
        assertEquals(n, tlist.size());
        for (int i = 0; i < n; i++) {
            Tuple t = tlist.get(i);
            long start = (Long) t.getColumn("start");
            assertEquals(i, start);

            UUID id = (UUID) t.getColumn("id");
            String data = (String) t.getColumn("data");
            assertEquals(data, id.toString());
        }
    }

    @Test
    public void testUuidArray() throws Exception {
        String tblName = "test2";
        execute("create table " + tblName
                + "(id int, uuidarray uuid[], primary key(id))");
        execute("create stream " + tblName + "_in(id int, uuidarray uuid[])");
        execute("upsert into " + tblName + " select * from " + tblName + "_in");
        Stream s = ydb.getStream(tblName + "_in");
        List<UUID> idlist = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());

        s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(1, idlist)));

        StreamSqlResult res = ydb.execute("select * from test2");
        assertTrue(res.hasNext());
        Tuple t = res.next();
        assertEquals(1, t.getIntColumn("id"));
        assertEquals(idlist, t.getColumn("uuidarray"));
        res.close();
    }

    @Test
    public void testSelectWithNull() throws Exception {
        execute("create table test3 (id1 int, id2 uuid, primary key(id1))");
        execute("create stream test3_in(id1 int, id2 uuid)");
        execute("upsert into test3 select * from test3_in");
        Stream s = ydb.getStream("test3_in");
        UUID uuid = UUID.randomUUID();

        s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(1, uuid)));
        Tuple twithoutid2 = new Tuple();
        twithoutid2.addColumn("id1", 2);
        s.emitTuple(twithoutid2);

        StreamSqlResult res = ydb.execute("select * from test3 where id2 = ?", uuid);
        assertTrue(res.hasNext());
        Tuple t1 = res.next();
        assertEquals(uuid, t1.getColumn("id2"));
        assertFalse(res.hasNext());
        res.close();
    }
}
