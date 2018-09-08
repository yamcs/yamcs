package com.spaceapplications.yamcs.scpi.commander;

@SuppressWarnings("serial")
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable t) {
        super(message, t);
    }
}
