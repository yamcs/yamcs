package org.yamcs.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import org.yamcs.FileBasedConfigurationResolver;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.yarch.YarchDatabase;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.PathConverter;

/**
 * Command line utility for doing yamcs stuff.
 *
 * This usage is yamcsadmin &lt;command&gt; [command_specific_options]
 *
 * @author nm
 */
public class YamcsAdminCli extends Command {

    public YamcsAdminCli() {
        super("yamcsadmin", null);
        addSubCommand(new BackupCli(this));
        addSubCommand(new CheckConfig(this));
        addSubCommand(new MdbCli(this));
        addSubCommand(new PasswordHashCli(this));
        addSubCommand(new RocksDbCli(this));
        addSubCommand(new UsersCli(this));
    }

    @Parameter(names = "--etc-dir", description = "Path to config directory", converter = PathConverter.class)
    private Path configDirectory = Paths.get("etc").toAbsolutePath();

    @Parameter(names = "--data-dir", description = "Path to data directory", converter = PathConverter.class)
    private Path dataDir;

    @Parameter(names = "--format", description = "Set the format for printing output", converter = OutputFormatConverter.class)
    OutputFormat format = OutputFormat.DEFAULT;

    @Parameter(names = "--log", description = "Level of verbosity")
    private int verbose = 1;

    @Parameter(names = { "-v", "--version" }, description = "Print version information and quit")
    boolean version;

    @Parameter(names = { "--debug" }, hidden = true)
    private boolean debug;

    private void initialize() {
        YConfiguration config = YConfiguration.getConfiguration("yamcs");
        if (dataDir == null) {
            dataDir = Paths.get(config.getString("dataDir"));
        }
        YarchDatabase.setHome(dataDir.toAbsolutePath().toString());
    }

    @Override
    void validate() throws ParameterException {
        selectedCommand.validate();
    }

    // Keep public, required by JCommander
    public static class OutputFormatConverter implements IStringConverter<OutputFormat> {

        @Override
        public OutputFormat convert(String value) {
            try {
                return OutputFormat.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ParameterException(
                        "Unknown value for --format. Possible values: "
                                + OutputFormat.joinOptions());
            }
        }
    }

    public static void main(String[] args) {
        YamcsAdminCli cli = new YamcsAdminCli();
        cli.parse(args);

        Level[] levels = { Level.OFF, Level.WARNING, Level.INFO, Level.FINE };

        Level logLevel = cli.verbose >= levels.length ? Level.ALL : levels[cli.verbose];

        Log.forceStandardStreams(logLevel);

        YConfiguration.setResolver(new FileBasedConfigurationResolver(cli.configDirectory));

        try {
            cli.initialize();
            cli.validate();
            cli.execute();
        } catch (ExecutionException e) {
            System.err.println(e.getCause());
            exit(1);
        } catch (Exception e) {
            if (cli.debug) {
                e.printStackTrace();
            } else {
                // Avoid class prefix if it's a known exception
                System.err.println((e instanceof YamcsAdminException) ? e.getMessage() : e);
            }
            exit(1);
        }
        exit(0);
    }
}
