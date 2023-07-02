package org.yamcs;

@FunctionalInterface
public interface YConfigurationPropertyProvider {

    /**
     * Get the value of a named property, or {@code null} if not found.
     */
    String get(String name);
}
