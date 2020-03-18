package org.yamcs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.yamcs.logging.Log;

/**
 * Controls the loading of Yamcs plugins.
 */
public class PluginManager {

    private static final Log log = new Log(PluginManager.class);

    private Map<Class<? extends Plugin>, PluginMetadata> metadata = new HashMap<>();
    private Map<Class<? extends Plugin>, Plugin> plugins = new HashMap<>();

    public PluginManager() throws IOException {
        for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
            String propsResource = "/META-INF/yamcs/" + plugin.getClass().getName() + "/plugin.properties";
            Properties props = new Properties();
            try (InputStream in = getClass().getResourceAsStream(propsResource)) {
                props.load(in);
            }
            metadata.put(plugin.getClass(), new PluginMetadata(props));
        }
    }

    public Collection<Plugin> getPlugins() {
        return plugins.values();
    }

    @SuppressWarnings("unchecked")
    public <T extends Plugin> T getPlugin(Class<T> clazz) {
        return (T) plugins.get(clazz);
    }

    public <T extends Plugin> PluginMetadata getMetadata(Class<T> clazz) {
        return metadata.get(clazz);
    }

    public void discoverPlugins() {
        List<String> disabledPlugins;
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        if (yconf.containsKey("disabledPlugins")) {
            disabledPlugins = yconf.getList("disabledPlugins");
        } else {
            disabledPlugins = Collections.emptyList();
        }

        for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
            PluginMetadata meta = metadata.get(plugin.getClass());
            if (disabledPlugins.contains(meta.getName())) {
                log.debug("Ignoring plugin {} (disabled by user config)", meta.getName());
            } else {
                plugins.put(plugin.getClass(), plugin);
            }
        }
    }

    public void loadPlugins() throws PluginException {
        for (Plugin plugin : plugins.values()) {
            PluginMetadata meta = metadata.get(plugin.getClass());
            log.debug("Loading plugin {} {}", meta.getName(), meta.getVersion());
            try {
                plugin.onLoad();
            } catch (PluginException e) {
                log.error("Could not load plugin {} {}", meta.getName(), meta.getVersion());
                throw e;
            }
        }
    }
}
