package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;

/**
 * Distributes parameters from {@link ParameterProcessorManager} to {@link ParameterConsumer}
 * <p>
 * The consumers will subscribe to parameters, this class (we call it PRM) will subscribe itself to providers
 * and send to consumers each time the providers provide some values.
 * 
 */
public class ParameterRequestManager {
    Log log;

    // Maps the parameters to the request(subscription id) in which they have been asked
    private ConcurrentHashMap<Parameter, SubscriptionArray> param2RequestMap = new ConcurrentHashMap<>();

    // Maps the request (subscription id) to the consumer
    private Map<Integer, ParameterConsumer> request2ParameterConsumerMap = new ConcurrentHashMap<>();

    // contains subscribe all
    private SubscriptionArray subscribeAllConsumers = new SubscriptionArray();

    private static AtomicInteger lastSubscriptionId = new AtomicInteger();

    public final Processor processor;

    LastValueCache lastValueCache;
    ParameterProcessorManager ppm;

    /**
     * Creates a new ParameterRequestManager, configured to listen to the specified XtceTmProcessor.
     */
    public ParameterRequestManager(ParameterProcessorManager ppm) throws ConfigurationException {
        this.processor = ppm.processor;
        this.ppm = ppm;
        log = new Log(getClass(), processor.getInstance());
        log.setContext(processor.getName());
        this.lastValueCache = processor.getLastValueCache();
    }

    /**
     * called by a consumer to subscribe to all parameters
     */
    public int subscribeAll(ParameterConsumer consumer) {
        int id = lastSubscriptionId.incrementAndGet();
        log.debug("new subscribeAll with subscriptionId {}", id);
        ppm.subscribeAllToProviders();

        subscribeAllConsumers.add(id);
        request2ParameterConsumerMap.put(id, consumer);
        return id;
    }

    /**
     * called by a consumer to remove a "subscribe all" subscription
     * 
     * return true of the subscription has been removed or false if it was not there
     * 
     * @param subscriptionId
     * @return
     */
    public boolean unsubscribeAll(int subscriptionId) {
        return subscribeAllConsumers.remove(subscriptionId);
    }

    /**
     * Called by a consumer to create a new subscription
     * 
     * @param paraList
     * @param tpc
     * @return the newly created subscription id
     */
    public int addRequest(final Collection<Parameter> paraList, final ParameterConsumer tpc) {
        final int id = lastSubscriptionId.incrementAndGet();
        log.debug("new request with subscriptionId {} with {} items", id, paraList.size());
        subscribeToProviders(paraList);

        for (Parameter p : paraList) {
            log.trace("adding to subscriptionID: {} item:{} ", id, p.getQualifiedName());
            addItemToRequest(id, p);
        }

        request2ParameterConsumerMap.put(id, tpc);
        return id;
    }

    /**
     * Called by a consumer to create a subscription with one parameter
     * 
     * @param para
     * @param tpc
     * @return the newly created subscription id
     */
    public int addRequest(final Parameter para, final ParameterConsumer tpc) {
        final int id = lastSubscriptionId.incrementAndGet();
        log.debug("new request with subscriptionId {} for parameter: {}", id, para.getQualifiedName());
        subscribeToProviders(para);
        addItemToRequest(id, para);
        request2ParameterConsumerMap.put(id, tpc);

        return id;
    }

    /**
     * Called by a consumer to create request with a given id. This is called when switching processors, the id is
     * coming from the other processor.
     * 
     * @param subscriptionId
     *            - subscription id
     * @param paraList
     * @param tpc
     */
    public void addRequest(int subscriptionId, List<Parameter> paraList, ParameterConsumer tpc) {
        subscribeToProviders(paraList);
        for (int i = 0; i < paraList.size(); i++) {
            log.trace("creating subscriptionID:{} with item:{}", subscriptionId, paraList.get(i));
            addItemToRequest(subscriptionId, paraList.get(i));
        }
        request2ParameterConsumerMap.put(subscriptionId, tpc);
    }

    /**
     * Called by a consumer to add a parameter to an existing subscription.
     * 
     * @param subscriptionId
     * @param para
     */
    public void addItemsToRequest(final int subscriptionId, final Parameter para) throws InvalidRequestIdentification {
        log.debug("adding to subscriptionID {}: items: {} ", subscriptionId, para.getName());
        verifySubscriptionId(subscriptionId);
        subscribeToProviders(para);
        addItemToRequest(subscriptionId, para);
    }

