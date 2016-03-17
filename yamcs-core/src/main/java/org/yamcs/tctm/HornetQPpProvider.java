package org.yamcs.tctm;


import org.yamcs.ConfigurationException;

/**
 * receives data from HornetQ and publishes it into a yamcs stream
 * 
 * @author nm
 *
 * @deprecated please use {@link HornetQPpDataLink} instead
 */
@Deprecated
public class HornetQPpProvider extends  HornetQPpDataLink {
    public HornetQPpProvider(String instance, String name, String hornetAddress) throws ConfigurationException  {
        super(instance, name, hornetAddress);
    }
}

