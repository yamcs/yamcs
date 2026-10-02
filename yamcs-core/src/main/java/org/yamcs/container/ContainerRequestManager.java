package org.yamcs.container;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ContainerListener;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.XtceTmProcessor;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;

/**
 * Keeps track of the subscribers to the containers of a processor.
 */
public class ContainerRequestManager implements ContainerListener {

    private Log log;
    // For each container, the subscribers to that specific container.
    private Map<SequenceContainer, CopyOnWriteArrayList<ContainerConsumer>> subscriptions = new ConcurrentHashMap<>();
    // Subscribers to all containers
    private CopyOnWriteArrayList<ContainerConsumer> allSubscribers = new CopyOnWriteArrayList<>();

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
        if (allSubscribers.addIfAbsent(subscriber)) {
            for (SequenceContainer c : tmProcessor.mdb.getSequenceContainers()) {
                tmProcessor.startProviding(c);
            }
        }
    }

    private void addSubscription(ContainerConsumer subscriber, SequenceContainer container) {
        var subscribers = subscriptions.computeIfAbsent(container, k -> new CopyOnWriteArrayList<>());
        if (subscribers.isEmpty()) {
            tmProcessor.startProviding(container);
        }
        subscribers.addIfAbsent(subscriber);
    }

    public synchronized void unsubscribe(ContainerConsumer subscriber, SequenceContainer container) {
        if (container == null) {
            throw new NullPointerException("Null container");
        }

        if (subscriptions.containsKey(container)) {
            List<ContainerConsumer> subscribers = subscriptions.get(container);
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

    /**
     * Removes a subscription made with {@link #subscribeAll(ContainerConsumer)}. Subscriptions to individual
     * containers are not affected.
     */
    public synchronized void unsubscribeAll(ContainerConsumer subscriber) {
        allSubscribers.remove(subscriber);
    }

    @Override
    public synchronized void update(ContainerProcessingResult cpr) {
        var results = cpr.getContainerResult();

        log.trace("Getting update of {} container(s)", results.size());
        for (ContainerExtractionResult result : results) {
            for (ContainerConsumer subscriber : allSubscribers) {
                subscriber.processContainer(cpr, result);
            }
            List<ContainerConsumer> subscribers = subscriptions.get(result.getContainer());
            if (subscribers == null) {
                continue;
            }
            for (ContainerConsumer subscriber : subscribers) {
                // the all subscribers have already received it
                if (!allSubscribers.contains(subscriber)) {
                    subscriber.processContainer(cpr, result);
                }
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
