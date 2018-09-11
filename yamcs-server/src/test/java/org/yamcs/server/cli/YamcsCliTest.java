package org.yamcs.server.cli;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Before;
import org.junit.Test;

public class YamcsCliTest {
    ByteArrayOutputStream outStream;

    @Before
    public void changeSysOut() {
        outStream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outStream);
        System.setOut(ps);
    }

    @Test
    public void testXtceDbCli() throws Exception {
        YamcsCtlCli yamcsCli = new YamcsCtlCli();
        yamcsCli.parse(new String[] { "xtcedb", "print", "refmdb" });
        yamcsCli.validate();
        yamcsCli.execute();
        String out = outStream.toString();
        assertTrue(out.contains("SpaceSystem /REFMDB"));
        assertTrue(out.contains("SequenceContainer name: PKT3"));
        assertTrue(out.contains("Algorithm name: ctx_param_test"));
        assertTrue(out.contains("MetaCommand name: CALIB_TC"));
    }
}
