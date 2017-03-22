package org.yamcs.cli;


import java.net.URISyntaxException;

import org.yamcs.api.YamcsConnectionProperties;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.internal.Console;

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