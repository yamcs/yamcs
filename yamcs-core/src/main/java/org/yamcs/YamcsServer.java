package org.yamcs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.InstanceTemplate;
import org.yamcs.protobuf.YamcsManagement.TemplateVariable;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance.InstanceState;
import org.yamcs.security.CryptoUtils;
import org.yamcs.spi.Plugin;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.TemplateProcessor;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yaml.snakeyaml.Yaml;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 *
 * Yamcs server together with the global instances
 * 
 * 
 * @author nm
 *
 */
public class YamcsServer {
    Map<String, YamcsServerInstance> instances = new LinkedHashMap<>();

    private static final String SERVER_ID_KEY = "serverId";
    private static final String SECRET_KEY = "secretKey";

    Set<Plugin> plugins = new HashSet<>();

    Map<String, InstanceTemplate> instanceTemplates = new HashMap<>();

    // global services
    List<ServiceWithConfig> globalServiceList;

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
    String dataDir;
    String instanceDefDir;

    static final Pattern ONLINE_INST_PATTERN = Pattern.compile("yamcs\\.(.*)\\.yaml");
    static final Pattern OFFLINE_INST_PATTERN = Pattern.compile("yamcs\\.(.*)\\.yaml.offline");
    static YamcsServer server = new YamcsServer();

    static CrashHandler loadCrashHandler(YConfiguration conf) throws ConfigurationException, IOException {
        if (conf.containsKey("crashHandler", "args")) {
            return YObjectLoader.loadObject(conf.getSubString("crashHandler", "class"),
                    conf.getSubMap("crashHandler", "args"));
        } else {
            return YObjectLoader.loadObject(conf.getSubString("crashHandler", "class"));
        }

    }

