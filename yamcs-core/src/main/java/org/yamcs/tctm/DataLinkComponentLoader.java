package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

/** Loads and validates a component definition of the form {@code { class: ..., args: ... }}. */
public final class DataLinkComponentLoader {

    private DataLinkComponentLoader() {
    }

    public static <T> T load(YConfiguration definition, Class<T> expectedType, String yamcsInstance,
            String linkName, String role) {
        String className = definition.getString("class");
        YConfiguration args = definition.containsKey("args")
                ? definition.getConfig("args")
                : YConfiguration.emptyConfig();

        Object component;
        final Class<?> componentClass;
        try {
            componentClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException("Cannot find " + role + " component class " + className, e);
        }

        if (DataLinkComponent.class.isAssignableFrom(componentClass)) {
            component = YObjectLoader.loadObject(className);
            var configurable = (DataLinkComponent) component;
            var spec = configurable.getSpec();
            if (spec != null) {
                try {
                    args = spec.validate(args);
                } catch (ValidationException e) {
                    throw new ConfigurationException("Invalid arguments for " + role + " component " + className
                            + ": " + e.getMessage(), e);
                }
            }
            configurable.init(yamcsInstance, linkName, args);
        } else {
            // Compatibility with existing providers, including custom outer-frame providers.
            component = YObjectLoader.loadObject(className, args);
        }

        if (!expectedType.isInstance(component)) {
            throw new ConfigurationException(role + " component " + className + " does not implement "
                    + expectedType.getName());
        }
        return expectedType.cast(component);
    }
}
