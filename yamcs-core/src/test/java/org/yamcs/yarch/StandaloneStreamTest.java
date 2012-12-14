package org.yamcs.yarch;

import org.junit.Test;
import org.yamcs.yarch.Stream;

import static org.junit.Assert.*;

public class StandaloneStreamTest extends YarchTestCase {
    
    @Test
    public void createStreamTest() throws Exception {
        execute("create stream test1 (packetId int, packet binary)");
        Stream s=ydb.getStream("test1");
        assertNotNull(s);
    }
}
