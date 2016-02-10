package org.yamcs.container;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.YProcessor;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ContainerListener;
import org.yamcs.xtceproc.XtceTmProcessor;


/**
 * Keeps track of the subscribers to the containers of a Yamcs Channel.
 */
public class ContainerRequestManager implements ContainerListener {

    private Logger log;
    // For each container, all the subscribers
    private Map<SequenceContainer, Set<ContainerConsumer>> subscriptions = new ConcurrentHashMap<SequenceContainer, Set<ContainerConsumer>>();    

    private XtceTmProcessor tmProcessor;

    /**
     * Creates a new ContainerRequestManager, configured to listen to a newly
     * created XtceTmProcessor.
     */
    public ContainerRequestManager(YProcessor yamcsChannel) {
        this(yamcsChannel, new XtceTmProcessor(yamcsChannel, null));
    }

    /**
     * Creates a new ContainerRequestManager, configured to listen to the
     * specified XtceTmProcessor.
     */
    public ContainerRequestManager(YProcessor yamcsChannel, XtceTmProcessor tmProcessor) {
        this.tmProcessor = tmProcessor;
        log = LoggerFactory.getLogger(getClass().getName() + "[" + yamcsChannel.getName() + "]");
        tmProcessor.setContainerListener(this);
    }

    public synchronized void subscribe(ContainerConsumer subscriber, SequenceContainer container) {
        if (container == null) {
            throw new NullPointerException("Null contaienr");
        }
        addSubscription(subscriber, container);
    }

    public synchronized void subscribeAll(ContainerConsumer subscriber) {
        for (SequenceContainer c : tmProcessor.xtcedb.getSequenceContainers()) {
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
            throw new NullPointerException("null container");
        }

        if (subscriptions.containsKey(container)) {
            Set<ContainerConsumer> subscribers = subscriptions.get(container);
            if (subscribers.remove(subscriber)) {
                if(subscribers.isEmpty()) {
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
            if (!subscriptions.containsKey(def)) continue;
            for (ContainerConsumer subscriber : subscriptions.get(def)) {               
                subscriber.processContainer(result);
            }
        }
    }

    public XtceTmProcessor getTmProcessor() {
        return tmProcessor;
    }

    public XtceDb getXtceDb() {
        return tmProcessor.getXtceDb();
    }
}
