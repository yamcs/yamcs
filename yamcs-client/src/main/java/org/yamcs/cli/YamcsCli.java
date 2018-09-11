package org.yamcs.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

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
        addSubCommand(new ClientsCli(this));
        addSubCommand(new Config(this));
        addSubCommand(new InstancesCli(this));
        addSubCommand(new LinksCli(this));
        addSubCommand(new ProcessorsCli(this));
        addSubCommand(new ServicesCli(this));
        addSubCommand(new StorageCli(this));
        addSubCommand(new TablesCli(this));
    }

    @Parameter(names = { "-y", "--yamcs-url" }, description = "Yamcs URL")
    private String yamcsUrl;

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
        selectedCommand.validate();
    }

    public static void main(String[] args) {
        YamcsCli yamcsCli = new YamcsCli();
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
}
