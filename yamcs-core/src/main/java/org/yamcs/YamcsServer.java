package org.yamcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.ReplayServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;
import org.yamcs.security.CryptoUtils;
import org.yamcs.spi.Plugin;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.YarchDatabase;

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

    private static final String SERVER_ID_KEY = "serverId";
    private static final String SECRET_KEY = "secretKey";

    private Set<Plugin> plugins = new HashSet<>();

    ReplayServer replay;

    // global services
    List<ServiceWithConfig> globalServiceList = null;

    static Logger staticlog = LoggerFactory.getLogger(YamcsServer.class);

    /** in the shutdown, allow services this number of seconds for stopping */
    public static int SERVICE_STOP_GRACE_TIME = 10;

    static TimeService realtimeTimeService = new RealtimeTimeService();

    // used for unit tests
    static TimeService mockupTimeService;

    private static String serverId;
    private static byte[] secretKey;

    static CrashHandler globalCrashHandler = new LogCrashHandler();
    int maxOnlineInstances = 1000;
    int maxNumInstances = 20;
    static String dataDir;

    static final Pattern ONLINE_INST_PATTERN = Pattern.compile("yamcs\\.(.*)\\.yaml");
    static final Pattern OFFLINE_INST_PATTERN = Pattern.compile("yamcs\\.(.*)\\.yaml.offline");

    static YamcsServer server;

    static CrashHandler loadCrashHandler(YConfiguration conf) throws ConfigurationException, IOException {
        if (conf.containsKey("crashHandler", "args")) {
            return YObjectLoader.loadObject(conf.getString("crashHandler", "class"),
                    conf.getMap("crashHandler", "args"));
        } else {
            return YObjectLoader.loadObject(conf.getString("crashHandler", "class"));
        }

    }

    /**
     * Creates services at global (if instance is null) or instance level. The services are not yet started. This must
     * be done in a second step, so that components can ask YamcsServer for other service instantiations.
     *
     * @param services
     *            list of service configuration; each of them is a string (=classname) or a map
     * @param instance
     *            if null, then start a global service, otherwise an instance service
     * @throws IOException
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    static List<ServiceWithConfig> createServices(String instance, List<Object> servicesConfig)
            throws ConfigurationException, IOException {
        int count = 1;
        ManagementService managementService = ManagementService.getInstance();
        Set<String> names = new HashSet<>();
        List<ServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for (Object servobj : servicesConfig) {
            String servclass;
            Object args = null;
            String name = "service" + count;
            if (servobj instanceof String) {
                servclass = (String) servobj;
            } else if (servobj instanceof Map<?, ?>) {
                Map<String, Object> m = (Map<String, Object>) servobj;
                servclass = YConfiguration.getString(m, "class");
                args = m.get("args");
                if (m.containsKey("name")) {
                    name = m.get("name").toString();
                }
            } else {
                throw new ConfigurationException(
                        "Services can either be specified by classname, or by {class: classname, args: ....} map. Cannot load a service from "
                                + servobj);
            }
            if (names.contains(name)) {
                throw new ConfigurationException(
                        "There is already a service named '" + name + "'");
            }

            staticlog.info("Loading {} service {}", (instance == null) ? "global" : instance, servclass);
            ServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, name, args);
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
                managementService.registerService(instance, name, swc.service);
            }
            names.add(name);
            count++;
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

    public static byte[] getSecretKey() {
        return secretKey;
    }

    public void discoverPlugins() {
        for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
            staticlog.info("{} {}", plugin.getName(), plugin.getVersion());
            plugins.add(plugin);
        }
    }

    public void createGlobalServicesAndInstances() throws ConfigurationException, IOException {
        serverId = deriveServerId();
        deriveSecretKey();

        YConfiguration c = YConfiguration.getConfiguration("yamcs");
        if (c.containsKey("crashHandler")) {
            globalCrashHandler = loadCrashHandler(c);
        }
        dataDir = c.getString("dataDir");

        if (c.containsKey("services")) {
            List<Object> services = c.getList("services");
            globalServiceList = createServices(null, services);
        }
        int numOnlineInst = 0;
        if (c.containsKey("instances")) {
            List<String> instArray = c.getList("instances");
            for (String inst : instArray) {
                if (instances.containsKey(inst)) {
                    throw new ConfigurationException("Duplicate instance specified: '" + inst + "'");
                }
                createYamcsInstance(inst, YConfiguration.getConfiguration("yamcs." + inst), true);
                numOnlineInst++;
            }
        }

        File dir = new File(dataDir + "/instance-def");
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                boolean online;
                String name;
                Matcher m = ONLINE_INST_PATTERN.matcher(f.getName());
                if (m.matches()) {
                    online = true;
                    name = m.group(1);
                    numOnlineInst++;
                    if (numOnlineInst > maxOnlineInstances) {
                        throw new ConfigurationException(
                                "Number of online instances greater than maximum allowed " + maxOnlineInstances);
                    }
                } else {
                    m = OFFLINE_INST_PATTERN.matcher(f.getName());
                    if (m.matches()) {
                        online = false;
                        name = m.group(1);
                        if (instances.size() > maxNumInstances) {
                            staticlog.warn("Number of instances exceeds the maximum {}, offline instance {} not loaded",
                                    maxNumInstances, name);
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                createYamcsInstance(name, getConf(name, online), online);
            }
        }
    }

    private int getNumOnlineInstances() {
        return (int)instances.values().stream().filter(ysi -> ysi.getState()!=InstanceState.OFFLINE).count();
    }
    
    private void startServices() throws Exception {
        if (globalServiceList != null) {
            startServices(globalServiceList);
        }

        for (YamcsServerInstance ysi : instances.values()) {
            if (ysi.getState() != InstanceState.OFFLINE) {
                ysi.startAsync();
            }
        }
    }

    public static void setupYamcsServer() throws Exception {
        staticlog.info("yamcs {}, build {}", YamcsVersion.VERSION, YamcsVersion.REVISION);
        server = new YamcsServer();
        server.discoverPlugins();
        server.createGlobalServicesAndInstances();
        server.startServices();

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
     * Restarts a yamcs instance. As we cannot restart instances, we create a new one and replace the old one.
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the newly created instance
     */
    public static YamcsServerInstance restartYamcsInstance(String instanceName) {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.getState() == InstanceState.RUNNING) {
            try {
                ysi.stop();
            } catch (IllegalStateException e) {
                staticlog.error("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        XtceDbFactory.remove(instanceName);
        staticlog.info("Re-loading instance '{}'", instanceName);

        try {
            ysi.init(getConf(instanceName, true));
            ysi.startAsync();
        } catch (IOException e) {
            staticlog.error("Failed to init/start instance '{}'", instanceName, e);
        }

        return ysi;
    }

    private static YConfiguration getConf(String instanceName, boolean online) {
        File f = new File(dataDir + "/instance-def/yamcs." + instanceName + ".yaml" + (online ? "" : ".offline"));
        if (f.exists()) {
            try (InputStream is = new FileInputStream(f)) {
                return new YConfiguration("yamcs." + instanceName, is, f.getAbsolutePath());
            } catch (IOException e) {
                throw new ConfigurationException("Cannot load configuration from " + f.getAbsolutePath(), e);
            }
        } else {
            return YConfiguration.getConfiguration("yamcs." + instanceName);
        }
    }

    /**
     * Stop the instance (it will be offline after this)
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the instance
     */
    public YamcsServerInstance stopInstance(String instanceName) {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.getState() == InstanceState.RUNNING) {
            try {
                ysi.stop();
            } catch (IllegalStateException e) {
                staticlog.error("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        XtceDbFactory.remove(instanceName);
        File f = new File(dataDir + "/instance-def/yamcs." + instanceName + ".yaml");
        if (f.exists()) {
            f.renameTo(new File(dataDir + "/instance-def/yamcs." + instanceName + ".yaml.offline"));
        }

        return ysi;
    }

    /**
     * Start the instance.
     * If the instance is already started, do nothing.
     * If the instance is offline, rename the <instance>.yaml.offline to <instance>.yaml and start the instance
     * Otherwise (if the instance is failed) restart it
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the instance
     * @throws IOException 
     */
    public YamcsServerInstance startInstance(String instanceName) throws IOException {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.getState() == InstanceState.RUNNING) {
            return ysi;
        } else if (ysi.getState() != InstanceState.OFFLINE) {
            return restartYamcsInstance(instanceName);
        }
        if(getNumOnlineInstances()>=maxOnlineInstances) {
            throw new LimitExceededException("Number of online instances already at the limit "+maxOnlineInstances); 
        }
        File f = new File(dataDir + "/instance-def/yamcs." + instanceName + ".yaml.offline");
        if (f.exists()) {
            f.renameTo(new File(dataDir + "/instance-def/yamcs." + instanceName + ".yaml"));
        }
        ysi.init(getConf(instanceName, true));
        ysi.startAsync();
        return ysi;
    }

    public Set<Plugin> getPlugins() {
        return plugins;
    }

    /**
     * Creates a new yamcs instance. If the instance already exists a ConfigurationException is thrown
     * 
     * @param name
     *            the name of the new instance
     * 
     * @return the newly created instance
     * @throws IOException
     */
    public YamcsServerInstance createYamcsInstance(String name, YConfiguration conf, boolean online)
            throws IOException {
        YamcsServerInstance ysi = instances.get(name);
        if (ysi != null) {
            throw new IllegalArgumentException(String.format(
                    "There already exists an instance named '%s' and it is not in FAILED or OFFLINE state", name));
        }

        if (online) {
            staticlog.info("Loading online instance '{}'", name);
        } else {
            staticlog.debug("Loading offline instance '{}'", name);
        }
        ysi = new YamcsServerInstance(name);
        if (conf.containsKey("tags")) {
            ysi.setTags(conf.getMap("tags"));
        }
        instances.put(name, ysi);
        if (online) {
            ysi.init(conf);
        }
        ManagementService.getInstance().registerYamcsInstance(ysi);
        return ysi;
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

    private static void deriveSecretKey() {
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        if (yconf.containsKey(SECRET_KEY)) {
            // Should maybe only allow base64 encoded secret keys
            secretKey = yconf.getString(SECRET_KEY).getBytes(StandardCharsets.UTF_8);
        } else {
            staticlog.warn("Generating random non-persisted secret key."
                    + " Cryptographic verifications will not work across server restarts."
                    + " Set 'secretKey: <secret>' in yamcs.yaml to avoid this message.");
            secretKey = CryptoUtils.generateRandomSecretKey();
        }
    }

    public static Set<YamcsServerInstance> getInstances() {
        return new HashSet<>(instances.values());
    }

    public static YamcsServerInstance getInstance(String yamcsInstance) {
        return instances.get(yamcsInstance);
    }

    private static void printOptionsAndExit() {
        System.err.println("Usage: yamcsd");
        System.err.println("\t All options are read from yamcs.yaml");
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

    public List<ServiceWithConfig> getGlobalServices() {
        return new ArrayList<>(globalServiceList);
    }

    public ServiceWithConfig getGlobalServiceWithConfig(String serviceName) {
        if (globalServiceList == null) {
            return null;
        }

        synchronized (globalServiceList) {
            for (ServiceWithConfig swc : globalServiceList) {
                if (swc.getName().equals(serviceName)) {
                    return swc;
                }
            }
        }
        return null;
    }

    public static <T extends Service> List<T> getServices(String yamcsInstance, Class<T> serviceClass) {
        YamcsServerInstance ys = YamcsServer.getInstance(yamcsInstance);
        if (ys == null) {
            return Collections.emptyList();
        }
        return ys.getServices(serviceClass);
    }

    public static void setMockupTimeService(TimeService timeService) {
        mockupTimeService = timeService;
    }

    public YamcsService getGlobalService(String serviceName) {
        ServiceWithConfig serviceWithConfig = getGlobalServiceWithConfig(serviceName);
        return serviceWithConfig != null ? serviceWithConfig.getService() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends YamcsService> List<T> getGlobalServices(Class<T> serviceClass) {
        List<T> services = new ArrayList<>();
        if (globalServiceList != null) {
            for (ServiceWithConfig swc : globalServiceList) {
                if (swc.getServiceClass().equals(serviceClass.getName())) {
                    services.add((T) swc.service);
                }
            }
        }
        return services;
    }

    static ServiceWithConfig createService(String instance, String serviceClass, String serviceName, Object args)
            throws ConfigurationException, IOException {
        YamcsService serv;
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
    static YamcsService startService(String instance, String serviceName, List<ServiceWithConfig> serviceList)
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

    public void startGlobalService(String serviceName) throws ConfigurationException, IOException {
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

    /**
     * 
     * @return the (singleton) server
     */
    public static YamcsServer getServer() {
        return server;
    }
}
