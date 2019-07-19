package org.yamcs.api;

public interface Plugin {

    /**
     * Short name of this plugin.
     */
    public String getName();

    /**
     * Description of this plugin.
     */
    public String getDescription();

    /**
     * Version of this plugin.
     */
    public String getVersion();

    /**
     * Maintainer of this plugin.
     */
    public String getVendor();

    /**
     * Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    public default void onLoad() throws PluginException {
    }
}
