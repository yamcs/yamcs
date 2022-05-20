package org.yamcs.cli;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum OutputFormat {

    DEFAULT,
    JSON;

    public static String joinOptions() {
        return Arrays.asList(values()).stream()
                .map(f -> f.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }
}
