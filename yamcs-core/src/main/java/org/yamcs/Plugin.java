package org.yamcs;

public interface Plugin {

    /**
     * Returns the valid configuration options for this plugin.
     * <p>
     * If <tt>null</tt> is returned, Yamcs will attempt to autodiscover plugin options in a resource file named after
     * the reverse qualified name of this plugin with yaml extension. For example: for a plugin
     * <tt>com.example.MyPlugin</tt>, Yamcs will search for a classpath resource <tt>/com/example/MyPlugin.yaml</tt>.
     * 
     * @return the argument specification. Or <tt>null</tt> if there are no options or Yamcs should autodiscover them.
     */
    public default Spec getSpec() {
        return null;
    }

    /**
     * Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    @Deprecated
    public default void onLoad() throws PluginException {
    }

    /**
     * Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    public default void onLoad(YConfiguration config) throws PluginException {
        // Keep around for a few releases, until plugins are using the new onLoad method
        onLoad();
    }
}
