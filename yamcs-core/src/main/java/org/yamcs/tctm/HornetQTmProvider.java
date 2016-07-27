package org.yamcs.tctm;

import org.yamcs.ConfigurationException;


/**
 * receives data from ActiveMQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 * @deprecated please use {@link HornetqTmDataLink} instead
 */
@Deprecated
public class HornetQTmProvider extends  HornetQTmDataLink {
    public HornetQTmProvider(String instance, String name, String hornetAddress) throws ConfigurationException  {
        super(instance, name, hornetAddress);
    }
}

