package org.yamcs.mdb;

import org.yamcs.ConfigurationException;
import org.yamcs.xtce.XtceDb;

/**
 * Use {@link MdbFactory} instead.
 */
@Deprecated
public class XtceDbFactory {

    public static XtceDb getInstance(String yamcsInstance) throws ConfigurationException {
        return MdbFactory.getInstance(yamcsInstance);
    }

    public static synchronized Mdb getInstanceByConfig(String yamcsInstance, String config) {
        return MdbFactory.getInstanceByConfig(yamcsInstance, config);
    }
}
