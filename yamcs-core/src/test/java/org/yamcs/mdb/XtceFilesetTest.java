package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.XtceDb;

public class XtceFilesetTest {
    @Test
    public void testXtceFileset() throws Exception {
        XtceDb db = MdbFactory.createInstanceByConfig("xtce-fileset");
        assertNotNull(db.getSpaceSystem("/a1"));
        assertNotNull(db.getSpaceSystem("/a2"));
        assertNotNull(db.getSpaceSystem("/b"));
    }
}
