package org.yamcs.xtceproc;

import org.yamcs.YConfiguration;

public class ContainerProcessingOptions {
    private static final String CONFIG_KEY_ignoreOutOfContainerEntries = "ignoreOutOfContainerEntries";
    private static final String CONFIG_KEY_expirationTolerance = "expirationTolerance";
    /**
     * If set to true, the entries that fit outside the packet definition, will not be even logged.
     * If set to false, a log message at WARNING level will be printed for the first entry that fits outside the binary
     * packet.
     * 
     * In both cases, the processing stops at first such entry.
     */
    boolean ignoreOutOfContainerEntries = false;

    /**
     * for containers that have the rate in stream, use this number as a multiplier to the minimum expected interval.
     * e.g. if a container comes each 1000 millisec, the parameters will be marked as expired after
     * 1000*expirationTolerance millisec
     */
    double expirationTolerance = 1.9;

    public ContainerProcessingOptions(YConfiguration config) {
        if (config != null) {
            ignoreOutOfContainerEntries = config.getBoolean(CONFIG_KEY_ignoreOutOfContainerEntries, false);
            expirationTolerance = config.getDouble(CONFIG_KEY_expirationTolerance, expirationTolerance);
        }
    }
    /**
     * Default configuration
     */
    public ContainerProcessingOptions() {
    }

    public boolean ignoreOutOfContainerEntries() {
        return ignoreOutOfContainerEntries;
    }

    public void setIgnoreOutOfContainerEntries(
            boolean ignoreOutOfContainerEntries) {
        this.ignoreOutOfContainerEntries = ignoreOutOfContainerEntries;
    }

    public double getExpirationTolerance() {
        return expirationTolerance;
    }

    public void setExpirationTolerance(double expirationTolerance) {
        this.expirationTolerance = expirationTolerance;
    }

}
