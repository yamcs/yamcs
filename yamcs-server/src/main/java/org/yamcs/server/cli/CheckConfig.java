package org.yamcs.server.cli;

import java.io.File;
import java.util.List;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.YarchDatabase;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.io.Files;

@Parameters(commandDescription = "Check the Yamcs configuration by loading all services without starting them.")

public class CheckConfig extends Command {
    @Parameter(required = false, description = "config-dir - use this directory in preference for loading configuration files. "
            + "If --no-etc is specified, all configuration files will be loaded from this directory. "
            + "Note that the data directory (yamcs.yaml dataDir) will be changed before starting the services,"
            + "otherwise there will be RocksDB LOCK errors if a yamcs server is running.")
    private List<String> args;

    String configDir;

    @Parameter(names = "--no-etc", required = false, description = "Do not use any file from the default Yamcs etc directory. "
            + "If this is specified, the argument config-dir becomes mandatory.")
    boolean noEtc = false;

    public CheckConfig(Command parent) {
        super("confcheck", parent);
    }

    @Override
    void validate() throws ParameterException {
        super.validate();
        if (noEtc && args == null) {
            throw new ParameterException("If --no-etc option is specified, the config-dir becomes mandatory.");
        }
        if (noEtc && getYamcsCtlCli().getEtcDir() != null) {
            throw new ParameterException("You cannot specify both --etc-dir and --no-etc.");
        }
        if (args != null) {
            if (args.size() != 1) {
                throw new ParameterException("Please specify only one config-dir.");
            }
            configDir = args.get(0);
            File d = new File(configDir);
            if (!d.exists()) {
                throw new ParameterException("Error: directory '" + configDir + "' does not exist");
            }
        }
    }

    @Override
    void execute() throws Exception {
        String etcDir = getYamcsCtlCli().getEtcDir();
        if (etcDir != null) {
            if (configDir == null) {
                YConfiguration.setResolver(new YamcsCtlCli.DirConfigurationResolver(etcDir));
            } else {
                YConfiguration.setResolver(new YamcsCtlCli.DirConfigurationResolver(configDir, etcDir));
            }
        } else {
            if (noEtc) {
                YConfiguration.setResolver(new YamcsCtlCli.DirConfigurationResolver(configDir));
            } else if (configDir != null) {
                YConfiguration.setup(null, configDir);
            }
        }
        File tmpDir = Files.createTempDir();
        // change the data directory, otherwise we will get RocksDB LOCK errors if a yamcs server is running
        YarchDatabase.setHome(tmpDir.getAbsolutePath());

        YamcsServer.createGlobalServicesAndInstances();
        FileUtils.deleteRecursively(tmpDir);
        console.println("The configuration appears to be valid.");
    }
}
