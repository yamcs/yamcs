package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.management.ManagementService;
import org.yamcs.xtceproc.XtceDbFactory;

public class SpreadsheetLoaderTest {

    @Test
    public void testParameterAliases() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();

        XtceDb db = XtceDbFactory.getInstance("refmdb");

        Parameter p = db.getParameter("/REFMDB/SUBSYS1/IntegerPara1_1");
        assertNotNull(p);
        String aliasPathname = p.getAlias("MDB:Pathname");
        assertEquals("/ccsds-default/PKT1/IntegerPara1_1", aliasPathname);

        String aliasParam = p.getAlias("MDB:AliasParam");
        assertEquals("AliasParam1", aliasParam);

    }

    @Test
    public void testCommandAliases() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();

        XtceDb db = XtceDbFactory.getInstance("refmdb");

        MetaCommand cmd1 = db.getMetaCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC");
        assertNotNull(cmd1);
        String alias = cmd1.getAlias("MDB:Alias1");
        assertEquals("AlternativeName1", alias);

        MetaCommand cmd2 = db.getMetaCommand("/REFMDB/SUBSYS1/FIXED_VALUE_TC");
        assertNotNull(cmd1);
        alias = cmd2.getAlias("MDB:Alias1");
        assertEquals("AlternativeName2", alias);

    }
    
    @Test
    public void testCommandVerifiers() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();

        XtceDb db = XtceDbFactory.getInstance("refmdb");

        MetaCommand cmd1 = db.getMetaCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC");
        assertNotNull(cmd1);
        assertTrue(cmd1.hasCommandVerifiers());
        List<CommandVerifier> verifiers = cmd1.getCommandVerifiers();
        assertEquals(2, verifiers.size());
    }
    
    @Test
    public void testAlgorithmAliases() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();

        XtceDb db = XtceDbFactory.getInstance("refmdb");

        Algorithm algo = db.getAlgorithm("/REFMDB/SUBSYS1/sliding_window");
        assertNotNull(algo);
        String alias = algo.getAlias("namespace1");
        assertEquals("/alternative/name1", alias);
    
        algo = db.getAlgorithm("/REFMDB/SUBSYS1/float_ypr");
        assertNotNull(algo);
        alias = algo.getAlias("namespace1");
        assertEquals("another alternative name", alias);
    }
    
    @Test
    public void testContainerAliases() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();

        XtceDb db = XtceDbFactory.getInstance("refmdb");

        SequenceContainer container = db.getSequenceContainer("/REFMDB/SUBSYS1/PKT1_2");
        assertNotNull(container);
        String alias = container.getAlias("MDB:Pathname");
        assertEquals("REFMDB\\ACQ\\PKTS\\PKT12", alias);
    }
    
    
    
}
