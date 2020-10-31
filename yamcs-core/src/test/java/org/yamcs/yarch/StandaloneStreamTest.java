package org.yamcs.yarch;

import org.junit.Test;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;

import static org.junit.Assert.*;

public class StandaloneStreamTest extends YarchTestCase {

    @Test
    public void createStreamTest() throws Exception {
        execute("create stream test1 (packetId int, packet binary)");
        Stream s = ydb.getStream("test1");
        assertNotNull(s);
    }

    @Test(expected = GenericStreamSqlException.class)
    public void testBogusWhere() throws Exception {
        execute("create stream tm_in(gentime timestamp, id int)");
        execute("create stream testbogus as select * from tm_in where id+3");

    }
}
