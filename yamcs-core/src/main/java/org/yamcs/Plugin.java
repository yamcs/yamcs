package org.yamcs;

/*
 * DEPRECATION INFORMATION:
 * 
 * If you're looking where getName(), getDescription(), getVersion() and getVendor() went:
 * They're gone.
 * 
 * Upgrade yamcs-maven-plugin to v1.2.x and add an execution with the 'detect' goal:
 * https://yamcs.org/docs/yamcs-maven-plugin/examples/plugin/
 */
public interface Plugin {

    /**
     * Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    public default void onLoad() throws PluginException {
    }
}
