package org.yamcs.container;

import org.yamcs.InvalidIdentification;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtceproc.ContainerListener;

import com.google.common.util.concurrent.Service;

/**
 * Interface implemented by all the classes that can provide containers to a
 * ContainerListener
 */
public interface ContainerProvider extends Service {

    public abstract void setContainerListener(ContainerListener containerListener);
    
    public abstract void startProviding(SequenceContainer container);
    
    public abstract void stopProviding(SequenceContainer container);

    /**
     * Start providing all known containers
     */
    public abstract void startProvidingAllContainers();

    /**
     * Returns whether or not a given container can be provided by this provider
     */
    public abstract boolean canProvideContainer(NamedObjectId containerId);

    /**
     * Returns the containerDefinition corresponding to the itemId
     * 
     * @throws InvalidIdentification
     */
    public abstract Container getContainer(NamedObjectId containerId)
                    throws InvalidIdentification;
}
