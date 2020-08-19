package org.yamcs;

import java.io.InputStream;

public interface YConfigurationResolver {

    /**
     * Searches configuration by name
     * 
     * @param name
     * @return
     * @throws ConfigurationException
     *             when configuration cannot be found
     */
    public InputStream getConfigurationStream(String name) throws ConfigurationException;
}
