package org.yamcs.tctm;

import java.io.File;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Reads telemetry files from the directory yamcs.incomingDir/tm
 * @author mache
 *
 * @deprecated this class is deprecated - please use {@link FilePollingTmDataLink} instead
 */
@Deprecated
public class FilePollingTmProvider extends FilePollingTmDataLink {
    public FilePollingTmProvider(String yamcsInstance, String name, String incomingDir) {
        super(yamcsInstance, name, incomingDir);
    }

    public FilePollingTmProvider(String archiveInstance, String name) throws ConfigurationException {
        this(archiveInstance, name, YConfiguration.getConfiguration("yamcs").getString("incomingDir")
                +File.separator+archiveInstance+File.separator+"tm");
    }
}
