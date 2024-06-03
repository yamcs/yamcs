package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;

public class MdbTest extends AbstractCliTest {
    @BeforeEach
    public void resetConfig() {
        YConfiguration.setupTest(null);
    }

    @Test
    public void testMdbPrintCli() throws Exception {
        YamcsAdminCli yamcsCli = new YamcsAdminCli();
        yamcsCli.parse(new String[] { "mdb", "print", "refmdb" });
        yamcsCli.validate();
        yamcsCli.execute();

        String out = mconsole.output();
        assertTrue(out.contains("SpaceSystem /REFMDB"));
        assertTrue(out.contains("SequenceContainer name: PKT3"));
        assertTrue(out.contains("Algorithm name: ctx_param_test"));
        assertTrue(out.contains("MetaCommand name: CALIB_TC"));
    }

    @Test
    public void testMdbVerifyCli() throws Exception {
        YConfiguration.setupTest("src/test/resources/");

        YamcsAdminCli yamcsCli = new YamcsAdminCli();
        yamcsCli.parse(new String[] { "mdb", "verify", "refmdb" });
        yamcsCli.validate();
        yamcsCli.execute();
        String out = mconsole.output();
        assertTrue(out.contains("MDB loaded successfully"));
    }
}
