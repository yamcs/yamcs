package org.yamcs.parameter;

import java.util.Collection;

/**
 * Interface implemented by classes that want to provide system parameters
 * 
 * @author nm
 *
 */
public interface SystemParametersProducer {
    public Collection<ParameterValue> getSystemParameters();
}
