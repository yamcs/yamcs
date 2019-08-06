package org.yamcs.security;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

/**
 * Wrapper around the (weird) JAAS configuration API.
 */
public class JaasConfiguration extends Configuration {

    private static final JaasConfiguration INSTANCE = new JaasConfiguration();

    private Map<String, AppConfigurationEntry[]> entries = new HashMap<>();

    private JaasConfiguration() {
    }

    public static synchronized void addEntry(String name, AppConfigurationEntry entry) {
        if (INSTANCE.entries.containsKey(name)) {
            throw new UnsupportedOperationException(); // Probably no need for this.
        }
        INSTANCE.entries.put(name, new AppConfigurationEntry[] { entry });
        Configuration.setConfiguration(INSTANCE);
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        AppConfigurationEntry[] matched = entries.get(name);
        return matched != null ? matched : new AppConfigurationEntry[0];
    }
}
