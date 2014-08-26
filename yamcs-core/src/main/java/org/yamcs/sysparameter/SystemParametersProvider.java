package org.yamcs.sysparameter;

import java.util.Collection;

import org.yamcs.protobuf.Pvalue.ParameterValue;

/**
 * Interface implemented by classes that want to provide system parameters
 * 
 * @author nm
 *
 */
public interface SystemParametersProvider {
    public Collection<ParameterValue> getSystemParameters();
}
