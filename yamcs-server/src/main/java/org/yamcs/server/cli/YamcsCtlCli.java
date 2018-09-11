package org.yamcs.server.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YConfiguration.ConfigurationNotFoundException;
import org.yamcs.YConfigurationResolver;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * Command line utility for doing yamcs stuff.
 *
 * This usage is yamcsctl &lt;command&gt; [command_specific_options]
 *
 * @author nm
 */
public class YamcsCtlCli extends Command {

    public YamcsCtlCli() {
        super("yamcsctl", null);
        addSubCommand(new ArchiveCli(this));
        addSubCommand(new Backup(this));
        addSubCommand(new CheckConfig(this));
        addSubCommand(new ParameterArchiveCli(this));
        addSubCommand(new PasswordHashCli(this));
        addSubCommand(new RocksDbCli(this));
        addSubCommand(new XtceDbCli(this));
    }

    @Parameter(names = "--etc-dir", description = "Override default Yamcs configuration directory")
    private String etcDir;

    @Parameter(names = { "-v", "--version" }, description = "Print version information and quit")
    boolean version;

    @Override
    void validate() throws ParameterException {
        if (etcDir != null) {
            YConfiguration.setResolver(new DirConfigurationResolver(etcDir));
        }
        selectedCommand.validate();
    }

    public static void main(String[] args) {
        YamcsCtlCli yamcsCli = new YamcsCtlCli();
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

    public String getEtcDir() {
        return etcDir;
    }

    /**
     * searches all configuration files in a specific directory
     * 
     * @author nm
     *
     */
    public static class DirConfigurationResolver implements YConfigurationResolver {
        private String[] dirs;

        public DirConfigurationResolver(String... dirs) {
            this.dirs = dirs;
        }

        @Override
        public InputStream getConfigurationStream(String name) throws ConfigurationException {
            for (String dir : dirs) {
                // see if the users has an own version of the file
                File f = new File(dir, name);
                if (f.exists()) {
                    try {
                        InputStream is = new FileInputStream(f);
                        return is;
                    } catch (FileNotFoundException e) {
                        throw new ConfigurationNotFoundException("Cannot read file " + f + ": " + e.getMessage(), e);
                    }
                }
            }
            throw new ConfigurationNotFoundException(
                    "Configuration file " + name + " does not exist. Searched in: " + Arrays.toString(dirs));
        }
    }
}
