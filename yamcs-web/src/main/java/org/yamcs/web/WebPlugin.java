package org.yamcs.web;

import org.yamcs.spi.Plugin;

public class WebPlugin implements Plugin {

    private String version;

    public WebPlugin() {
        Package pkg = getClass().getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
    }

    @Override
    public String getName() {
        return "yamcs-web";
    }

    @Override
    public String getDescription() {
        return "Web interface for managing and monitoring Yamcs";
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
