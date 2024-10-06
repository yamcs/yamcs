package org.yamcs.parameter;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.ParameterAlarmServer;
import org.yamcs.alarms.ParameterAlarmStreamer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ParameterAlarmChecker;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.XtceTmProcessor;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Makes the connection between {@link ParameterProvider} and {@link ParameterProcessor}
 * <p>
 * Each parameter processor will get the {@link ProcessingData} delivery (those containing parameters it is interested
 * into) and can further add parameters to it.
 * <p>
 * The AlgorithmManager is a parameter processor and is added first in the list.
 * <p>
 * After each parameter processor is called, the alarm manager (if enabled) will check the newly added parameters
 * 
 * 
 */
public class ParameterProcessorManager extends AbstractService implements ParameterProcessor {
    Log log;
    static final String REALTIME_ALARM_SERVER = "alarms_realtime";
    ParameterProcessor[] parameterProcessors = new ParameterProcessor[10];

    // Maps the parameters to the request(subscription id) in which they have been asked
    private ConcurrentHashMap<Parameter, BitSet> param2SubscriptionMap = new ConcurrentHashMap<>();

    // contains subscribe all
    private BitSet subscribeAll = new BitSet();

    private Map<Class<?>, ParameterProvider> parameterProviders = new LinkedHashMap<>();

    public final Processor processor;

    ParameterCache parameterCache;
    ParameterCacheConfig cacheConfig;
    LastValueCache lastValueCache;

    // if all parameter shall be subscribed/processed
    private boolean shouldSubcribeAllParameters = false;
    private boolean subscribedAllParameters = false;

    AlarmServer<Parameter, ParameterValue> parameterAlarmServer;
    Map<DataSource, SoftwareParameterManager> spm = new HashMap<>();

    private ParameterAlarmChecker alarmChecker;
    ParameterRequestManager prm;

