package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.Processor;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.ParameterAlarmStreamer;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtceproc.ParameterAlarmChecker;
import org.yamcs.xtceproc.XtceTmProcessor;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Makes the connection between {@link ParameterProvider} and {@link ParameterConsumer}
 * <p>
 * The consumers will subscribe to parameters, this class (we call it PRM) will subscribe itself to providers and send
 * to consumers each time the providers provide some values.
 * <p>
 * A special case is the AlgorithmManager which provides parameters that are computed based on others. The
 * AlgorithmManager is not invoking itself the PRM; instead the PRM calls it each time one of the other providers
 * sent some parameters.
 * 
 * <p>
 * For the consumers there are two types of subscriptions: - subscribe all - subscribe to a set. Both types have an
 * unique id associated but different methods work with them.
 * <p>
 * 
 */
public class ParameterRequestManager extends AbstractService implements ParameterListener {
    Log log;

    static final String REALTIME_ALARM_SERVER = "alarms_realtime";
    // Maps the parameters to the request(subscription id) in which they have been asked
    private ConcurrentHashMap<Parameter, SubscriptionArray> param2RequestMap = new ConcurrentHashMap<>();

    // Maps the request (subscription id) to the consumer
    private Map<Integer, ParameterConsumer> request2ParameterConsumerMap = new ConcurrentHashMap<>();

    // contains subscribe all
    private SubscriptionArray subscribeAll = new SubscriptionArray();

    private ParameterAlarmChecker alarmChecker;
    private AlgorithmManager algorithmManager;

    private Map<Class<?>, ParameterProvider> parameterProviders = new LinkedHashMap<>();

    private static AtomicInteger lastSubscriptionId = new AtomicInteger();
    public final Processor processor;

    // if all parameter shall be subscribed/processed
    private boolean shouldSubcribeAllParameters = false;

    AlarmServer<Parameter, ParameterValue> parameterAlarmServer;
    Map<DataSource, SoftwareParameterManager> spm = new HashMap<>();
    ParameterCache parameterCache;
    ParameterCacheConfig cacheConfig;
    LastValueCache lastValueCache;

    /**
     * Creates a new ParameterRequestManager, configured to listen to the specified XtceTmProcessor.
     */
    public ParameterRequestManager(Processor yproc, XtceTmProcessor tmProcessor) throws ConfigurationException {
        this.processor = yproc;
        log = new Log(getClass(), yproc.getInstance());
        log.setContext(yproc.getName());
        cacheConfig = yproc.getPameterCacheConfig();
        shouldSubcribeAllParameters = yproc.isSubscribeAll();

        this.lastValueCache = yproc.getLastValueCache();

        tmProcessor.setParameterListener(this);
        addParameterProvider(tmProcessor);
        if (yproc.hasAlarmChecker()) {
            alarmChecker = new ParameterAlarmChecker(this, yproc.getProcessorData());
        }
        if (yproc.hasAlarmServer()) {
            parameterAlarmServer = new AlarmServer<>(yproc.getInstance(), yproc.getTimer());
            alarmChecker.enableServer(parameterAlarmServer);
        }

        if (cacheConfig.enabled) {
            parameterCache = new ArrayParameterCache(yproc.getInstance(), cacheConfig);
        }
    }

    public void addParameterProvider(ParameterProvider parameterProvider) {
        if (parameterProviders.containsKey(parameterProvider.getClass())) {
            log.warn("Ignoring duplicate parameter provider of type {}", parameterProvider.getClass());
        } else {
            log.debug("Adding parameter provider: {}", parameterProvider.getClass());
            parameterProvider.setParameterListener(this);
            parameterProviders.put(parameterProvider.getClass(), parameterProvider);
        }
    }

    /**
     * This is called after all the parameter providers have been added but before the start.
     */
    public void init() {
        if (shouldSubcribeAllParameters) {
            for (ParameterProvider prov : parameterProviders.values()) {
                prov.startProvidingAll();
            }
        } else if (parameterAlarmServer != null) { // at least get all that have alarms
            for (Parameter p : processor.getXtceDb().getParameters()) {
                if (p.getParameterType() != null && p.getParameterType().hasAlarm()) {
                    try {
                        subscribeToProviders(p);
                    } catch (NoProviderException e) {
                        log.warn("No provider found for parameter {} which has alarms", p.getQualifiedName());
                    }
                }
            }
        }
    }

