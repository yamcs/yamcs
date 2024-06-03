package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class XtceFilesetTest {
    @Test
    public void testXtceFileset() throws Exception {
        Mdb mdb = MdbFactory.createInstanceByConfig("xtce-fileset");
        assertNotNull(mdb.getSpaceSystem("/a1"));
        assertNotNull(mdb.getSpaceSystem("/a2"));
        assertNotNull(mdb.getSpaceSystem("/b"));
    }
}
