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
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

/**
 * Represents a Yamcs instance together with the instance specific services
 * 
 * @author nm
 *
 */
public class YamcsServerInstance extends AbstractService {
    private String instanceName;
    Logger log;
    TimeService timeService;
    private CrashHandler crashHandler;
    // instance specific services
    List<ServiceWithConfig> serviceList;

    YamcsServerInstance(String instance) throws IOException {
        this.instanceName = instance;
        log = LoggingUtils.getLogger(YamcsServer.class, instance);
        loadTimeService();
    }

    void init() throws IOException {
        YConfiguration conf = YConfiguration.getConfiguration("yamcs." + instanceName);
        if (conf.containsKey("crashHandler")) {
            crashHandler = YamcsServer.loadCrashHandler(conf);
        } else {
            crashHandler = YamcsServer.globalCrashHandler;
        }
        // first load the XtceDB (if there is an error in it, we don't want to load any other service)
        XtceDbFactory.getInstance(instanceName);

        StreamInitializer.createStreams(instanceName);
        List<Object> services = conf.getList("services");
        serviceList = YamcsServer.createServices(instanceName, services);
    }

    private void loadTimeService() throws ConfigurationException, IOException {
        YConfiguration conf = YConfiguration.getConfiguration("yamcs." + instanceName);
        if (conf.containsKey("timeService")) {
            Map<String, Object> m = conf.getMap("timeService");
            String servclass = YConfiguration.getString(m, "class");
            Object args = m.get("args");
            if (args == null) {
                timeService = YObjectLoader.loadObject(servclass, instanceName);
            } else {
                timeService = YObjectLoader.loadObject(servclass, instanceName, args);
            }
        } else {
            timeService = new RealtimeTimeService();
        }
    }

    public Service getService(String serviceName) {
        if (serviceList == null) {
            return null;
        }

        for (ServiceWithConfig swc : serviceList) {
            Service s = swc.service;
            if (s.getClass().getName().equals(serviceName)) {
                return s;
            }
        }
        return null;
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

    @Override
    protected void doStart() {
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

    /**
     * 
     * Returns Yamcs instance name
     */
    public String getName() {
        return instanceName;
    }
}
