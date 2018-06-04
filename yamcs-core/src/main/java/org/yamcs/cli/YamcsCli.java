package org.yamcs.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YConfiguration.ConfigurationNotFoundException;
import org.yamcs.YConfigurationResolver;
import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * Command line utility for doing yamcs stuff.
 *
 * This usage is yamcs &lt;command&gt; [command_specific_options]
 *
 * @author nm
 */
public class YamcsCli extends Command {

    public YamcsCli() {
        super("yamcs", null);
        addSubCommand(new ArchiveCli(this));
        addSubCommand(new Backup(this));
        addSubCommand(new CheckConfig(this));
        addSubCommand(new Config(this));
        addSubCommand(new InstancesCli(this));
        addSubCommand(new ParameterArchiveCli(this));
        addSubCommand(new RocksDbCli(this));
        addSubCommand(new ServicesCli(this));
        addSubCommand(new StorageCli(this));
        addSubCommand(new TablesCli(this));
        addSubCommand(new XtceDbCli(this));
    }

    @Parameter(names = { "-y", "--yamcs-url" }, description = "Yamcs URL")
    private String yamcsUrl;

    @Parameter(names = "--etc-dir", description = "Override default Yamcs configuration directory")
    private String etcDir;

    @Parameter(names = { "-v", "--version" }, description = "Print version information and quit")
    boolean version;

    YamcsConnectionProperties ycp;

    @Override
    void validate() throws ParameterException {
        if (yamcsUrl != null) {
            try {
                ycp = YamcsConnectionProperties.parse(yamcsUrl);
            } catch (URISyntaxException e) {
                throw new ParameterException("Invalid Yamcs URL '" + yamcsUrl + "'");
            }
        } else {
            File preferencesFile = YamcsConnectionProperties.getPreferenceFile();
            if (preferencesFile.exists()) {
                YamcsConnectionProperties yprops = new YamcsConnectionProperties();
                try {
                    yprops.load();
                    ycp = yprops;
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        if (etcDir != null) {
            YConfiguration.setResolver(new DirConfigurationResolver(etcDir));
        }
        selectedCommand.validate();
    }

    public static void main(String[] args) {
        YamcsCli yamcsCli = new YamcsCli();
        yamcsCli.parse(args);

        try {
            yamcsCli.validate();
            yamcsCli.execute();
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
