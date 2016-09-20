package org.yamcs.web.rest.mdb;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class NameDescriptionSearchMatcherTest {
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
    }

    @Test
    public void testSearchMatch() throws ConfigurationException {
        XtceDb mdb = XtceDbFactory.createInstance("refmdb");
        assertTrue(match("/REFMDB/CcSdS-APID", mdb));
        assertTrue(match("REFMDB_ccsds-apid", mdb));
        assertTrue(match("ap ReFmDB_CC", mdb));
    }
    
    private boolean match(String searchTerm, XtceDb mdb) {
        NameDescriptionSearchMatcher matcher = new NameDescriptionSearchMatcher(searchTerm);
        for (Parameter p : mdb.getParameters()) {
            if (matcher.matches(p)) {
                System.out.println(p.getQualifiedName());
                return true;
            }
        }
        return false;
    }
}
