package org.yamcs;

import java.io.InputStream;

public interface YConfigurationResolver {
    public InputStream getConfigurationStream(String name) throws ConfigurationException;
}