    /**
     * Called by a consumer to add parameters to an existing subscription.
     * 
     * @param subscriptionId
     * @param paraList
     *            list of parameters that are added to the subscription
     * @throws InvalidRequestIdentification
     */
    public void addItemsToRequest(final int subscriptionId, final List<Parameter> paraList)
            throws InvalidRequestIdentification {
        log.debug("adding to subscriptionID {}: {} items ", subscriptionId, paraList.size());
        verifySubscriptionId(subscriptionId);

        subscribeToProviders(paraList);
        for (int i = 0; i < paraList.size(); i++) {
            addItemToRequest(subscriptionId, paraList.get(i));
        }
    }

    private void verifySubscriptionId(int subscriptionId) throws InvalidRequestIdentification {
        if (!request2ParameterConsumerMap.containsKey(subscriptionId)) {
            throw new InvalidRequestIdentification("no such subscriptionID", subscriptionId);
        }
    }

    /**
     * Called by a consumer to remove a parameter from a subscription.
     * <p>
     * If the parameter is not part of the subscription, the operation will have no effect.
     * 
     * @param subscriptionID
     * @param param
     */
    public void removeItemsFromRequest(int subscriptionID, Parameter param) {
        removeItemFromRequest(subscriptionID, param);
    }

    /**
     * Called by a consumer to remove parameters from a subscription.
     * <p>
     * Any parameter that is not in the subscription will be ignored.
     * 
     * @param subscriptionID
     * @param paraList
     */
    public void removeItemsFromRequest(int subscriptionID, List<Parameter> paraList) {
        for (int i = 0; i < paraList.size(); i++) {
            removeItemFromRequest(subscriptionID, paraList.get(i));
        }
    }

    private void addItemToRequest(int id, Parameter para) {
        SubscriptionArray al_req = param2RequestMap.computeIfAbsent(para, k -> new SubscriptionArray());
        al_req.add(id);
    }

    private void removeItemFromRequest(int subscriptionId, Parameter para) {
        if (param2RequestMap.containsKey(para)) { // is there really any request associated to this parameter?
            SubscriptionArray al_req = param2RequestMap.get(para);
            // remove the subscription from the list of this parameter
            if (al_req.remove(subscriptionId)) {
                /*
                 * Don't remove the al_req from the map and
                 * don't ask provider to stop providing
                 * because it is not thread safe (maybe another thread just asked to start providing after seeing that
                 * the list is empty
                 * if(al_req.isEmpty()) { //nobody wants this parameter anymore
                 * if(!cacheAll) provider.stopProviding(para);
                 * }
                 */
            } else {
                log.warn("parameter removal requested for {} but not part of subscription {}", para, subscriptionId);
            }
        } else {
            log.warn("parameter removal requested for {} but not subscribed", para);
        }
    }

