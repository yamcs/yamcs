package org.yamcs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.ReplayServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;

/**
 *
 * Yamcs server together with the global instances
 * 
 * 
 * @author nm
 *
 */
public class YamcsServer {
    static Map<String, YamcsServerInstance> instances = new LinkedHashMap<>();
    final static private String SERVER_ID_KEY = "serverId";

    ReplayServer replay;

    // global services
    static List<ServiceWithConfig> globalServiceList = null;

    static Logger staticlog = LoggerFactory.getLogger(YamcsServer.class);

    /** in the shutdown, allow services this number of seconds for stopping */
    public static int SERVICE_STOP_GRACE_TIME = 10;

    static TimeService realtimeTimeService = new RealtimeTimeService();

    // used for unit tests
    static TimeService mockupTimeService;

    private static String serverId;
    static YObjectLoader<Service> objLoader = new YObjectLoader<>();

    static CrashHandler globalCrashHandler = new LogCrashHandler();

    static CrashHandler loadCrashHandler(YConfiguration conf) throws ConfigurationException, IOException {
        if (conf.containsKey("crashHandler", "args")) {
            return YObjectLoader.loadObject(conf.getString("crashHandler", "class"),
                    conf.getMap("crashHandler", "args"));
        } else {
            return YObjectLoader.loadObject(conf.getString("crashHandler", "class"));
        }
    }

    /**
     * Creates services either server-wide (if instance is null) or instance-specific. The services are not yet started.
     * This must be done in a second step, so that components can ask YamcsServer for other service instantiations.
     *
     * @param services
     *            - list of service configuration; each of them is a string (=classname) or a map
     * @param instance
     *            - if null, then start a server-wide service, otherwise an instance-specific service
     * @throws IOException
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    static List<ServiceWithConfig> createServices(String instance, List<Object> servicesConfig)
            throws ConfigurationException, IOException {
        ManagementService managementService = ManagementService.getInstance();
        List<ServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for (Object servobj : servicesConfig) {
            String servclass;
            Object args = null;
            if (servobj instanceof String) {
                servclass = (String) servobj;
            } else if (servobj instanceof Map<?, ?>) {
                Map<String, Object> m = (Map<String, Object>) servobj;
                servclass = YConfiguration.getString(m, "class");
                args = m.get("args");
            } else {
                throw new ConfigurationException(
                        "Services can either be specified by classname, or by {class: classname, args: ....} map. Cannot load a service from "
                                + servobj);

            }
            staticlog.info("Loading {} service {}", (instance == null) ? "server-wide" : instance, servclass);
            ServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, servclass, args);
                serviceList.add(swc);
            } catch (NoClassDefFoundError e) {
                staticlog.error("Cannot create service {}, with arguments {}: class {} not found", servclass, args,
                        e.getMessage());
                throw e;
            } catch (Exception t) {
                staticlog.error("Cannot create service {}, with arguments {}: {}", servclass, args, t.getMessage());
                throw t;
            }
            if (managementService != null) {
                managementService.registerService(instance, servclass, swc.service);
            }
        }

        return serviceList;
    }

    /**
     * Starts the specified list of services.
     *
     * @param serviceList
     *            list of service configurations
     * @throws ConfigurationException
     */
    public static void startServices(List<ServiceWithConfig> serviceList) throws ConfigurationException {
        for (ServiceWithConfig swc : serviceList) {
            swc.service.startAsync();
            try {
                swc.service.awaitRunning();
            } catch (IllegalStateException e) {
                // this happens when it fails, the next check will throw an error in this case
            }
            State result = swc.service.state();
            if (result == State.FAILED) {
                throw new ConfigurationException("Failed to start service " + swc.service, swc.service.failureCause());
            }
        }
    }

    public static void shutDown() {
        for (YamcsServerInstance ys : instances.values()) {
            ys.stopAsync();
        }
        for (YamcsServerInstance ys : instances.values()) {
            ys.awaitTerminated();
        }
    }

    public static boolean hasInstance(String instance) {
        return instances.containsKey(instance);
    }

    public static String getServerId() {
        return serverId;
    }

