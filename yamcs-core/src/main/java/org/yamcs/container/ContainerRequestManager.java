package org.yamcs.container;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ContainerListener;
import org.yamcs.mdb.XtceTmProcessor;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;

/**
 * Keeps track of the subscribers to the containers of a processor.
 */
public class ContainerRequestManager implements ContainerListener {

    private Log log;
    // For each container, all the subscribers
    private Map<SequenceContainer, Set<ContainerConsumer>> subscriptions = new ConcurrentHashMap<>();

    private XtceTmProcessor tmProcessor;

    /**
     * Creates a new ContainerRequestManager, configured to listen to the specified XtceTmProcessor.
     */
    public ContainerRequestManager(Processor proc, XtceTmProcessor tmProcessor) {
        this.tmProcessor = tmProcessor;
        log = new Log(this.getClass(), proc.getInstance());
        log.setContext(proc.getName());
        tmProcessor.setContainerListener(this);
    }

    public synchronized void subscribe(ContainerConsumer subscriber, SequenceContainer container) {
        if (container == null) {
            throw new NullPointerException("Null container");
        }
        addSubscription(subscriber, container);
    }

    public synchronized void subscribeAll(ContainerConsumer subscriber) {
        for (SequenceContainer c : tmProcessor.mdb.getSequenceContainers()) {
            addSubscription(subscriber, c);
        }
    }

    private void addSubscription(ContainerConsumer subscriber, SequenceContainer container) {
        if (!subscriptions.containsKey(container)) {
            subscriptions.put(container, new HashSet<ContainerConsumer>());
            tmProcessor.startProviding(container);
        }
        subscriptions.get(container).add(subscriber);
    }

    public synchronized void unsubscribe(ContainerConsumer subscriber, SequenceContainer container) {
        if (container == null) {
            throw new NullPointerException("Null container");
        }

        if (subscriptions.containsKey(container)) {
            Set<ContainerConsumer> subscribers = subscriptions.get(container);
            if (subscribers.remove(subscriber)) {
                if (subscribers.isEmpty()) {
                    // The following call does not do anything (yet)
                    tmProcessor.stopProviding(container);
                }
            } else {
                log.warn("Container removal requested for {} but not subscribed", container);
            }

        } else {
            log.warn("Container removal requested for {} but not subscribed", container);
        }
    }

    public synchronized void unsubscribeAll(ContainerConsumer subscriber) {
        for (Entry<SequenceContainer, Set<ContainerConsumer>> entry : subscriptions.entrySet()) {
            Set<ContainerConsumer> subscribers = entry.getValue();
            subscribers.remove(subscriber);
            if (subscribers.isEmpty()) {
                // The following call does not do anything (yet)
                tmProcessor.stopProviding(entry.getKey());
            }
        }
    }

    @Override
    public synchronized void update(List<ContainerExtractionResult> results) {
        log.trace("Getting update of {} container(s)", results.size());
        for (ContainerExtractionResult result : results) {
            SequenceContainer def = result.getContainer();
            if (!subscriptions.containsKey(def)) {
                continue;
            }
            for (ContainerConsumer subscriber : subscriptions.get(def)) {
                subscriber.processContainer(result);
            }
        }
    }

    public XtceTmProcessor getTmProcessor() {
        return tmProcessor;
    }

    public Mdb getMdb() {
        return tmProcessor.getMdb();
    }
}
