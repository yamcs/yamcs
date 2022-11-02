package org.yamcs.cli;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.yamcs.FileBasedConfigurationResolver;
import org.yamcs.YConfiguration;

import com.beust.jcommander.internal.Console;

public abstract class AbstractCliTest {
    PrintStream psout;
    static MockConsole mconsole = new MockConsole();

    @BeforeAll
    public static void beforeClass() {
        Command.console = mconsole;
        Command.exitFunction = status -> {
            throw new ExitException(status);
        };
    }

    @BeforeEach
    public void resetOutput() {
        mconsole.reset();
    }

    /**
     * runs main and returns the exitCode
     */
    int runMain(String... args) {
        try {
            YamcsAdminCli.main(args);
        } catch (ExitException e) {
            return e.exitStatus;
        }
        return 0;
    }

    // create a temporary directory containing a yamcs.yaml and a yamcs-data
    static Path createTmpEtcData() throws IOException {
        Path etcdata = Files.createTempDirectory("etcdata-");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(etcdata.resolve("yamcs.yaml").toFile()))) {
            writer.write("dataDir: " + etcdata.toAbsolutePath().resolve("yamcs-data"));
        }

        YConfiguration.setResolver(new FileBasedConfigurationResolver(etcdata));
        YConfiguration.clearConfigs();
        return etcdata;
    }

    static class MockConsole implements Console {
        char[] password1;
        char[] password2;

        StringBuilder sb = new StringBuilder();
        boolean firstPassword = true;

        void setPassword(char[] password1, char[] password2) {
            this.password1 = password1;
            this.password2 = password2;
            firstPassword = true;
        }

        public String output() {
            return sb.toString();
        }

        void reset() {
            sb.setLength(0);
        }

        @Override
        public void print(String msg) {
            sb.append(msg);
        }

        @Override
        public void println(String msg) {
            // System.out.println("b");
            sb.append(msg).append("\n");
        }

        @Override
        public char[] readPassword(boolean echoInput) {
            if (firstPassword) {
                firstPassword = false;
                return password1;
            } else {
                return password2;
            }
        }
    }

    // this replaces System.exit(status) called from the CLI commands
    @SuppressWarnings("serial")
    public static class ExitException extends Error {
        final int exitStatus;

        public ExitException(int status) {
            super();
            this.exitStatus = status;
        }
    }

}
