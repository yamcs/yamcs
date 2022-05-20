package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;

public class ConfCheckTest extends AbstractCliTest {

    @BeforeEach
    public void resetConfig() {
        YConfiguration.setupTest(null);
    }

    @Test
    public void testConfCheckOK() throws Exception {
        int exitStatus = runMain("--etc-dir", "src/test/resources/YamcsServer", "confcheck");
        assertEquals(0, exitStatus);
        String out = mconsole.output();
        assertTrue(out.contains("Configuration OK"));
    }

    @Test
    public void testConfCheckNOK1() throws Exception {
        Path etcdir = createTmpEtcData();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(etcdir.resolve("yamcs.yaml").toFile()))) {
            writer.write("services:\n");
            writer.write("  - class: bogus\n");
        }
        try {
            YamcsAdminCli yamcsCli = new YamcsAdminCli();

            yamcsCli.parse(new String[] { "confcheck" });

            yamcsCli.validate();
            yamcsCli.execute();
        } finally {
            FileUtils.deleteRecursively(etcdir);
        }
        String out = mconsole.output();

        assertTrue(out.contains("Cannot instantiate object from class bogus"));
        assertTrue(out.contains("Configuration Invalid"));
    }

    @Test
    public void testConfCheckNOK2() throws Exception {
        Path etcdir = createTmpEtcData();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(etcdir.resolve("yamcs.yaml").toFile()))) {
            writer.write("services:\n");
            writer.write("  - class: org.yamcs.http.HttpServer\n");
            writer.write("    args: {bogus: 0}");
        }
        try {
            YamcsAdminCli yamcsCli = new YamcsAdminCli();

            yamcsCli.parse(new String[] { "confcheck" });

            yamcsCli.validate();
            yamcsCli.execute();
        } finally {
            FileUtils.deleteRecursively(etcdir);
        }
        String out = mconsole.output();
        assertTrue(out.contains("Unknown argument bogus"));
        assertTrue(out.contains("Configuration Invalid"));
    }
}
