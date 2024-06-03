package org.yamcs;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.yamcs.Spec.OptionType;
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
        var yamcs = YamcsServer.getServer();
        for (var plugin : ServiceLoader.load(Plugin.class)) {
            var propsResource = "/META-INF/yamcs/" + plugin.getClass().getName() + "/plugin.properties";
            var props = new Properties();
            try (var in = getClass().getResourceAsStream(propsResource)) {
                props.load(in);
            }

            var pluginMetadata = new PluginMetadata(props);
            metadata.put(plugin.getClass(), pluginMetadata);

            var discoveredSpecs = discoverPluginOptions(plugin.getClass());

            // Plugins may programmatically define a spec, but default to
            // autodiscovery based on a resource descriptor.
            var spec = plugin.getSpec();
            if (spec == null) {
                spec = discoveredSpecs.globalSpec;
            }

            // Allow to disable any plugin
            spec.addOption("enabled", OptionType.BOOLEAN).withDefault(true);

            yamcs.addConfigurationSection(ConfigScope.YAMCS, pluginMetadata.getName(), spec);

            spec = plugin.getInstanceSpec();
            if (spec == null) {
                spec = discoveredSpecs.instanceSpec;
            }
            yamcs.addConfigurationSection(ConfigScope.YAMCS_INSTANCE, pluginMetadata.getName(), spec);
        }
    }

    @SuppressWarnings("unchecked")
    private PluginSpecs discoverPluginOptions(Class<?> pluginClass) throws IOException {
        var specs = new PluginSpecs();
        try (var in = pluginClass.getResourceAsStream(pluginClass.getSimpleName() + ".yaml")) {
            if (in != null) {
                var yaml = new Yaml();
                Map<String, Object> pluginDescriptor = yaml.load(in);
                if (pluginDescriptor.containsKey("options")) {
                    var optionDescriptors = (Map<String, Map<String, Object>>) pluginDescriptor
                            .get("options");
                    try {
                        specs.globalSpec = Spec.fromDescriptor(optionDescriptors);
                    } catch (ValidationException e) {
                        // Plugin error. Just throw it up because it must be fixed.
                        throw new RuntimeException(e);
                    }
                }
                if (pluginDescriptor.containsKey("instanceOptions")) {
                    var optionDescriptors = (Map<String, Map<String, Object>>) pluginDescriptor
                            .get("instanceOptions");
                    try {
                        specs.instanceSpec = Spec.fromDescriptor(optionDescriptors);
                    } catch (ValidationException e) {
                        // Plugin error. Just throw it up because it must be fixed.
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return specs;
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

        for (var plugin : ServiceLoader.load(Plugin.class)) {
            var meta = metadata.get(plugin.getClass());
            if (disabledPlugins.contains(meta.getName())) {
                log.debug("Ignoring plugin {} (disabled by user config)", meta.getName());
            } else {
                var pluginConfig = yconf.getConfigOrEmpty(meta.getName());
                if (pluginConfig.getBoolean("enabled", true)) {
                    plugins.put(plugin.getClass(), plugin);
                } else {
                    log.debug("Ignoring plugin {} (disabled by user config)", meta.getName());
                }
            }
        }
    }

    public void loadPlugins() throws PluginException {
        var yamcsConfig = YamcsServer.getServer().getConfig();
        for (var plugin : plugins.values()) {
            var meta = metadata.get(plugin.getClass());
            log.debug("Loading plugin {} {}", meta.getName(), meta.getVersion());
            try {
                var config = yamcsConfig.getConfigOrEmpty(meta.getName());
                plugin.onLoad(config);
            } catch (PluginException e) {
                log.error("Could not load plugin {} {}", meta.getName(), meta.getVersion());
                throw e;
            }
        }
    }

    private static class PluginSpecs {
        Spec globalSpec = new Spec();
        Spec instanceSpec = new Spec();
    }
}
