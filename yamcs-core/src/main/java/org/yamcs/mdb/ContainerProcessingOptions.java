package org.yamcs.mdb;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.Spec.OptionType;

public class ContainerProcessingOptions {
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

    /**
     * If this is false, the sub-containers appearing as part of container entries, are not included as part of
     * container result. XtceTmRecorder does not want to use them as archiving partitions.
     */
    boolean resultIncludesSubcontainers = true;

    int maxArraySize = 10000;

    public ContainerProcessingOptions(YConfiguration config) {
        if (config != null) {
            ignoreOutOfContainerEntries = config.getBoolean("ignoreOutOfContainerEntries", false);
            expirationTolerance = config.getDouble("expirationTolerance", expirationTolerance);
            maxArraySize = config.getInt("maxArraySize", maxArraySize);
        }
    }

    public static Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("ignoreOutOfContainerEntries", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("expirationTolerance", OptionType.FLOAT).withDefault(1.9);
        spec.addOption("maxArraySize", OptionType.INTEGER).withDefault(10000);

        return spec;
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

    public boolean isSubcontainerPartOfResult() {
        return resultIncludesSubcontainers;
    }

    public void setSubcontainerPartOfResult(boolean resultIncludesSubcontainers) {
        this.resultIncludesSubcontainers = resultIncludesSubcontainers;
    }

    public int getMaxArraySize() {
        return maxArraySize;
    }
}
