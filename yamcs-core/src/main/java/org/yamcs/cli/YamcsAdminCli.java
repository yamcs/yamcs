package org.yamcs.cli;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import org.yamcs.FileBasedConfigurationResolver;
import org.yamcs.YConfiguration;

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
        addSubCommand(new Backup(this));
        addSubCommand(new CheckConfig(this));
        addSubCommand(new ParameterArchiveCli(this));
        addSubCommand(new PasswordHashCli(this));
        addSubCommand(new RocksDbCli(this));
        addSubCommand(new XtceDbCli(this));
    }

    @Parameter(names = "--etc-dir", converter = PathConverter.class, description = "Override default Yamcs configuration directory")
    private Path configDirectory;

    @Parameter(names = { "-v", "--version" }, description = "Print version information and quit")
    boolean version;

    @Override
    void validate() throws ParameterException {
        if (configDirectory != null) {
            YConfiguration.setResolver(new FileBasedConfigurationResolver(configDirectory));
        }
        selectedCommand.validate();
    }

    public static void main(String[] args) {
        YamcsAdminCli yamcsCli = new YamcsAdminCli();
        yamcsCli.parse(args);

        try {
            yamcsCli.validate();
            yamcsCli.execute();
        } catch (ExecutionException e) {
            System.err.println(e.getCause());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }

        System.exit(0);
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }
}
