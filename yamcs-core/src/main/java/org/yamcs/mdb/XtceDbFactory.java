package org.yamcs.mdb;

import org.yamcs.ConfigurationException;

/**
 * Use {@link MdbFactory} instead.
 */
@Deprecated
public class XtceDbFactory {

    public static Mdb getInstance(String yamcsInstance) throws ConfigurationException {
        return MdbFactory.getInstance(yamcsInstance);
    }

    public static synchronized Mdb getInstanceByConfig(String yamcsInstance, String config) {
        return MdbFactory.getInstanceByConfig(yamcsInstance, config);
    }
}
