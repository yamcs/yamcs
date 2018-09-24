package org.yamcs.tctm;

import com.google.common.util.concurrent.Service;

/**
 * Interface for components providing parameters aquired from external systems.
 * 
 * @author nm
 *
 */
public interface ParameterDataLink extends Service, Link {
    public void setParameterSink(ParameterSink parameterSink);
}
