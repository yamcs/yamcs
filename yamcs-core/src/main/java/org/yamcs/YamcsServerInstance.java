package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.Listener;
import com.google.common.util.concurrent.Service.State;

/**
 * Represents a Yamcs instance together with the instance specific services
 * 
 * @author nm
 *
 */
public class YamcsServerInstance {
    private String instanceName;
    Logger log;
    TimeService timeService;
    private CrashHandler crashHandler;
    // instance specific services
    List<ServiceWithConfig> serviceList;
    private XtceDb xtceDb;
    private InstanceState state = InstanceState.OFFLINE;

    private Set<InstanceStateListener> stateListeners = new CopyOnWriteArraySet<>();

    private Exception initFailed;

    private AbstractService guavaService;

    YamcsServerInstance(String instance) {
        this.instanceName = instance;
        log = LoggingUtils.getLogger(YamcsServer.class, instance);
        loadTimeService();

        guavaService = new AbstractService() {

            @Override
            protected void doStart() {
                // Note that instance should already be init-ed before attempting guava start.
                YamcsServer.startServices(serviceList);
                notifyStarted();
            }

            @Override
            protected void doStop() {
                for (int i = serviceList.size() - 1; i >= 0; i--) {
                    ServiceWithConfig swc = serviceList.get(i);
                    Service s = swc.service;
                    s.stopAsync();
                    try {
                        s.awaitTerminated(YamcsServer.SERVICE_STOP_GRACE_TIME, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        log.error("Service {} did not stop in {} seconds", s.getClass().getName(),
                                YamcsServer.SERVICE_STOP_GRACE_TIME);
                    } catch (IllegalStateException e) {
                        log.error("Service {} was in a bad state: {}", s.getClass().getName(), e.getMessage());
                    }
                }
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(instanceName);
                ydb.close();
                YarchDatabase.removeInstance(instanceName);
                notifyStopped();
            }
        };
        guavaService.addListener(new Listener() {

            @Override
            public void starting() {
                state = InstanceState.STARTING;
                stateListeners.forEach(InstanceStateListener::starting);
            }

            @Override
            public void running() {
                state = InstanceState.RUNNING;
                stateListeners.forEach(InstanceStateListener::running);
            }

            @Override
            public void stopping(State from) {
                state = InstanceState.STOPPING;
                System.out.println("stopping " + stateListeners.size());
                stateListeners.forEach(l -> l.stopping());
            }

            @Override
            public void terminated(State from) {
                state = InstanceState.OFFLINE;
                stateListeners.forEach(InstanceStateListener::offline);
            }

            @Override
            public void failed(State from, Throwable failure) {
                state = InstanceState.FAILED;
                stateListeners.forEach(l -> l.failed(failure));
            }
        }, MoreExecutors.directExecutor());
    }

    void init() throws IOException {
        initFailed = null;
        state = InstanceState.INITIALIZING;
        stateListeners.forEach(InstanceStateListener::initializing);
        YConfiguration conf = YConfiguration.getConfiguration("yamcs." + instanceName);
        if (conf.containsKey("crashHandler")) {
            crashHandler = YamcsServer.loadCrashHandler(conf);
        } else {
            crashHandler = YamcsServer.globalCrashHandler;
        }
        try {
            // first load the XtceDB (if there is an error in it, we don't want to load any other service)
            xtceDb = XtceDbFactory.getInstance(instanceName);
            StreamInitializer.createStreams(instanceName);
            List<Object> services = conf.getList("services");
            serviceList = YamcsServer.createServices(instanceName, services);
            state = InstanceState.INITIALIZED;
            stateListeners.forEach(InstanceStateListener::initialized);
        } catch (Exception e) {
            state = InstanceState.FAILED;
            initFailed = e;
            stateListeners.forEach(l -> l.failed(initFailed));
            throw e;
        }
    }

    public InstanceState getState() {
        return state;
    }

    public void addStateListener(InstanceStateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(InstanceStateListener listener) {
        stateListeners.remove(listener);
    }

    public XtceDb getXtceDb() {
        return xtceDb;
    }

    public Throwable failureCause() {
        if (initFailed != null) {
            return initFailed;
        }
        return guavaService.failureCause();
    }

    /**
     * Starts this instance, and waits until it is running
     * 
     * @throws IllegalStateException
     *             if the instance fails to start
     */
    public void start() throws IllegalStateException {
        guavaService.startAsync();
        guavaService.awaitRunning();
    }

    public void startAsync() {
        guavaService.startAsync();
    }

    /**
     * Stops this instance, and waits until it terminates
     * 
     * @throws IllegalStateException
     *             if the instance fails to do a clean stop
     */
    public void stop() throws IllegalStateException {
        guavaService.stopAsync();
        guavaService.awaitTerminated();
    }

    public void stopAsync() {
        guavaService.stopAsync();
    }

    public void awaitTerminated() {
        guavaService.awaitTerminated();
    }

    private void loadTimeService() throws ConfigurationException {
        YConfiguration conf = YConfiguration.getConfiguration("yamcs." + instanceName);
        if (conf.containsKey("timeService")) {
            Map<String, Object> m = conf.getMap("timeService");
            String servclass = YConfiguration.getString(m, "class");
            Object args = m.get("args");
            try {
                if (args == null) {
                    timeService = YObjectLoader.loadObject(servclass, instanceName);
                } else {
                    timeService = YObjectLoader.loadObject(servclass, instanceName, args);
                }
            } catch (IOException e) {
                throw new ConfigurationException("Failed to load time service:" + e.getMessage(), e);
            }
        } else {
            timeService = new RealtimeTimeService();
        }
    }

    public ServiceWithConfig getServiceWithConfig(String serviceName) {
        if (serviceList == null) {
            return null;
        }

        for (ServiceWithConfig swc : serviceList) {
            Service s = swc.service;
            if (s.getClass().getName().equals(serviceName)) {
                return swc;
            }
        }
        return null;
    }

    public Service getService(String serviceName) {
        ServiceWithConfig serviceWithConfig = getServiceWithConfig(serviceName);
        return serviceWithConfig != null ? serviceWithConfig.getService() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(Class<T> serviceClass) {
        return (T) getService(serviceClass.getName());
    }

    public TimeService getTimeService() {
        return timeService;
    }

    public List<ServiceWithConfig> getServices() {
        return new ArrayList<>(serviceList);
    }

    /**
     * Registers an instance-specific service and starts it up
     */
    public void createAndStartService(String serviceClass, Map<String, Object> args)
            throws ConfigurationException, IOException {
        Map<String, Object> serviceConf = new HashMap<>(2);
        serviceConf.put("class", serviceClass);
        serviceConf.put("args", args);
        List<ServiceWithConfig> newServices = YamcsServer.createServices(instanceName, Arrays.asList(serviceConf));
        serviceList.addAll(newServices);
        YamcsServer.startServices(newServices);
    }

    public void startService(String serviceName) throws ConfigurationException, IOException {
        YamcsServer.startService(instanceName, serviceName, serviceList);
    }

    CrashHandler getCrashHandler() {
        return crashHandler;
    }

    /**
     * 
     * Returns Yamcs instance name
     */
    public String getName() {
        return instanceName;
    }

    public YamcsInstance getInstanceInfo() {
        YamcsInstance.Builder aib = YamcsInstance.newBuilder().setName(instanceName);
        InstanceState state = getState();
        aib.setState(state);
        if (state == InstanceState.FAILED) {
            aib.setFailureCause(failureCause().toString());
        }
        try {
            MissionDatabase.Builder mdb = MissionDatabase.newBuilder();
            YConfiguration c = YConfiguration.getConfiguration("yamcs." + instanceName);
            if (!c.isList("mdb")) {
                String configName = c.getString("mdb");
                mdb.setConfigName(configName);
            }
            XtceDb xtcedb = getXtceDb();
            if (xtcedb != null) { // if the instance is in a failed state, it could be that it doesn't have a XtceDB
                                  // (the failure might be due to the load of the XtceDb)
                mdb.setName(xtcedb.getRootSpaceSystem().getName());
                Header h = xtcedb.getRootSpaceSystem().getHeader();
                if ((h != null) && (h.getVersion() != null)) {
                    mdb.setVersion(h.getVersion());
                }
            }
            aib.setMissionDatabase(mdb.build());
        } catch (ConfigurationException | DatabaseLoadException e) {
            log.warn("Got error when finding the mission database for instance {}", instanceName, e);
        }
        return aib.build();
    }
}
