package org.yamcs;

public interface Plugin {

    /**
     * Returns the valid configuration options for this plugin.
     * <p>
     * If {@code null} is returned, Yamcs will attempt to autodiscover plugin options in a resource file named after the
     * reverse qualified name of this plugin with yaml extension. For example: for a plugin
     * {@code com.example.MyPlugin}, Yamcs will search for a classpath resource {@code /com/example/MyPlugin.yaml}.
     * 
     * @return the argument specification. Or {@code null} if there are no options or Yamcs should autodiscover them.
     */
    public default Spec getSpec() {
        return null;
    }

    /**
     * Returns the valid instance-level configuration options for this plugin.
     * <p>
     * If {@code null} is returned, Yamcs will attempt to autodiscover plugin instance-level options in a resource file
     * named after the reverse qualified name of this plugin with yaml extension. For example: for a plugin
     * {@code com.example.MyPlugin}, Yamcs will search for a classpath resource {@code /com/example/MyPlugin.yaml}.
     * 
     * @return the argument specification. Or {@code null} if there are no options or Yamcs should autodiscover them.
     */
    public default Spec getInstanceSpec() {
        return null;
    }

    /**
     * Callback executed when the plugin is loaded.
     * <p>
     * This is executed after Yamcs has created all configured services, but before actually starting them.
     */
    public default void onLoad(YConfiguration config) throws PluginException {
    }
}