    /**
     * Creates services at global (if instance is null) or instance level. The services are not yet started. This must
     * be done in a second step, so that components can ask YamcsServer for other service instantiations.
     *
     * @param instance
     *            if null, then start a global service, otherwise an instance service
     * @param services
     *            list of service configuration; each of them is a string (=classname) or a map
     * @throws IOException
     * @throws ConfigurationException
     */
    static List<ServiceWithConfig> createServices(String instance, List<YConfiguration> servicesConfig)
            throws ConfigurationException, IOException {
        ManagementService managementService = ManagementService.getInstance();
        Set<String> names = new HashSet<>();
        List<ServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for (YConfiguration servconf : servicesConfig) {
            String servclass;
            Object args = null;
            String name = null;
            servclass = servconf.getString("class");
            args = servconf.get("args");
            if (args instanceof Map) {
                args = servconf.getConfig("args");
            }
            name = servconf.getString("name", servclass.substring(servclass.lastIndexOf('.') + 1));
            String candidateName = name;
            int count = 1;
            while (names.contains(candidateName)) {
                candidateName = name + "-" + count;
                count++;
            }
            name = candidateName;

            staticlog.info("Loading {} service {} ({})", (instance == null) ? "global" : instance, name, servclass);
            ServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, name, args);
                serviceList.add(swc);
            } catch (NoClassDefFoundError e) {
                staticlog.error("Cannot create service {}, with arguments {}: class {} not found", servclass, args,
                        e.getMessage());
                throw e;
            } catch (ValidationException e) {
                staticlog.error("Cannot create service {}, with arguments {}: {}", servclass, args, e.getMessage());
                throw new ConfigurationException("Invalid configuration");
            } catch (Exception e) {
                staticlog.error("Cannot create service {}, with arguments {}: {}", servclass, args, e.getMessage());
                throw e;
            }
            if (managementService != null) {
                managementService.registerService(instance, name, swc.service);
            }
            names.add(name);
        }

        return serviceList;
    }

    public <T extends YamcsService> void addGlobalService(
            String name, Class<T> serviceClass, YConfiguration args) throws ValidationException, IOException {

        for (ServiceWithConfig otherService : server.globalServiceList) {
            if (otherService.getName().equals(name)) {
                throw new ConfigurationException(String.format(
                        "A service named '%s' already exists", name));
            }
        }

        staticlog.info("Loading global service {} ({})", name, serviceClass.getName());
        ServiceWithConfig swc = createService(null, serviceClass.getName(), name, args);
        server.globalServiceList.add(swc);

        ManagementService managementService = ManagementService.getInstance();
        managementService.registerService(null, name, swc.service);
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
            staticlog.debug("Starting service {}", swc.getName());
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

    public void shutDown() {
        for (YamcsServerInstance ys : instances.values()) {
            ys.stopAsync();
        }
        for (YamcsServerInstance ys : instances.values()) {
            ys.awaitOffline();
        }
    }

    public static boolean hasInstance(String instance) {
        return server.instances.containsKey(instance);
    }

    public static boolean hasInstanceTemplate(String template) {
        return server.instanceTemplates.containsKey(template);
    }

    public static String getServerId() {
        return serverId;
    }

    public static byte[] getSecretKey() {
        return secretKey;
    }

    private void discoverTemplates() throws IOException {
        Path templatesDir = Paths.get("etc").resolve("instance-templates");
        if (!Files.exists(templatesDir)) {
            return;
        }

        try (Stream<Path> dirStream = Files.list(templatesDir)) {
            dirStream.filter(Files::isDirectory).forEach(p -> {
                if (Files.exists(p.resolve("template.yaml"))) {
                    String name = p.getFileName().toString();
                    InstanceTemplate.Builder templateb = InstanceTemplate.newBuilder()
                            .setName(name);

                    Path varFile = p.resolve("variables.yaml");
                    if (Files.exists(varFile)) {
                        try (InputStream in = new FileInputStream(varFile.toFile())) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> varDefs = (List<Map<String, Object>>) new Yaml().load(in);
                            for (Map<String, Object> varDef : varDefs) {
                                TemplateVariable.Builder varb = TemplateVariable.newBuilder();
                                varb.setName(YConfiguration.getString(varDef, "name"));
                                varb.setRequired(YConfiguration.getBoolean(varDef, "required", true));
                                if (varDef.containsKey("description")) {
                                    varb.setDescription(YConfiguration.getString(varDef, "description"));
                                }
                                templateb.addVariable(varb);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    server.instanceTemplates.put(name, templateb.build());
                }
            });
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
        instanceDefDir = dataDir + "/instance-def/";

        if (c.containsKey("services")) {
            List<YConfiguration> services = c.getServiceConfigList("services");
            globalServiceList = createServices(null, services);
        }
        int numOnlineInst = 0;
        if (c.containsKey("instances")) {
            List<String> instArray = c.getList("instances");
            for (String inst : instArray) {
                if (instances.containsKey(inst)) {
                    throw new ConfigurationException("Duplicate instance specified: '" + inst + "'");
                }
                createInstance(inst, YConfiguration.getConfiguration("yamcs." + inst));
                numOnlineInst++;
            }
        }

        File dir = new File(instanceDefDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new ConfigurationException("Failed to create directory " + dir);
            }
        }

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

            YamcsServerInstance ysi = createInstance(name, online ? getConf(name) : null);
            File metadataFile = new File(instanceDefDir + "yamcs." + name + ".metadata");
            if (metadataFile.exists()) {
                Yaml y = new Yaml();
                try (InputStream in = new FileInputStream(metadataFile)) {
                    Object o = y.load(in);
                    if (o instanceof Map<?, ?>) {
                        Object labels = ((Map<String, Object>) o).get("labels");
                        if (labels instanceof Map<?, ?>) {
                            ysi.setLabels((Map<String, String>) labels);
                        } else {
                            staticlog.warn("Unexpected data of type {} in {}, expected a map", o.getClass(),
                                    metadataFile.getAbsolutePath());
                        }
                    } else {
                        staticlog.warn("Unexpected data of type {} in {}, expected a map", o.getClass(),
                                metadataFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void loadPlugins() {
        List<String> disabledPlugins;
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        if (yconf.containsKey("disabledPlugins")) {
            disabledPlugins = yconf.getList("disabledPlugins");
        } else {
            disabledPlugins = Collections.emptyList();
        }

        for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
            if (disabledPlugins.contains(plugin.getName())) {
                staticlog.debug("Ignoring plugin {} (disabled by user config)", plugin.getName());
            } else {
                plugins.add(plugin);
            }
        }

        for (Plugin plugin : plugins) {
            staticlog.debug("Loading plugin {} {}", plugin.getName(), plugin.getVersion());
            plugin.onLoad();
        }
    }

    private int getNumOnlineInstances() {
        return (int) instances.values().stream().filter(ysi -> ysi.state() != InstanceState.OFFLINE).count();
    }

    private void startServices() throws Exception {
        if (globalServiceList != null) {
            startServices(globalServiceList);
        }

        for (YamcsServerInstance ysi : instances.values()) {
            if (ysi.state() != InstanceState.OFFLINE) {
                ysi.startAsync();
            }
        }
    }

    public static void setupYamcsServer() throws Exception {
        staticlog.info("yamcs {}, build {}", YamcsVersion.VERSION, YamcsVersion.REVISION);

        server.discoverTemplates();
        server.createGlobalServicesAndInstances();
        server.loadPlugins();
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
     * Restarts a yamcs instance.
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the newly created instance
     * @throws IOException
     */
    public YamcsServerInstance restartInstance(String instanceName) throws IOException {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.state() == InstanceState.RUNNING || ysi.state() == InstanceState.FAILED) {
            try {
                ysi.stop();
            } catch (IllegalStateException e) {
                staticlog.warn("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        XtceDbFactory.remove(instanceName);
        staticlog.info("Re-loading instance '{}'", instanceName);

        ysi.init(getConf(instanceName));
        ysi.startAsync();
        try {
            ysi.awaitRunning();
        } catch (IllegalStateException e) {
            Throwable t = ExceptionUtil.unwind(e.getCause());
            staticlog.warn("Failed to start instance", t);
            throw new UncheckedExecutionException(t);
        }
        return ysi;
    }

    private YConfiguration getConf(String instanceName) {
        File f = new File(instanceDefDir + "/yamcs." + instanceName + ".yaml");
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

        if (ysi.state() != InstanceState.OFFLINE) {
            try {
                ysi.stop();
            } catch (IllegalStateException e) {
                staticlog.error("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        XtceDbFactory.remove(instanceName);
        File f = new File(instanceDefDir + "yamcs." + instanceName + ".yaml");
        if (f.exists()) {
            staticlog.debug("Renaming {} to {}.offline", f.getAbsolutePath(), f.getName());
            f.renameTo(new File(instanceDefDir + "yamcs." + instanceName + ".yaml.offline"));
        }

        return ysi;
    }

    public void removeInstance(String instanceName) {
        stopInstance(instanceName);
        new File(instanceDefDir + "yamcs." + instanceName + ".yaml").delete();
        new File(instanceDefDir + "yamcs." + instanceName + ".yaml.offline").delete();
        instances.remove(instanceName);
    }

    /**
     * Start the instance. If the instance is already started, do nothing.
     * 
     * If the instance is FAILED, restart the instance
     * 
     * If the instance is OFFLINE, rename the &lt;instance&gt;.yaml.offline to &lt;instance&gt;.yaml and start the
     * instance
     * 
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the instance
     * @throws IOException
     */
    public YamcsServerInstance startInstance(String instanceName) throws IOException {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.state() == InstanceState.RUNNING) {
            return ysi;
        } else if (ysi.state() == InstanceState.FAILED) {
            return restartInstance(instanceName);
        }

        if (getNumOnlineInstances() >= maxOnlineInstances) {
            throw new LimitExceededException("Number of online instances already at the limit " + maxOnlineInstances);
        }

        if (ysi.state() == InstanceState.OFFLINE) {
            File f = new File(instanceDefDir + "yamcs." + instanceName + ".yaml.offline");
            if (f.exists()) {
                f.renameTo(new File(instanceDefDir + "yamcs." + instanceName + ".yaml"));
            }
            ysi.init(getConf(instanceName));
        }
        ysi.startAsync();
        ysi.awaitRunning();
        return ysi;
    }

    public Set<Plugin> getPlugins() {
        return plugins;
    }

    /**
     * Creates a new yamcs instance. If conf is not null, the instance is also initialized (but not started)
     * 
     * If the instance already exists a ConfigurationException is thrown
     * 
     * @param name
     *            the name of the new instance
     * 
     * @return the newly created instance
     * @throws IOException
     */
    public synchronized YamcsServerInstance createInstance(String name, YConfiguration conf)
            throws IOException {

        if (instances.containsKey(name)) {
            throw new IllegalArgumentException(String.format("There already exists an instance named '%s'", name));
        }

        if (conf != null) {
            staticlog.info("Loading online instance '{}'", name);
        } else {
            staticlog.debug("Loading offline instance '{}'", name);
        }
        YamcsServerInstance ysi = new YamcsServerInstance(name);
        instances.put(name, ysi);
        if (conf != null) {
            ysi.init(conf);
        }
        ManagementService.getInstance().registerYamcsInstance(ysi);
        return ysi;
    }

    /**
     * Create an instance from a template
     * 
     * @param name
     * @param template
     * @param templateArgs
     * @param labels
     * @return
     */
    public synchronized YamcsServerInstance createInstance(String name, String template,
            Map<String, String> templateArgs, Map<String, String> labels) {
        if (instances.containsKey("name")) {
            throw new IllegalArgumentException(String.format("There already exists an instance named '%s'", name));
        }
        try {
            String tmplResource = "/instance-templates/" + template + "/template.yaml";
            InputStream is = YConfiguration.getResolver().getConfigurationStream(tmplResource);

            StringBuilder buf = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    buf.append(line).append("\n");
                }
            }
            String source = buf.toString();
            String processed = TemplateProcessor.process(source, templateArgs);

            String confFile = instanceDefDir + "yamcs." + name + ".yaml";
            try (FileWriter writer = new FileWriter(confFile)) {
                writer.write(processed);
            }

            String metadataFile = instanceDefDir + "yamcs." + name + ".metadata";
            Yaml yaml = new Yaml();
            try (Writer w = new BufferedWriter(new FileWriter(metadataFile))) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("templateArgs", templateArgs);
                metadata.put("labels", labels);
                yaml.dump(metadata, w);
            }
            FileInputStream fis = new FileInputStream(confFile);
            YConfiguration conf = new YConfiguration("yamcs." + name, fis, confFile);
            fis.close();

            YamcsServerInstance ysi = createInstance(name, conf);
            ysi.setLabels(labels);
            return ysi;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        return new HashSet<>(server.instances.values());
    }

    public YamcsServerInstance getInstance(String yamcsInstance) {
        return instances.get(yamcsInstance);
    }

    public static Set<InstanceTemplate> getInstanceTemplates() {
        return new HashSet<>(server.instanceTemplates.values());
    }

    public InstanceTemplate getInstanceTemplate(String name) {
        return instanceTemplates.get(name);
    }

    private static void printOptionsAndExit() {
        System.err.println("Usage: yamcsd");
        System.err.println("\t All options are read from yamcs.yaml");
        System.exit(-1);
    }

    public static TimeService getTimeService(String yamcsInstance) {
        if (server.instances.containsKey(yamcsInstance)) {
            return server.instances.get(yamcsInstance).getTimeService();
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

    public <T extends Service> List<T> getServices(String yamcsInstance, Class<T> serviceClass) {
        YamcsServerInstance ys = getInstance(yamcsInstance);
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
            throws ConfigurationException, ValidationException, IOException {
        YamcsService service = null;

        // Try first to find just a no-arg constructor. This will become
        // the common case when all services are using the init method.
        if (args instanceof YConfiguration) {
            try {
                service = YObjectLoader.loadObject(serviceClass);
            } catch (ConfigurationException e) {
                // Ignore for now. Fallback to constructor initialization.
            }
        }

        if (service == null) { // "Legacy" fallback
            if (instance != null) {
                if (args == null) {
                    service = YObjectLoader.loadObject(serviceClass, instance);
                } else {
                    service = YObjectLoader.loadObject(serviceClass, instance, args);
                }
            } else {
                if (args == null) {
                    service = YObjectLoader.loadObject(serviceClass);
                } else {
                    service = YObjectLoader.loadObject(serviceClass, args);
                }
            }
        }

        if (args instanceof YConfiguration) {
            try {
                YConfigurationSpec spec = service.specifyArgs();
                if (spec != null) {
                    args = spec.validate((YConfiguration) args);
                }
                staticlog.debug("Intializing {} with resolved args: {}", serviceName, args);
                service.init(instance, (YConfiguration) args);
            } catch (InitException e) { // TODO should add this to throws instead
                throw new ConfigurationException(e);
            }
        }
        return new ServiceWithConfig(service, serviceClass, serviceName, args);
    }

    // starts a service that has stopped or not yet started
    static YamcsService startService(String instance, String serviceName, List<ServiceWithConfig> serviceList)
            throws ConfigurationException, ValidationException, IOException {
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

    public void startGlobalService(String serviceName) throws ConfigurationException, ValidationException, IOException {
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
            YConfiguration.setupDaemon();
            setupYamcsServer();
        } catch (ConfigurationException e) {
            staticlog.error("Could not start Yamcs Server", e);
            System.exit(-1);
        } catch (Exception e) {
            staticlog.error("Could not start Yamcs Server", e);
            System.exit(-1);
        }
    }

    public CrashHandler getCrashHandler(String yamcsInstance) {
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