    public static void createGlobalServicesAndInstances() throws ConfigurationException, IOException {
        serverId = deriveServerId();

        YConfiguration c = YConfiguration.getConfiguration("yamcs");
        if (c.containsKey("crashHandler")) {
            globalCrashHandler = loadCrashHandler(c);
        }

        if (c.containsKey("services")) {
            List<Object> services = c.getList("services");
            globalServiceList = createServices(null, services);
        }

        if (c.containsKey("instances")) {
            List<String> instArray = c.getList("instances");
            for (String inst : instArray) {
                if (instances.containsKey(inst)) {
                    throw new ConfigurationException("Duplicate instance specified: '" + inst + "'");
                }
                createYamcsInstance(inst);
            }
        }
    }

    public static void startServices() throws Exception {
        if (globalServiceList != null) {
            startServices(globalServiceList);
        }

        for (String inst : instances.keySet()) {
            instances.get(inst).startAsync();
        }
    }

    public static void setupYamcsServer() throws Exception {
        createGlobalServicesAndInstances();
        startServices();

        Thread.setDefaultUncaughtExceptionHandler((t, thrown) -> {
            String msg = "Uncaught exception '" + thrown + "' in thread " + t + ": "
                    + Arrays.toString(thrown.getStackTrace());
            staticlog.error(msg);
            globalCrashHandler.handleCrash("UncaughtException", msg);
        });

        if (System.getenv("YAMCS_DAEMON") == null) {
            staticlog.info("Server running... press ctrl-c to stop");
        } else {// the init.d/yamcs-server depends on this line on the standard output, do not change it (without
                // changing the script also)!
            System.out.println("yamcsstartup success");
        }
    }

    /**
     * Creates a new yamcs instance without starting it. If the instance already exist and not in the state FAILED or
     * TERMINATED a ConfigurationException is thrown
     * 
     * @param name
     *            the name of the new instance
     * 
     * @return the newly created instance
     * @throws IOException
     */
    public static YamcsServerInstance createYamcsInstance(String name) throws IOException {
        YamcsServerInstance ysi = instances.get(name);
        if (ysi != null) {
            if ((ysi.state() != State.FAILED) && (ysi.state() != State.TERMINATED)) {
                throw new IllegalArgumentException(String.format(
                        "There already exists an instance named '%s' and is not in FAILED or TERMINATED state", name));
            } else {
                staticlog.info("Re-loading instance '{}'", name);
            }
        } else {
            staticlog.info("Loading instance '{}'", name);
        }
        ysi = new YamcsServerInstance(name);
        instances.put(name, ysi);
        ManagementService.getInstance().registerYamcsInstance(ysi);
        ysi.init();

        return ysi;
    }

    public static Set<String> getYamcsInstanceNames() {
        return instances.keySet();
    }

    public static YamcsInstances getYamcsInstances() {
        YamcsInstances.Builder aisb = YamcsInstances.newBuilder();
        for (String name : instances.keySet()) {
            aisb.addInstance(getYamcsInstance(name));
        }
        return aisb.build();
    }

    public static YamcsInstance getYamcsInstance(String name) {
        if (!hasInstance(name)) {
            return null;
        }
        YamcsInstance.Builder aib = YamcsInstance.newBuilder().setName(name);
        YamcsServerInstance ysi = getInstance(name);
        Service.State state = ysi.state();
        aib.setState(ServiceState.valueOf(state.name()));
        if (state == State.FAILED) {
            aib.setFailureCause(ysi.failureCause().toString());
        }
        try {
            MissionDatabase.Builder mdb = MissionDatabase.newBuilder();
            YConfiguration c = YConfiguration.getConfiguration("yamcs." + name);
            if (!c.isList("mdb")) {
                String configName = c.getString("mdb");
                mdb.setConfigName(configName);
            }
            XtceDb xtcedb = XtceDbFactory.getInstance(name);
            mdb.setName(xtcedb.getRootSpaceSystem().getName());
            Header h = xtcedb.getRootSpaceSystem().getHeader();
            if ((h != null) && (h.getVersion() != null)) {
                mdb.setVersion(h.getVersion());
            }
            aib.setMissionDatabase(mdb.build());
        } catch (ConfigurationException | DatabaseLoadException e) {
            staticlog.warn("Got error when finding the mission database for instance {}", name, e);
        }
        return aib.build();
    }

    private static String deriveServerId() {
        try {
            YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
            String id;
            if (yconf.containsKey(SERVER_ID_KEY)) {
                id = yconf.getString(SERVER_ID_KEY);
            } else {
                id = InetAddress.getLocalHost().getHostName();
            }
            serverId = id;
            staticlog.debug("Using serverId {}", serverId);
            return serverId;
        } catch (ConfigurationException e) {
            throw e;
        } catch (UnknownHostException e) {
            String msg = "Java cannot resolve local host (InetAddress.getLocalHost()). Make sure it's defined properly or alternatively add 'serverId: <name>' to yamcs.yaml";
            staticlog.warn(msg);
            throw new ConfigurationException(msg, e);
        }
    }

