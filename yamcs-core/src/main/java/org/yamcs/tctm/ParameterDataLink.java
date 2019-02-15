package org.yamcs.tctm;

/**
 * Interface for components providing parameters aquired from external systems.
 * 
 * @author nm
 *
 */
public interface ParameterDataLink extends Link {
    public void setParameterSink(ParameterSink parameterSink);
}
