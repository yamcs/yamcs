package org.yamcs.artemis;

import org.yamcs.Plugin;

public class ArtemisPlugin implements Plugin {

    private String version;

    public ArtemisPlugin() {
        Package pkg = getClass().getPackage();
        version = (pkg != null) ? pkg.getImplementationVersion() : null;
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
