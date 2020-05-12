package org.yamcs;

import java.io.InputStream;

import org.yamcs.ConfigurationException;

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
