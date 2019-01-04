package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.yamcs.protobuf.Mdb.MissionDatabase;
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

import com.google.common.util.concurrent.Service;

/**
 * Represents a Yamcs instance together with the instance specific services
 * 
 * @author nm
 *
 */
public class YamcsServerInstance extends YamcsInstanceService {
    private String instanceName;
    Logger log;
    TimeService timeService;
    private CrashHandler crashHandler;
    // instance specific services
    List<ServiceWithConfig> serviceList;
    private XtceDb xtceDb;

    Map<String, String> labels;
    YConfiguration conf;

    YamcsServerInstance(String name) {
        this.instanceName = name;
        log = LoggingUtils.getLogger(YamcsServer.class, name);
    }

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

    void init(YConfiguration conf) throws IOException {
        this.conf = conf;
        initAsync();
        awaitInitialized();
    }

    @Override
    public void doInit() {
        try {
            loadTimeService();

            if (conf.containsKey("crashHandler")) {
                crashHandler = YamcsServer.loadCrashHandler(conf);
            } else {
                crashHandler = YamcsServer.globalCrashHandler;
            }

            // first load the XtceDB (if there is an error in it, we don't want to load any other service)
            xtceDb = XtceDbFactory.getInstance(instanceName);
            StreamInitializer.createStreams(instanceName);
            List<Object> services = conf.getList("services");
            serviceList = YamcsServer.createServices(instanceName, services);
            notifyInitialized();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    public XtceDb getXtceDb() {
        return xtceDb;
    }

    /**
     * Starts this instance, and waits until it is running
     * 
     * @throws IllegalStateException
     *             if the instance fails to start
     */
    public void start() throws IllegalStateException {
        startAsync();
        awaitRunning();
    }

    /**
     * Stops this instance, and waits until it terminates
     * 
     * @throws IllegalStateException
     *             if the instance fails to do a clean stop
     */
    public void stop() throws IllegalStateException {
        stopAsync();
        awaitOffline();

        // set to null to free some memory
        xtceDb = null;
        serviceList = null;
    }

    public void loadTimeService() throws ConfigurationException {
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
            if (swc.getName().equals(serviceName)) {
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
    public <T extends Service> List<T> getServices(Class<T> serviceClass) {
        List<T> services = new ArrayList<>();
        if (serviceList != null) {
            for (ServiceWithConfig swc : serviceList) {
                if (swc.getServiceClass().equals(serviceClass.getName())) {
                    services.add((T) swc.service);
                }
            }
        }
        return services;
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
        InstanceState state = state();
        aib.setState(state);
        if (state == InstanceState.FAILED) {
            aib.setFailureCause(failureCause().toString());
        }
        if (conf != null) { // Can be null for an offline instance
            try {
                MissionDatabase.Builder mdb = MissionDatabase.newBuilder();
                if (!conf.isList("mdb")) {
                    String configName = conf.getString("mdb");
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
        }
        return aib.build();
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, ?> getLabels() {
        return labels;
    }
}