    public static YamcsServerInstance getInstance(String yamcsInstance) {
        return instances.get(yamcsInstance);
    }

    private static void setupSecurity() {
        org.yamcs.security.Privilege.getInstance();
    }

    private static void printOptionsAndExit() {
        System.err.println("Usage: yamcs-server.sh");
        System.err.println("\t All options are taken from yamcs.yaml");
        System.exit(-1);
    }

    public static TimeService getTimeService(String yamcsInstance) {
        if (instances.containsKey(yamcsInstance)) {
            return instances.get(yamcsInstance).getTimeService();
        } else {
            if (mockupTimeService != null) {
                return mockupTimeService;
            } else {
                return realtimeTimeService; // happens from unit tests
            }
        }

    }

    public static List<ServiceWithConfig> getGlobalServices() {
        return new ArrayList<>(globalServiceList);
    }

    public static <T extends Service> T getService(String yamcsInstance, Class<T> serviceClass) {
        YamcsServerInstance ys = YamcsServer.getInstance(yamcsInstance);
        if (ys == null) {
            return null;
        }
        return ys.getService(serviceClass);
    }

    public static void setMockupTimeService(TimeService timeService) {
        mockupTimeService = timeService;
    }

    public static Service getGlobalService(String serviceName) {
        if (globalServiceList == null) {
            return null;
        }

        synchronized (globalServiceList) {
            for (ServiceWithConfig swc : globalServiceList) {
                Service s = swc.service;
                if (s.getClass().getName().equals(serviceName)) {
                    return s;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Service> T getGlobalService(Class<T> serviceClass) {
        return (T) getGlobalService(serviceClass.getName());
    }

    static ServiceWithConfig createService(String instance, String serviceClass, String serviceName, Object args)
            throws ConfigurationException, IOException {
        Service serv;
        if (instance != null) {
            if (args == null) {
                serv = YObjectLoader.loadObject(serviceClass, instance);
            } else {
                serv = YObjectLoader.loadObject(serviceClass, instance, args);
            }
        } else {
            if (args == null) {
                serv = YObjectLoader.loadObject(serviceClass);
            } else {
                serv = YObjectLoader.loadObject(serviceClass, args);
            }
        }
        return new ServiceWithConfig(serv, serviceClass, serviceName, args);
    }

    // starts a service that has stopped or not yet started
    static Service startService(String instance, String serviceName, List<ServiceWithConfig> serviceList)
            throws ConfigurationException, IOException {
        for (int i = 0; i < serviceList.size(); i++) {
            ServiceWithConfig swc = serviceList.get(i);
            if (swc.name.equals(serviceName)) {
                switch (swc.service.state()) {
                case RUNNING:
                case STARTING:
                    // do nothing, service is already starting
                    break;
                case NEW: // not yet started, start it now
                    swc.service.startAsync();
                    break;
                case FAILED:
                case STOPPING:
                case TERMINATED:
                    // start a new one
                    swc = createService(instance, swc.serviceClass, serviceName, swc.args);
                    serviceList.set(i, swc);
                    swc.service.startAsync();
                    break;
                }
                return swc.service;
            }
        }
        return null;
    }

    public static void startGlobalService(String serviceName) throws ConfigurationException, IOException {
        startService(null, serviceName, globalServiceList);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            printOptionsAndExit();
        }

        try {
            YConfiguration.setup();
            setupSecurity();
            setupYamcsServer();

        } catch (ConfigurationException e) {
            staticlog.error("Could not start Yamcs Server", e);
            System.err.println(e.toString());
            System.exit(-1);
        } catch (Exception e) {
            staticlog.error("Could not start Yamcs Server", e);
            System.exit(-1);
        }
    }

    public static CrashHandler getCrashHandler(String yamcsInstance) {
        YamcsServerInstance ys = getInstance(yamcsInstance);
        if (ys != null) {
            return ys.getCrashHandler();
        } else {
            return globalCrashHandler; // may happen if the instance name is not valid (in unit tests)
        }
    }

    public static CrashHandler getGlobalCrashHandler() {
        return globalCrashHandler;
    }

}
