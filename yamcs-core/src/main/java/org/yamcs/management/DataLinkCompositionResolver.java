package org.yamcs.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/** Resolves a named data-link composition from the selected startup mode. */
public class DataLinkCompositionResolver {
    private static final Set<String> ALLOWED_SLOTS = Set.of(
            "protocol", "protocolSecurity", "outerFrame", "channelCoding", "transport");

    private final YConfiguration instanceConfig;

    public DataLinkCompositionResolver(YConfiguration instanceConfig) {
        this.instanceConfig = instanceConfig;
    }

    public YConfiguration resolve(YConfiguration linkConfig) {
        if (!linkConfig.containsKey("composition")) {
            return linkConfig;
        }
        if (linkConfig.containsKey("components")) {
            throw error(linkConfig, "cannot specify both composition and inline components");
        }
        if (!instanceConfig.containsKey("dataLinkComposition")) {
            throw error(linkConfig, "requires dataLinkComposition at instance level");
        }

        YConfiguration dataLinkComposition = instanceConfig.getConfig("dataLinkComposition");
        String modeName = dataLinkComposition.getString("selectedMode");
        String compositionName = linkConfig.getString("composition");
        Map<String, Object> componentDefinitions = map(dataLinkComposition.toMap().get("components"),
                "dataLinkComposition.components");
        Map<String, Object> modes = map(dataLinkComposition.toMap().get("modes"), "dataLinkComposition.modes");
        Map<String, Object> mode = map(modes.get(modeName), "dataLinkComposition.modes." + modeName);
        Map<String, Object> composition = map(mode.get(compositionName),
                "dataLinkComposition.modes." + modeName + "." + compositionName);

        if (!composition.containsKey("protocol") || !composition.containsKey("transport")) {
            throw error(linkConfig, "composition '" + compositionName + "' in mode '" + modeName
                    + "' requires protocol and transport slots");
        }

        Map<String, Object> resolvedComponents = new LinkedHashMap<>();
        for (var entry : composition.entrySet()) {
            String slot = entry.getKey();
            if (!ALLOWED_SLOTS.contains(slot)) {
                throw error(linkConfig, "composition '" + compositionName + "' uses unsupported slot '" + slot
                        + "'");
            }
            if (!(entry.getValue() instanceof String componentName)) {
                throw error(linkConfig, "component reference at " + compositionName + "." + slot
                        + " must be a string");
            }
            Object definition = componentDefinitions.get(componentName);
            if (definition == null) {
                throw error(linkConfig, "composition '" + compositionName + "' references unknown component '"
                        + componentName + "'");
            }
            Map<String, Object> component = map(definition, "dataLinkComposition.components." + componentName);
            if (!component.containsKey("class")) {
                throw error(linkConfig, "component '" + componentName + "' is missing class");
            }
            resolvedComponents.put(slot, copyValue(component));
        }

        Map<String, Object> resolvedLink = copyMap(linkConfig.toMap());
        resolvedLink.put("components", resolvedComponents);
        return YConfiguration.wrap(resolvedLink);
    }

    private static ConfigurationException error(YConfiguration linkConfig, String message) {
        return new ConfigurationException("Data link '" + linkConfig.getString("name", "<unnamed>") + "' "
                + message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?>)) {
            throw new ConfigurationException("Missing or invalid map at " + path);
        }
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copyValue(value)));
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(String.valueOf(key), copyValue(nested)));
            return result;
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(nested -> result.add(copyValue(nested)));
            return result;
        }
        return value;
    }
}
