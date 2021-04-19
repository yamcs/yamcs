package org.yamcs.parameter;

import org.yamcs.ProcessorService;
import org.yamcs.InvalidIdentification;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;

/**
 * interface implemented by all the classes that can provide parameters to the {@link ParameterProcessorManager}
 *
 */
public interface ParameterProvider extends ProcessorService {
    /**
     * Send parameters to this processor.
     * 
     * @param parameterProcessor
     */
    public abstract void setParameterProcessor(ParameterProcessor parameterProcessor);

    /**
     * Adds a new parameter to the list of parameters that have to provided
     * 
     * @param paramDef
     */
    public abstract void startProviding(Parameter paramDef);

    /**
     * start providing all known parameters
     */
    public abstract void startProvidingAll();

    /**
     * Removes a parameter from the list of parameters that have to be provided
     * 
     * @param paramDef
     */
    public abstract void stopProviding(Parameter paramDef);

    /**
     * Returns whether or not a given parameter can be provided by this provider
     * 
     * @return
     */
    public abstract boolean canProvide(NamedObjectId paraId);

    /**
     * Returns the parameterDefinition corresponding to the parameter id
     * 
     * @param paraId
     *            - id of the parameter that is returned
     * @return
     * @throws InvalidIdentification
     */
    public abstract Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification;

    public abstract boolean canProvide(Parameter param);
}
