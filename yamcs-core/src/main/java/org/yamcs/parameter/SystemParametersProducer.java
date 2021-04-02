package org.yamcs.parameter;

import java.util.Collection;
import java.util.Collections;

/**
 * Interface implemented by classes that want to provide system parameters.
 * These are collected regularly by the {@link SystemParametersService}
 * 
 * @author nm
 *
 */
public interface SystemParametersProducer {
    @Deprecated
    /**
     * 
     * @deprecated Please use the method {@link #getSystemParameters(long)}
     */
    default public Collection<ParameterValue> getSystemParameters() {
        return Collections.emptyList();
    }

    /**
     * return the next bunch of parameter values.
     * <p>
     * The gentime is the mission time when the parameter collection started. The returning parameters can use this time
     * to allow all parameters in one collection interval to be timestamped with the same time.
     */
    default Collection<ParameterValue> getSystemParameters(long gentime) {
        return getSystemParameters();
    }

    /**
     * How often this producer should be called. This is a multiplier for the base frequency which is 1 second.
     * <p>
     * For example a value of 3 means call each 3 seconds)
     * 
     * @return
     */
    default int getFrequency() {
        return 1;
    }

}
