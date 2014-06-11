package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtceproc.ContainerListener;
import org.yamcs.xtceproc.XtceTmProcessor;


/**
 * Keeps track of the subscribers to the containers of a Yamcs Channel.
 */
public class ContainerRequestManager implements ContainerListener {
    
    private Logger log;
    // For each container, all the subscribers
    private Map<SequenceContainer,Set<PacketConsumer>> subscriptions = new HashMap<SequenceContainer,Set<PacketConsumer>>();    
    // Keeps track of the specific name used for subscribing to a container
    private Map<PacketConsumer,List<ContainerWithId>> nameRefs = new HashMap<PacketConsumer, List<ContainerWithId>>();
    
    private XtceTmProcessor tmProcessor;
    private TmPacketProvider tmPacketProvider;
    
    /**
     * Creates a new ContainerRequestManager, configured to listen to a newly
     * created XtceTmProcessor.
     */
    public ContainerRequestManager(Channel yamcsChannel) {
        this(yamcsChannel, new XtceTmProcessor(yamcsChannel));
    }
    
    /**
     * Creates a new ContainerRequestManager, configured to listen to the
     * specified XtceTmProcessor.
     */
    public ContainerRequestManager(Channel yamcsChannel, XtceTmProcessor tmProcessor) {
        this.tmProcessor = tmProcessor;
        log = LoggerFactory.getLogger(getClass().getName() + "[" + yamcsChannel.getName() + "]");
        tmProcessor.setContainerListener(this);
    }
    
    public synchronized void subscribe(PacketConsumer subscriber, NamedObjectId id) {
        SequenceContainer c = tmProcessor.xtcedb.getSequenceContainer(id);
        if (c == null) {
            log.warn("Can't add subscription for container {}. Identification invalid", id);
        } else {
            addSubscription(subscriber, c, id);
        }
    }

    public synchronized void subscribeAll(PacketConsumer subscriber) {
        for (SequenceContainer c : tmProcessor.xtcedb.getSequenceContainers()) {
            NamedObjectId id = NamedObjectId.newBuilder().setName(c.getQualifiedName()).build();
            addSubscription(subscriber, c, id);
        }
    }
    
    private void addSubscription(PacketConsumer subscriber, SequenceContainer container, NamedObjectId requestId) {
        if (!subscriptions.containsKey(container)) {
            subscriptions.put(container, new HashSet<PacketConsumer>());
            tmProcessor.startProviding(container);
        }
        if (subscriptions.get(container).add(subscriber)) {
            if (!nameRefs.containsKey(subscriber)) {
                nameRefs.put(subscriber, new ArrayList<ContainerWithId>());
            }
            nameRefs.get(subscriber).add(new ContainerWithId(container, requestId));
        }
    }

    public synchronized void unsubscribe(PacketConsumer subscriber, NamedObjectId id) {
        SequenceContainer container = tmProcessor.xtcedb.getSequenceContainer(id);
        if (container == null) {
            log.warn("Container removal requested for {} but identification invalid", id);
        } else {
            if (subscriptions.containsKey(container)) {
                Set<PacketConsumer> subscribers = subscriptions.get(container);
                if (subscribers.remove(subscriber)) {
                    if(subscribers.isEmpty()) {
                        // The following call does not do anything (yet)
                        tmProcessor.stopProviding(container);
                    }
                } else {
                    log.warn("Container removal requested for {} but not subscribed", container);      
                }
                
                // Remove also name mapping
                Iterator<ContainerWithId> it = nameRefs.get(subscriber).iterator();
                while (it.hasNext()) {
                    if (it.next().id.equals(id)) {
                        it.remove();
                    }
                }
            } else {
                log.warn("Container removal requested for {} but not subscribed", container);
            }
        }
    }
    
    public synchronized void unsubscribeAll(PacketConsumer subscriber) {
        for (Entry<SequenceContainer, Set<PacketConsumer>> entry : subscriptions.entrySet()) {
            Set<PacketConsumer> subscribers = entry.getValue();
            subscribers.remove(subscriber);
            if (subscribers.isEmpty()) {
                // The following call does not do anything (yet)
                tmProcessor.stopProviding(entry.getKey());
            }
        }
        
        nameRefs.remove(subscriber);
    }
    
    /**
     * Sets the telemetry packet provider
     */
    public void setPacketProvider(TmPacketProvider tmPacketProvider) {
        this.tmPacketProvider = tmPacketProvider;
        tmPacketProvider.setTmProcessor(tmProcessor);
    }

    @Override
    public synchronized void update(List<ContainerExtractionResult> results) {
        log.trace("Getting update of {} container(s)", results.size());
        for (ContainerExtractionResult result : results) {
            SequenceContainer def = result.getContainer();
            if (!subscriptions.containsKey(def)) continue;
            for (PacketConsumer subscriber : subscriptions.get(def)) {
                for (ContainerWithId nameMapping : nameRefs.get(subscriber)) {
                    // Return with the same name as used for subscription 
                    if (nameMapping.def.equals(def)) {
                        ItemIdPacketConsumerStruct struct = new ItemIdPacketConsumerStruct(
                                        subscriber, nameMapping.id, def,
                                        result.getAcquisitionTime(), result.getGenerationTime());
                        subscriber.processPacket(struct, result.getContainerContent());
                    }
                }
            }
        }
    }
    
    public XtceTmProcessor getTmProcessor() {
        return tmProcessor;
    }

    public void start() {
        tmProcessor.start();
    }
    
    public void quit() {
        tmPacketProvider.stop();
    }
    
    private static class ContainerWithId {
        SequenceContainer def; // The definition of the container
        NamedObjectId id; // The id used by the subscriber for refering to this container
        ContainerWithId(SequenceContainer def, NamedObjectId id) {
            this.def = def;
            this.id = id;
        }
    }
}
