package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class XtceXIncludeTest {

    @Test
    public void testXInclude() throws Exception {
        Mdb mdb = MdbFactory.createInstanceByConfig("xtce-xinclude");
        assertNotNull(mdb.getSpaceSystem("/xinclude-test"));
        assertNotNull(mdb.getSpaceSystem("/xinclude-test/subsys1"));
        assertNotNull(mdb.getParameter("/xinclude-test/subsys1/param1"));
    }
}
