package org.yamcs.spi;

public interface Plugin {

    public String getName();

    public String getDescription();

    public String getVersion();

    public String getVendor();

    /**
     * <strong>(experimental api)</strong> Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    public default void onLoad() {
    }
}
