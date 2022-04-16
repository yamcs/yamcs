package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;

public class StandaloneStreamTest extends YarchTestCase {

    @Test
    public void createStreamTest() throws Exception {
        execute("create stream test1 (packetId int, packet binary)");
        Stream s = ydb.getStream("test1");
        assertNotNull(s);
    }

    @Test
    public void testBogusWhere() throws Exception {
        assertThrows(GenericStreamSqlException.class, () -> {
            execute("create stream tm_in(gentime timestamp, id int)");
            execute("create stream testbogus as select * from tm_in where id+3");
        });
    }
}
