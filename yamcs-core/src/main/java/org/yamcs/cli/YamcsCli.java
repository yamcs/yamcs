package org.yamcs.cli;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YConfigurationResolver;
import org.yamcs.YConfiguration.ConfigurationNotFoundException;
import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * Command line utility for doing yamcs stuff.
 * 
 * This usage is yamcs &lt;command&gt; [command_specific_options]
 * 
 * The commands implemented currently are:
 *   backup
 * 
 * @author nm
 *
 */
public class YamcsCli extends Command {    

    public YamcsCli() {
        super("yamcs", null);
        addSubCommand(new Backup(this));
        addSubCommand(new RocksDbCli(this));
        addSubCommand(new ArchiveCli(this));
        addSubCommand(new XtceDbCli(this));
        addSubCommand(new TablesCli(this));
        addSubCommand(new ParameterArchiveCli(this));
    }

    @Parameter(names="-y", description="yamcs url")
    private String yamcsUrl;

    @Parameter(names="--etcDir", description="yamcs configuration directory to use (instead of the default /opt/yamcs/etc, ~/.yamcs/)")
    private String etcDir;
    
    YamcsConnectionProperties ycp;


    @Override
    void validate() throws ParameterException {
        if(yamcsUrl!=null) {
            try {
                ycp = YamcsConnectionProperties.parse(yamcsUrl);
            } catch (URISyntaxException e) {
                throw new ParameterException("Invalid yamcs url '"+yamcsUrl+"'");
            }
        }
        if(etcDir!=null) {
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
            System.err.println(e.getMessage());
        }
    }

    /**
     * searches all configuration files in a specific directory
     * @author nm
     *
     */
    public static class DirConfigurationResolver implements YConfigurationResolver {
        private String dir;
        public DirConfigurationResolver(String dir) {
            this.dir = dir;
        }

        @Override
        public InputStream getConfigurationStream(String name) throws ConfigurationException {
            //see if the users has an own version of the file
            File f = new File(dir+"/"+name);
            if(f.exists()) {
                try {
                    InputStream is = new FileInputStream(f);
                    return is;
                } catch (FileNotFoundException e) {
                    throw new ConfigurationNotFoundException("Cannot read file "+f);
                }
            } else {
                throw new ConfigurationNotFoundException("Configuration file "+f+" does not exist");
            }
        }
    }
}