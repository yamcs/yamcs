package org.yamcs.cli;


import java.net.URISyntaxException;

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

}