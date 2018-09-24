package org.yamcs.parameter;

import java.util.List;

import org.yamcs.xtce.Parameter;

/**
 * Handles parameters that can be set from the clients.
 * 
 * @author nm
 *
 *///this should be renamed to SoftwareParameterManager when we remove that class kept for backward compatibility
public interface SoftwareParameterManagerIf {

    void updateParameters(List<ParameterValue> pvals);

    void updateParameter(Parameter p, Value v);

}
