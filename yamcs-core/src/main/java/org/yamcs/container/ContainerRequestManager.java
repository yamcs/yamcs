package org.yamcs.container;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InvalidIdentification;
import org.yamcs.YProcessor;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SubscriptionArray;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;


/**
 * This manager for providing extracted parameters of a specified SequenceContainer. The reasons of having this manager are
 *  - We cannot just extend RawContainerRequestManager (previously ContainerRequestManager), because we need AlarmChecker. 
 *  - Improving ParameterRequestManager to support SequenceContainer results a mixing of Parameter and SequenceContainer  
 * 
 * This manager use the above two manager to provide both raw and extracted sequence container with minimal impact of all the
 * logics in the YProcessor. Major change:  ParameterRequestManager receives the list of ContainerExtractionResult (if exist) then
 * forwards to the ParameterConsumer.
 * 
 * @author dho
 *
 */
public class ContainerRequestManager implements ParameterConsumer {
    private Logger log;
   
    private ParameterRequestManagerImpl paramReqManager;
    private static final AtomicInteger  subscriptionCounter = new AtomicInteger(); 
    
    private ConcurrentHashMap<Integer, ParameterConsumer> consumers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<SequenceContainer, SubscriptionArray> containerToSubscription = new ConcurrentHashMap<>();
    private ConcurrentHashMap<SequenceContainer, Set<Parameter>> cachedParameterSet = new ConcurrentHashMap<>();
    private int parameterSubscriptionId;
    
    
    public ContainerRequestManager(YProcessor yproc, ParameterRequestManagerImpl paramReqManager) {
    	log = LoggerFactory.getLogger(getClass().getName() + "[" + yproc.getName() + "]");
    	this.paramReqManager = paramReqManager;
    	try {
    		this.parameterSubscriptionId = paramReqManager.addRequest(new LinkedList<Parameter>(), this);
    	} catch(InvalidIdentification e) {     		
    	}
    }
        
        
    /**
     * Create a new subscription, add a list of containers in it and register the corresponding consumer
     * 
     * @param containers
     * @param consumer
     * @return
     * @throws InvalidIdentification
     */
    public int subscribeContainers(Collection<SequenceContainer> containers, ParameterConsumer consumer) throws InvalidIdentification {
    	int subscriptionId = subscriptionCounter.incrementAndGet();    	
    	consumers.put(subscriptionId, consumer);    	
    	subscribeContainers(subscriptionId, containers);    	    	    	    	
    	return subscriptionId;
    }
    
    /**
     * Add a list of containers to an existing subscription
     * 
     * @param subscriptionId
     * @param containers
     * @return
     * @throws InvalidIdentification
     */
    public boolean subscribeContainers(int subscriptionId, Collection<SequenceContainer> containers) throws InvalidIdentification {
    	ParameterConsumer consumer = consumers.get(subscriptionId); 
    	if (consumer != null) {
    		for (SequenceContainer container: containers) {
    			__subscribeContainer(subscriptionId, container);
    		}
    		
    		return true;
    	} 
    	
    	return false;
    }
    
    
    /**
     * Add a container to an existing subscription
     * 
     * @param subscriptionId
     * @param container
     * @return
     * @throws InvalidIdentification
     */
    public boolean subscribeContainer(int subscriptionId, SequenceContainer container) throws InvalidIdentification {
    	if (consumers.containsKey(subscriptionId)) {
    		__subscribeContainer(subscriptionId, container);
    	}
    	
    	return false;
    }
    
    /**
     * Remove all the containers in the specified list from an existing subscription
     * 
     * @param subcriptionId
     * @param containers
     */
    public void unsubscribeContainer(int subcriptionId, Collection<SequenceContainer> containers) {
    	for (SequenceContainer container: containers) {
    		unsubscribeContainer(subcriptionId, container);
    	}
    }

    
    /**
     * Remove a container from an existing subscription
     * 
     * @param subscriptionId
     * @param container
     */
    public void unsubscribeContainer(int subscriptionId, SequenceContainer container) {
    	SubscriptionArray subscription = containerToSubscription.get(container);
    	if (subscription != null) {
    		subscription.remove(subscriptionId);
    		
    		if (subscription.size() == 0) { // No more subscription to the specified SequenceContainer
	        	List<Parameter> paramList = new LinkedList<>();
	        	paramList.addAll(__getParameters(container));
	        	paramReqManager.removeItemsFromRequest(parameterSubscriptionId, paramList);
    		}
    	}    	
    }

        
    /**
     * Internal processing for subscribing to a container 
     * 
     * @param subscriptionId
     * @param container
     * @throws InvalidIdentification 
     */
    private void __subscribeContainer(int subscriptionId, SequenceContainer container) throws InvalidIdentification {
    	SubscriptionArray subscription = containerToSubscription.get(subscriptionId);
    	if (subscription == null) {
    		subscription = new SubscriptionArray();
    		containerToSubscription.put(container, subscription);
    	}
    	
    	List<Parameter> paramList = new LinkedList<>();
    	paramList.addAll(__getParameters(container));
    	
    	

    	// Make a subscription to the ParameterRequestManager
    	if (subscription.size()  == 0) {
	    	paramReqManager.addItemsToRequest(parameterSubscriptionId, paramList);
    	}
    	
    	subscription.add(subscriptionId);
    }
    
    /**
     * Retrieve from the cache all the parameters contained in the specified container. If they are missing in the current cache,
     * they will be added before returned to the caller
     * 
     * @param container
     * @return
     */
    private Set<Parameter> __getParameters(SequenceContainer container) {
    	Set<Parameter> result = cachedParameterSet.get(container);
    	
    	if (result == null) {
    		result = new HashSet<>();
    		__indexParameters(container, result); 
    		cachedParameterSet.put(container, result);
    	}
    	
    	return result;
    }
    
    /**
     * Retrieve recursively all the parameters contained in the specified container
     * @param container
     * @param parameters
     */
    private void __indexParameters(SequenceContainer container, Set<Parameter> parameters) {
    	if (container != null) {
    		for (SequenceEntry se: container.getEntryList()) {
    			if  (se instanceof ContainerEntry) {
    				__indexParameters(((ContainerEntry) se).getRefContainer(), parameters);
    			} else if (se instanceof ParameterEntry) {
    				parameters.add(((ParameterEntry)se).getParameter());
    			}
    		}    		
    		__indexParameters(container.getBaseContainer(), parameters);    		
    	}
    }
    
	@Override
	public void updateItems(int subscriptionId, List<ContainerExtractionResult> containers,	List<ParameterValue> items) {
		for (ContainerExtractionResult container : containers) {
			SequenceContainer sc = container.getContainer();
			SubscriptionArray subscription = containerToSubscription.get(sc);
			if ((subscription != null) && (subscription.size() > 0)) {
				Set<Parameter> subscribed = cachedParameterSet.get(sc);
				List<ParameterValue> containerValue = new LinkedList<>();
				for (ParameterValue pv: items) {
					if (subscribed.contains(pv.getParameter())) {
						containerValue.add(pv);
					}
				}
								
				for (int subId: subscription.getArray()) {
					ParameterConsumer consumer = consumers.get(subId);
					if (consumer != null) {
						consumer.updateItems(subId, containers, containerValue);
					}
				}
			}
		}
	}	
}
