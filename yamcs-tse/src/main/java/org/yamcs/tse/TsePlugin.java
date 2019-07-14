package org.yamcs.tse;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.spi.Plugin;

public class TsePlugin implements Plugin {

    private String version;

    public TsePlugin() {
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
    }

    @Override
    public String getName() {
        return "yamcs-tse";
    }

    @Override
    public String getDescription() {
        return "Interface with Test Support Equipment";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getVendor() {
        return "Space Applications Services";
    }

    @Override
    public void onLoad() {
        YamcsServer yamcs = YamcsServer.getServer();

        // Activate TSE Commander (only if user did not manually add this service)
        if (yamcs.getGlobalServices(TseCommander.class).isEmpty()) {
            addTseCommander(yamcs);
        }
    }

    /**
     * Starts the TseCommander. These can be configured in tse.yaml
     */
    private void addTseCommander(YamcsServer yamcs) {
        YConfiguration yconf;
        if (YConfiguration.isDefined("tse")) {
            yconf = YConfiguration.getConfiguration("tse");
        } else {
            yconf = YConfiguration.emptyConfig();
        }

        try {
            yamcs.addGlobalService("TSE Commander", TseCommander.class, yconf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