    /**
     * called by a consumer to subscribe to all parameters
     */
    public int subscribeAll(ParameterConsumer consumer) {
        int id = lastSubscriptionId.incrementAndGet();
        log.debug("new subscribeAll with subscriptionId {}", id);
        if (subscribeAll.isEmpty()) {
            for (ParameterProvider provider : parameterProviders.values()) {
                provider.startProvidingAll();
            }
        }
        subscribeAll.add(id);
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
        return subscribeAll.remove(subscriptionId);
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
        final ParameterConsumer consumer = request2ParameterConsumerMap.get(subscriptionId);
        if (consumer == null) {
            throw new InvalidRequestIdentification("no such subscriptionID", subscriptionId);
        }
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
        final ParameterConsumer consumer = request2ParameterConsumerMap.get(subscriptionId);
        if (consumer == null) {
            throw new InvalidRequestIdentification("no such subscriptionID", subscriptionId);
        }
        subscribeToProviders(paraList);
        for (int i = 0; i < paraList.size(); i++) {
            addItemToRequest(subscriptionId, paraList.get(i));
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
        if (!param2RequestMap.containsKey(para)) {
            // this parameter is not requested by any other request
            if (param2RequestMap.putIfAbsent(para, new SubscriptionArray()) == null) {
                if (alarmChecker != null) {
                    alarmChecker.parameterSubscribed(para);
                }
            }
        }
        SubscriptionArray al_req = param2RequestMap.get(para);
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
        if (shouldSubcribeAllParameters) {
            return;
        }
        boolean providerFound = false;

        for (ParameterProvider provider : parameterProviders.values()) {
            if (provider.canProvide(param)) {
                providerFound = true;
                provider.startProviding(param);
            }
        }
        if (!providerFound) {
            throw new NoProviderException("No provider found for " + param);
        }

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
        if (shouldSubcribeAllParameters) {
            return;
        }
        for (Parameter p : itemList) {
            subscribeToProviders(p);
        }
    }

    /**
     * returns a parameter based on fully qualified name
     * 
     * @param fqn
     * @return
     * @throws InvalidIdentification
     */
    public Parameter getParameter(String fqn) throws InvalidIdentification {
        return getParameter(NamedObjectId.newBuilder().setName(fqn).build());
    }

    /**
     * @param paraId
     * @return the corresponding parameter definition for a IntemIdentification
     * @throws InvalidIdentification
     *             in case no provider knows of this parameter.
     */
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        for (ParameterProvider provider : parameterProviders.values()) {
            if (provider.canProvide(paraId)) {
                return provider.getParameter(paraId);
            }
        }
        throw new InvalidIdentification(paraId);
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
    @Override
    public void update(ParameterValueList currentDelivery) {
        log.trace("ParamRequestManager.update with {} parameters", currentDelivery.size());

        if (alarmChecker != null) {
            alarmChecker.performAlarmChecking(currentDelivery, currentDelivery.iterator());
        }

        Iterator<ParameterValue> tailIt = currentDelivery.tailIterator();
        if (algorithmManager != null) {
            algorithmManager.updateDelivery(currentDelivery);
        }

        if (alarmChecker != null) {
            alarmChecker.performAlarmChecking(currentDelivery, tailIt);
        }

        // build the customised lists for the subscribers and send it to them
        HashMap<Integer, ArrayList<ParameterValue>> subscription = new HashMap<>();
        updateSubscription(subscription, currentDelivery);

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
        if (parameterCache != null) {
            parameterCache.update(currentDelivery);
        }
        lastValueCache.update(currentDelivery);
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
        for (int id : subscribeAll.getArray()) {
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

    void checkAlarms(ParameterValueList pvlist, Iterator<ParameterValue> it) {
        if (alarmChecker != null) {
            try {
                alarmChecker.performAlarmChecking(pvlist, it);
            } catch (Exception e) {
                log.error("Error when performing alarm checking ", e);
            }
        }
    }
    /**
     * 
     * @return the SoftwareParameterManager associated to the DataSource or null if not configured
     */
    public SoftwareParameterManager getSoftwareParameterManager(DataSource ds) {
        return spm.get(ds);
    }

    @SuppressWarnings("unchecked")
    public <T extends ParameterProvider> T getParameterProvider(Class<T> type) {
        return (T) parameterProviders.get(type);
    }

    public ParameterAlarmChecker getAlarmChecker() {
        return alarmChecker;
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

    public AlarmServer<Parameter, ParameterValue> getAlarmServer() {
        return parameterAlarmServer;
    }

    public boolean hasParameterCache() {
        return parameterCache != null;
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

    /**
     * Get all the values from cache for a specific parameters
     * 
     * The parameter are returned in descending order (newest parameter is returned first). Note that you can only all
     * this function if the {@link #hasParameterCache()} returns true.
     * 
     * @param param
     * @return
     */
    public List<ParameterValue> getValuesFromCache(Parameter param) {
        return parameterCache.getAllValues(param);
    }

    public ParameterCache getParameterCache() {
        return parameterCache;
    }

    public Object getXtceDb() {
        return processor.getXtceDb();
    }

    @Override
    protected void doStart() {
        if (parameterAlarmServer != null) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(processor.getInstance());
            Stream s = ydb.getStream(REALTIME_ALARM_SERVER);
            if (s == null) {
                notifyFailed(new ConfigurationException("Cannot find a stream named '" + REALTIME_ALARM_SERVER + "'"));
                return;
            }
            parameterAlarmServer.addAlarmListener(new ParameterAlarmStreamer(s));
            parameterAlarmServer.startAsync();
        }

        notifyStarted();
    }

    @Override
    protected void doStop() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.stopAsync();
        }
        notifyStopped();
    }

    public LastValueCache getLastValueCache() {
        return lastValueCache;
    }

    /**
     * Register a {@link SoftwareParameterManager} for the given {@link DataSource}. Throws an
     * {@link IllegalStateException} if there is already registered a parameter manager for this data source.
     * 
     * @param ds
     * @param swParameterManager
     */
    public void addSoftwareParameterManager(DataSource ds, SoftwareParameterManager swParameterManager) {
        if (spm.containsKey(ds)) {
            throw new IllegalStateException("There is already a soft parameter manager for " + ds);
        }
        spm.put(ds, swParameterManager);
    }

    public void setAlgortihmManager(AlgorithmManager algmgr) {
        if (algorithmManager != null) {
            throw new IllegalStateException("There is already one algorithm manager");
        }
        algorithmManager = algmgr;
    }
}
