package org.yamcs;

/**
 * Represents a type of configuration file
 */
public enum ConfigScope {

    /**
     * Represents yamcs.yaml configuration. This file contains global configuration options.
     */
    YAMCS,

    /**
     * Represents yamcs.[instance].yaml configuration. Such files contain instance-specific configuration options.
     */
    YAMCS_INSTANCE
}
