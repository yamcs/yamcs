package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

public class NameDescriptionSearchMatcherTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
    }

    @Test
    public void testSearchMatch() throws ConfigurationException {
        Mdb mdb = MdbFactory.createInstanceByConfig("refmdb");
        assertTrue(match("/REFMDB/CcSdS-APID", mdb));
        assertTrue(match("REFMDB_ccsds-apid", mdb));
        assertTrue(match("ap ReFmDB_CC", mdb));
    }

    private boolean match(String searchTerm, Mdb mdb) {
        NameDescriptionSearchMatcher matcher = new NameDescriptionSearchMatcher(searchTerm);
        for (Parameter p : mdb.getParameters()) {
            if (matcher.matches(p)) {
                return true;
            }
        }
        return false;
    }
}