    /**
     * Removes all the parameters from a subscription and returns them into an List.
     * 
     */
    public List<Parameter> removeRequest(int subscriptionId) {
        log.debug("removing request for subscriptionId {}", subscriptionId);
        // It's a bit annoying that we have to loop through all the parameters to find the ones that
        // are relevant for this request. We could keep track of an additional map.
        ArrayList<Parameter> result = new ArrayList<>();
        // loop through all the parameter definitions
        // find all the subscriptions with the requested subscriptionId and add their corresponding
        // itemId to the list.
        Iterator<Map.Entry<Parameter, SubscriptionArray>> it = param2RequestMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Parameter, SubscriptionArray> m = it.next();
            Parameter param = m.getKey();
            SubscriptionArray al_req = m.getValue();
            if (al_req.remove(subscriptionId)) {
                result.add(param);
            }
            if (al_req.isEmpty()) { // nobody wants this parameter anymore
                /*
                 * if(!cacheAll) { commented out because not thread safe
                 * getProvider(param).stopProviding(param);
                 * }
                 */
            }
        }
        request2ParameterConsumerMap.remove(subscriptionId);
        return result;
    }

    private void subscribeToProviders(Parameter param) throws NoProviderException {
        ppm.subscribeToProviders(param);
    }

    /**
     * Called to subscribe to providers for the given parameters.
     * <p>
     * Unless already subscribed, the PRM will start delivering from now on those parameters.
     * 
     * 
     * @param itemList
     */
    public void subscribeToProviders(Collection<Parameter> itemList) {
        ppm.subscribeToProviders(itemList);
    }

    /**
     * returns a parameter based on fully qualified name
     * 
     * @param fqn
     * @return
     * @throws InvalidIdentification
     */
    public Parameter getParameter(String fqn) throws InvalidIdentification {
        return ppm.getParameter(fqn);
    }

    /**
     * @param paraId
     * @return the corresponding parameter definition for a IntemIdentification
     * @throws InvalidIdentification
     *             in case no provider knows of this parameter.
     */
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        return ppm.getParameter(paraId);
    }

    /**
     * Called by a provider with a list of provided parameters called "current delivery".
     * <p>
     * The PRM will take ownership of the current delivery (and modify it!).
     * 
     * <p>
     * The following steps are performed (in the provider thread, possible by multiple providers in parallel!):
     * <ol>
     * <li>Run algorithms. All results from algorithms are also added to the list.</li>
     * <li>Check alarms.</li>
     * <li>Distribute to subscribers.</li>
     * <li>Add to parameter cache (if enabled).</li>
     * <li>Add to the last value cache.</li>
     * </ol>
     * 
     * 
     */
    public void update(ParameterValueList pvlist) {
        // build the customised lists for the subscribers and send it to them
        HashMap<Integer, ArrayList<ParameterValue>> subscription = new HashMap<>();
        updateSubscription(subscription, pvlist);

        for (Map.Entry<Integer, ArrayList<ParameterValue>> entry : subscription.entrySet()) {
            Integer subscriptionId = entry.getKey();

            ArrayList<ParameterValue> al = entry.getValue();
            ParameterConsumer consumer = request2ParameterConsumerMap.get(subscriptionId);
            if (consumer == null) {
                log.warn("subscriptionId {} appears in the delivery list, but there is no consumer for it",
                        subscriptionId);
            } else {
                consumer.updateItems(subscriptionId, al);
            }
        }
    }

    /**
     * adds the passed parameters to the subscription
     * 
     * @param subscription
     * @param currentDelivery
     */
    private void updateSubscription(HashMap<Integer, ArrayList<ParameterValue>> subscription,
            Collection<ParameterValue> currentDelivery) {
        if (currentDelivery == null) {
            return;
        }

        for (Iterator<ParameterValue> it = currentDelivery.iterator(); it.hasNext();) {
            ParameterValue pv = it.next();
            Parameter pDef = pv.getParameter();
            SubscriptionArray cowal = param2RequestMap.get(pDef);
            // now walk through the requests and add this item to their delivery list
            if (cowal == null) {
                continue;
            }

            for (int s : cowal.getArray()) {
                ArrayList<ParameterValue> al = subscription.get(s);
                if (al == null) {
                    al = new ArrayList<>();
                    subscription.put(s, al);
                }
                al.add(pv);
            }
        }

        // update the subscribeAll subscriptions
        for (int id : subscribeAllConsumers.getArray()) {
            ArrayList<ParameterValue> al = subscription.get(id);

            if (al == null) {
                al = new ArrayList<>();
                subscription.put(id, al);
            }

            for (ParameterValue pv : currentDelivery) {
                al.add(pv);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Subscription list:\n");
        for (Parameter param : param2RequestMap.keySet()) {
            sb.append(param);
            sb.append("requested by [");
            SubscriptionArray al_req = param2RequestMap.get(param);
            for (int id : al_req.getArray()) {
                sb.append(id);
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    /**
     * Returns the last known value for each parameter.
     * 
     * @param plist
     * @return
     */
    public List<ParameterValue> getValuesFromCache(Collection<Parameter> plist) {
        List<ParameterValue> al = new ArrayList<>(plist.size());
        for (Parameter p : plist) {
            ParameterValue pv = lastValueCache.getValue(p);
            if (pv != null) {
                al.add(pv);
            }
        }
        return al;
    }

    /**
     * Get the last value from cache for a specific parameters
     * 
     * @param param
     * @return
     */
    public ParameterValue getLastValueFromCache(Parameter param) {
        return lastValueCache.getValue(param);
    }
}
