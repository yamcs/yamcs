package org.yamcs.cli;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.AbstractIntegrationTest;
import org.yamcs.cli.YamcsCli;

public class YamcsCliTest extends AbstractIntegrationTest {
    ByteArrayOutputStream outStream;
    
    
    @Before
    public void changeSysOut() {
        outStream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outStream);
        System.setOut(ps);
    }
    
    @Test
    public void testXtceDbCli() throws Exception {
        YamcsCli yamcsCli = new YamcsCli();
        yamcsCli.parse(new String[] {"xtcedb", "print", "--config", "refmdb"});
        yamcsCli.validate();
        yamcsCli.execute();
        String out = outStream.toString();
        assertTrue(out.contains("SpaceSystem /REFMDB"));
        assertTrue(out.contains("SequenceContainer name: PKT3"));
        assertTrue(out.contains("Algorithm name: ctx_param_test"));
        assertTrue(out.contains("MetaCommand name: CALIB_TC"));
    }
}
