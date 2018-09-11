package org.yamcs.server.cli;

import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.Parameters;

/**
 * Command line utility for doing general archive operations
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Archive operations")
public class ArchiveCli extends Command {
    public ArchiveCli(YamcsCtlCli yamcsCli) {
        super("archive", yamcsCli);
        addSubCommand(new ArchiveUpgradeCommand(this));
        TimeEncoding.setUp();
    }
}
