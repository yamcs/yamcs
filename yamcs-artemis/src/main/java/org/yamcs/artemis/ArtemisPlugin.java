package org.yamcs.artemis;

import org.yamcs.api.Plugin;

public class ArtemisPlugin implements Plugin {

    private String version;

    public ArtemisPlugin() {
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
    }

    @Override
    public String getName() {
        return "yamcs-artemis";
    }

    @Override
    public String getDescription() {
        return "Send or receive Yamcs stream tuples over Artemis";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getVendor() {
        return "Space Applications Services";
    }
}
