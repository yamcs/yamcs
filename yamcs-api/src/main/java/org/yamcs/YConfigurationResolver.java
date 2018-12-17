package org.yamcs;

import java.io.InputStream;

public interface YConfigurationResolver {
    /**
     * Searches configuration with name
     * 
     * @param name
     * @return
     * @throws ConfigurationException
     *             - thrown if configuration cannot be found
     */
    public InputStream getConfigurationStream(String name) throws ConfigurationException;
}
