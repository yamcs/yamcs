package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.YamcsVersion;

public class YamcsCliTest extends AbstractCliTest {
    @Test
    public void testGetUsage() throws Exception {
        YamcsAdminCli yamcsCli = new YamcsAdminCli();
        yamcsCli.setConsole(mockConsole);
        String usage = yamcsCli.getUsage();
        assertTrue(usage.contains("backup           Perform and restore backups"));
        assertTrue(usage.contains("users            User operations"));

        mockConsole.reset();
        assertEquals(1, runMain());
        assertEquals(usage + "\n", mockConsole.output());

        mockConsole.reset();
        assertEquals(0, runMain("-h"));

        assertEquals(usage + "\n", mockConsole.output());
    }

    @Test
    public void testVersion() throws Exception {
        int exitStatus = runMain("--version");
        assertEquals(0, exitStatus);
        String out = mockConsole.output();
        assertTrue(out.contains("yamcs " + YamcsVersion.VERSION + ", build " + YamcsVersion.REVISION));
    }

    @Test
    public void testInvalidCommand() throws Exception {
        int exitStatus = runMain("bogus");
        assertEquals(1, exitStatus);
        String out = mockConsole.output();
        assertTrue(out.contains("'bogus' is not a valid command "));
    }

    @Test
    public void testInvalidOption() throws Exception {
        assertEquals(1, runMain("--bogus"));
        assertTrue(mockConsole.output().contains("Unknown option '--bogus'"));
    }
}
