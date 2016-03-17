package org.yamcs.tctm;

import org.yamcs.ConfigurationException;

/**
 * @deprecated please used {@link TcmTmDataLink} instead
 * 
 * @author nm
 *
 */
@Deprecated
public class TcpTmProvider extends TcpTmDataLink {
    protected TcpTmProvider(String instance, String name) {// dummy constructor needed by subclass constructors
       super(instance, name);
    }

    public TcpTmProvider(String instance, String name, String spec) throws ConfigurationException  {
        super(instance, name, spec);
    }
}