    /**
     * Creates a new ParameterRequestManager, configured to listen to the specified XtceTmProcessor.
     */
    public ParameterProcessorManager(Processor proc, XtceTmProcessor tmProcessor) throws ConfigurationException {
        this.processor = proc;
        log = new Log(getClass(), proc.getInstance());
        log.setContext(proc.getName());
        cacheConfig = proc.getPameterCacheConfig();
        shouldSubcribeAllParameters = proc.isSubscribeAll();

        this.lastValueCache = proc.getLastValueCache();

        tmProcessor.setParameterProcessor(this);
        addParameterProvider(tmProcessor);
        if (proc.hasAlarmChecker()) {
            alarmChecker = new ParameterAlarmChecker(this, proc.getProcessorData());
        }
        if (proc.hasAlarmServer()) {
            parameterAlarmServer = new ParameterAlarmServer(proc.getInstance(), proc.getConfig(), proc.getTimer());
            alarmChecker.enableServer(parameterAlarmServer);
        }

        if (cacheConfig.enabled) {
            parameterCache = new ArrayParameterCache(proc.getInstance(), cacheConfig);

            // Populate any initial values
            var pvs = proc.getLastValueCache().getValues();
            parameterCache.update(pvs);
        }
        prm = new ParameterRequestManager(this);
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
            for (Parameter p : processor.getMdb().getParameters()) {
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

    public void addParameterProvider(ParameterProvider parameterProvider) {
        if (parameterProviders.containsKey(parameterProvider.getClass())) {
            log.warn("Ignoring duplicate parameter provider of type {}", parameterProvider.getClass());
        } else {
            log.debug("Adding parameter provider: {}", parameterProvider.getClass());
            parameterProvider.setParameterProcessor(this);
            parameterProviders.put(parameterProvider.getClass(), parameterProvider);
        }
    }

    public int subscribe(final Collection<Parameter> paraList, final ParameterProcessor paramProcessor) {
        int id = allocateProcessorId(paramProcessor);
        log.debug("new request with subscriptionId {} with {} items for {}", id, paraList.size(),
                paramProcessor.getClass());
        subscribeToProviders(paraList);

        for (Parameter p : paraList) {
            log.trace("adding to subscriptionID: {} item:{} ", id, p.getQualifiedName());
            addItemToSubscription(id, p);
        }

        return id;
    }

    private void addItemToSubscription(int id, Parameter para) {
        BitSet bitset = param2SubscriptionMap.computeIfAbsent(para, k -> new BitSet());
        bitset.set(id);
    }

    public int subscribeAll(ParameterProcessor processor) {
        int id = allocateProcessorId(processor);
        log.debug("new subscribeAll with subscriptionId {}", id);

        subscribeAll.set(id);
        return id;
    }

    public void unsubscribeAll(int subscriptionId) {
        subscribeAll.clear(subscriptionId);
        removeSubscriptionId(subscriptionId);
    }

    private synchronized int allocateProcessorId(ParameterProcessor processor) {
        for (int i = 0; i < parameterProcessors.length; i++) {
            if (parameterProcessors[i] == null) {
                parameterProcessors[i] = processor;
                return i;
            }
        }
        int n = parameterProcessors.length;
        parameterProcessors = Arrays.copyOf(parameterProcessors, n + 10);
        parameterProcessors[n] = processor;
        return n;
    }

    private synchronized void removeSubscriptionId(int id) {
        parameterProcessors[id] = null;
    }

    public void unsubscribe(int subscriptionId) {
        param2SubscriptionMap.values().forEach(bitset -> bitset.clear(subscriptionId));
        removeSubscriptionId(subscriptionId);
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

    @Override
    public void process(ProcessingData processingData) {
        ParameterValueList pvlist = processingData.getTmParams();
        log.trace("Received TM data with {} parameters", pvlist.size);
        if (alarmChecker != null) {
            alarmChecker.performAlarmChecking(processingData, pvlist.iterator());
        }
        BitSet bitset = new BitSet();
        bitset.or(subscribeAll);

        for (ParameterValue pv : pvlist) {
            BitSet bitset1 = param2SubscriptionMap.get(pv.getParameter());
            if (bitset1 != null) {
                bitset.or(bitset1);
            }
        }
        // at this point bitset contains all the subscriptions with parameters from the original delivery.
        // as we run the delivery through the processors, new parameters may be created and so new subscriptions might
        // be added to the set. We do not want to deliver twice for the same subscription though.
        // In particular, the subscribe all processors will only get called once, so they will not be called again if
        // other parameters have been added to delivery
        // (the algorithm manager handles that by doing its internal parameter dependency management)
        boolean finished = false;
        int loopCount = 1;
        while (!finished) {
            finished = true;
            Iterator<ParameterValue> tailIt = pvlist.tailIterator();

            for (int id = bitset.nextSetBit(0); id != -1; id = bitset.nextSetBit(id + 1)) {
                finished = false;
                sendToProcessor(parameterProcessors[id], processingData);
            }

            // check the new parameters added in the loop above
            BitSet bitset1 = new BitSet();
            while (tailIt.hasNext()) {
                ParameterValue pv = tailIt.next();
                BitSet bitset2 = param2SubscriptionMap.get(pv.getParameter());
                if (bitset2 != null) {
                    bitset1.or(bitset2);
                }
            }

            // remove the subscriptions for which the data has already been sent
            // - even though without the extra parameters! because we don't like cyclic dependencies
            // cyclic dependencies could be detected by checking bitset1 intersection with bitset
            bitset1.andNot(bitset);

            bitset = bitset1;
        }

        prm.update(pvlist);

        if (parameterCache != null) {
            parameterCache.update(pvlist);
        }
        lastValueCache.addAll(pvlist);
    }

    // sends the parameter to processor
    private void sendToProcessor(ParameterProcessor paramProcessor, ProcessingData processingData) {
        log.trace("Sending data to parameter processor {}", paramProcessor.getClass());
        ParameterValueList pvlist = processingData.getTmParams();

        Iterator<ParameterValue> tailIt = pvlist.tailIterator();

        try {
            paramProcessor.process(processingData);
        } catch (Exception e) {
            log.error("Parameter processor exception ", e);
        }
        if (alarmChecker != null) {
            alarmChecker.performAlarmChecking(processingData, tailIt);
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

    public AlarmServer<Parameter, ParameterValue> getAlarmServer() {
        return parameterAlarmServer;
    }

    public boolean hasParameterCache() {
        return parameterCache != null;
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

    void subscribeAllToProviders() {
        if (!subscribedAllParameters) {
            for (ParameterProvider provider : parameterProviders.values()) {
                provider.startProvidingAll();
            }
            subscribedAllParameters = true;
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

    void subscribeToProviders(Parameter param) throws NoProviderException {
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

    public ParameterRequestManager getParameterRequestManager() {
        return prm;
    }
}
