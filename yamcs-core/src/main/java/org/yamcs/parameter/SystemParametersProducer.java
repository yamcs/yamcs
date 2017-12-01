package org.yamcs.parameter;

import java.util.Collection;

/**
 * Interface implemented by classes that want to provide system parameters.
 * These are collected regularly by the {@link SystemParametersCollector}
 * 
 * @author nm
 *
 */
public interface SystemParametersProducer {
    /**
     * return the next bunch of parameter values. The values must contain a reference to a Parameter (i.e. parameter definition)
     * and that Parameter does not need to be coming from the XtceDB - the provider can just make it up on the fly.
     */
    public Collection<ParameterValue> getSystemParameters();
}
