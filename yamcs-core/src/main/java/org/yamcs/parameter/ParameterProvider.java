package org.yamcs.parameter;

import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
/**
 * interface implemented by all the classes that can provide parameters to the ParameterRequestManager.
 * 
 * When the classes inherited from this object are created as part of a realtime processor, they need two constructors:
 *  (String yamcsInstance, args)
 *  
 * When called as part of a replay processor, the following constructor is invoked 
 *  (String yamcsInstance, args, ReplayRequest)
 *  
 *  args is the yaml parsed object used in the definition of the provider in yprocessor.yaml
 *  
 * @author nm
 *
 */
public interface ParameterProvider extends ProcessorService {
    /**
     * Called before the startup to pass on the processor and initialize whatever needed
     * @param processor
     * @throws ConfigurationException 
     */
    public abstract void init(Processor processor);

    /**
     * Send parameters to this listener.
     * Normally is the channel.parameterRequestManager but something different may be used for unit tests or special applications (PacketViewer)
     * 
     * @param parameterListener
     */
    public abstract void setParameterListener(ParameterListener parameterListener);

    /**
     * Adds a new parameter to the list  of parameters that have to provided 
     * @param paramDef
     */
    public abstract void startProviding(Parameter paramDef);

    /**
     * start providing all known parameters
     */
    public abstract void startProvidingAll();

    /**
     * Removes a parameter from the list of parameters that have to be provided
     * @param paramDef
     */
    public abstract void stopProviding(Parameter paramDef);

    /**
     * Returns whether or not a given parameter can be provided by this provider
     * @return
     */
    public abstract boolean canProvide(NamedObjectId paraId);
    /**
     * Returns the parameterDefinition corresponding to the parameter id
     * @param paraId - id of the parameter that is returned
     * @return
     * @throws InvalidIdentification
     */
    public abstract Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification;

    public abstract boolean canProvide(Parameter param);
}