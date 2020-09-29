package org.yamcs.xtce;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.yamcs.xtceproc.XtceDbFactory;

public class XtceFilesetTest {
    @Test
    public void testXtceFileset() throws Exception {
        XtceDb db = XtceDbFactory.createInstanceByConfig("xtce-fileset");
        assertNotNull(db.getSpaceSystem("/a1"));
        assertNotNull(db.getSpaceSystem("/a2"));
        assertNotNull(db.getSpaceSystem("/b"));
    }
}
