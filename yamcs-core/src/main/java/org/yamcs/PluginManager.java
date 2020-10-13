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
import org.yaml.snakeyaml.Yaml;

/**
 * Controls the loading of Yamcs plugins.
 */
public class PluginManager {

    private static final Log log = new Log(PluginManager.class);

    private Map<Class<? extends Plugin>, PluginMetadata> metadata = new HashMap<>();
    private Map<Class<? extends Plugin>, Plugin> plugins = new HashMap<>();

    public PluginManager() throws IOException {
        YamcsServer yamcs = YamcsServer.getServer();
        for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
            String propsResource = "/META-INF/yamcs/" + plugin.getClass().getName() + "/plugin.properties";
            Properties props = new Properties();
            try (InputStream in = getClass().getResourceAsStream(propsResource)) {
                props.load(in);
            }

            PluginMetadata pluginMetadata = new PluginMetadata(props);
            metadata.put(plugin.getClass(), pluginMetadata);

            // Allow plugins to manually define a spec, but default to
            // autodiscovery based on a resource descriptor.
            Spec spec = plugin.getSpec();
            if (spec == null) {
                spec = discoverPluginOptions(plugin.getClass());
            }
            if (spec != null) {
                yamcs.addConfigurationSection(pluginMetadata.getName(), spec);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Spec discoverPluginOptions(Class<?> pluginClass) throws IOException {
        try (InputStream in = pluginClass.getResourceAsStream(pluginClass.getSimpleName() + ".yaml")) {
            if (in != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> pluginDescriptor = yaml.load(in);
                if (pluginDescriptor.containsKey("options")) {
                    Map<String, Map<String, Object>> optionDescriptors = (Map<String, Map<String, Object>>) pluginDescriptor
                            .get("options");
                    try {
                        return Spec.fromDescriptor(optionDescriptors);
                    } catch (ValidationException e) {
                        // Plugin error. Just throw it up because it must be fixed.
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return null;
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
        YConfiguration yamcsConfig = YamcsServer.getServer().getConfig();
        for (Plugin plugin : plugins.values()) {
            PluginMetadata meta = metadata.get(plugin.getClass());
            log.debug("Loading plugin {} {}", meta.getName(), meta.getVersion());
            try {
                YConfiguration config = YConfiguration.emptyConfig();
                if (yamcsConfig.containsKey(meta.getName())) {
                    config = yamcsConfig.getConfig(meta.getName());
                }
                plugin.onLoad(config);
            } catch (PluginException e) {
                log.error("Could not load plugin {} {}", meta.getName(), meta.getVersion());
                throw e;
            }
        }
    }
}
