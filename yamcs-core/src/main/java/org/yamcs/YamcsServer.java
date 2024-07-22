package org.yamcs;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yamcs.Spec.OptionType;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.ConsoleFormatter;
import org.yamcs.logging.Log;
import org.yamcs.logging.YamcsLogManager;
import org.yamcs.management.ManagementService;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.YamcsInstance.InstanceState;
import org.yamcs.security.CryptoUtils;
import org.yamcs.security.SecurityStore;
import org.yamcs.tctm.Link;
import org.yamcs.templating.ParseException;
import org.yamcs.templating.Template;
import org.yamcs.templating.Variable;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.SDNotify;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yaml.snakeyaml.Yaml;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.util.concurrent.Service.State;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

import io.netty.util.ResourceLeakDetector;

/**
 *
 * Yamcs server together with the global instances
 */
public class YamcsServer {

    private static final String CFG_SERVER_ID_KEY = "serverId";
    private static final String CFG_SECRET_KEY = "secretKey";
    public static final String CFG_CRASH_HANDLER_KEY = "crashHandler";

    public static final String GLOBAL_INSTANCE = "_global";

    private static final Log LOG = new Log(YamcsServer.class);

    private static final Pattern INSTANCE_PATTERN = Pattern.compile("yamcs\\.(.*)\\.yaml(.offline)?");
    private static final YamcsServer YAMCS = new YamcsServer();

    // used to schedule various tasks throughout the yamcs server (to avoid each service creating its own)
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat("YamcsServer-general-executor").build());

    /**
     * During shutdown, allow services this number of seconds for stopping
     */
    public static final int SERVICE_STOP_GRACE_TIME = 10;

    static TimeService realtimeTimeService = new RealtimeTimeService();

    // used for unit tests
    static TimeService mockupTimeService;

    private CrashHandler globalCrashHandler;

    private YamcsServerOptions options = new YamcsServerOptions();
    private Properties properties = new Properties();
    private YConfiguration config;
    private Spec spec;
    private Map<ConfigScope, Map<String, Spec>> sectionSpecs = new HashMap<>();

    private Map<String, CommandOption> commandOptions = new ConcurrentHashMap<>();
    private Set<CommandOptionListener> commandOptionListeners = new CopyOnWriteArraySet<>();

    List<ServiceWithConfig> globalServiceList;
    Map<String, YamcsServerInstance> instances = new LinkedHashMap<>();
    Map<String, Template> instanceTemplates = new HashMap<>();
    List<ReadyListener> readyListeners = new ArrayList<>();

    private SecurityStore securityStore;
    private PluginManager pluginManager;

    private String serverId;
    private byte[] secretKey;
    int maxOnlineInstances = 1000;
    int maxNumInstances = 20;
    @Deprecated
    Path incomingDir;
    Path instanceDefDir;

    // Set when the shutdown hook triggers
    private boolean shuttingDown = false;

    /**
     * Creates services at global (if instance is null) or instance level. The services are not yet initialized. This
     * must be done in a second step, so that components can ask YamcsServer for other service instantiations.
     *
     * @param instance
     *            if null, then start a global service, otherwise an instance service
     * @param services
     *            list of service configuration; each of them is a string (=classname) or a map
     * @param targetLog
     *            the logger to use for any messages
     * @throws IOException
     * @throws ValidationException
     */
    static List<ServiceWithConfig> createServices(String instance, List<YConfiguration> servicesConfig, Log targetLog)
            throws ValidationException, IOException {
        ManagementService managementService = ManagementService.getInstance();
        Set<String> names = new HashSet<>();
        List<ServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for (YConfiguration servconf : servicesConfig) {
            String servclass;
            String name = null;
            servclass = servconf.getString("class");
            YConfiguration args = servconf.getConfigOrEmpty("args");
            name = servconf.getString("name", servclass.substring(servclass.lastIndexOf('.') + 1));
            String candidateName = name;
            int count = 1;
            while (names.contains(candidateName)) {
                candidateName = name + "-" + count;
                count++;
            }
            name = candidateName;
            boolean enabledAtStartup = servconf.getBoolean("enabledAtStartup", true);

            targetLog.info("Loading service {}", name);
            ServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, name, args, enabledAtStartup);
                serviceList.add(swc);
            } catch (NoClassDefFoundError e) {
                targetLog.error("Cannot create service {}, with arguments {}: class {} not found", name, args,
                        e.getMessage());
                throw e;
            } catch (ValidationException e) {
                throw e;
            } catch (Exception e) {
                targetLog.error("Cannot create service {}, with arguments {}: {}", name, args, e.getMessage());
                throw e;
            }
            if (managementService != null) {
                managementService.registerService(instance, name, swc.service);
            }
            names.add(name);
        }

        return serviceList;
    }

    public static void initServices(String instance, List<ServiceWithConfig> services) throws InitException {
        for (ServiceWithConfig swc : services) {
            swc.service.init(instance, swc.name, swc.args);
        }
    }

    public <T extends YamcsService> void addGlobalService(
            String name, Class<T> serviceClass, YConfiguration args) throws ValidationException, InitException {

        for (ServiceWithConfig otherService : YAMCS.globalServiceList) {
            if (otherService.getName().equals(name)) {
                throw new ConfigurationException(String.format(
                        "A service named '%s' already exists", name));
            }
        }

        LOG.info("Loading service {}", name);
        ServiceWithConfig swc = createService(null, serviceClass.getName(), name, args, true);
        swc.service.init(null, name, swc.args);
        YAMCS.globalServiceList.add(swc);

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
            if (!swc.enableAtStartup) {
                LOG.debug("NOT starting service {} because enableAtStartup=false (can be manually started)",
                        swc.getName());
                continue;
            }
            LOG.debug("Starting service {}", swc.getName());
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
        long t0 = System.nanoTime();
        LOG.info("Yamcs is shutting down");
        if (SDNotify.isSupported()) {
            SDNotify.sendStoppingNotification();
        }
        for (YamcsServerInstance ys : instances.values()) {
            ys.stopAsync();
        }
        for (YamcsServerInstance ys : instances.values()) {
            LOG.debug("Awaiting termination of instance {}", ys.getName());
            ys.awaitOffline();
            LOG.info("Stopped instance '{}'", ys.getName());
        }
        if (globalServiceList != null) {
            for (ServiceWithConfig swc : globalServiceList) {
                swc.getService().stopAsync();
            }
            for (ServiceWithConfig swc : globalServiceList) {
                LOG.info("Awaiting termination of service {}", swc.getName());
                swc.getService().awaitTerminated();
            }
        }
        instances.clear();
        YarchDatabase.removeInstance(GLOBAL_INSTANCE);

        // Shutdown database when we're sure no services are using it.
        RdbStorageEngine.getInstance().shutdown();

        long stopTime = System.nanoTime() - t0;

        LOG.info("Yamcs stopped in {}ms", NANOSECONDS.toMillis(stopTime));
        YamcsLogManager.shutdown();
        timer.shutdown();
    }

    public static boolean hasInstance(String instance) {
        return YAMCS.instances.containsKey(instance);
    }

    public boolean hasInstanceTemplate(String template) {
        return instanceTemplates.containsKey(template);
    }

    /**
     * The serverId has to be unique among Yamcs servers connected to eachother.
     * <p>
     * It is used to distinguish the data generated by one particular server.
     *
     * @return
     */
    public String getServerId() {
        return serverId;
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    /**
     * Registers the system-wide availability of a {@link CommandOption}. Command options represent additional arguments
     * that commands may require, but that are not used by Yamcs in building telecommand binary.
     * <p>
     * An example use case would be a custom TC {@link Link} that may support additional arguments for controlling its
     * behaviour.
     * <p>
     * While not enforced we recommend to call this method from a {@link Plugin#onLoad(YConfiguration)} hook as this
     * will avoid registering an option multiple times (attempts to do so would generate an error).
     * 
     * @param option
     *            the new command option.
     */
    @Experimental
    public void addCommandOption(CommandOption option) {
        CommandOption previous = commandOptions.putIfAbsent(option.getId(), option);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "A command option '" + option.getId() + "' was already registered with Yamcs");
        }
        if (PreparedCommand.isReservedColumn(option.getId())) {
            throw new IllegalArgumentException(
                    "Command options may not be named '" + option.getId() + "'. This name is reserved");
        }
        commandOptionListeners.forEach(l -> l.commandOptionAdded(option));
    }

    /**
     * Returns the command options registered to this instance.
     */
    public Collection<CommandOption> getCommandOptions() {
        return commandOptions.values();
    }

    public boolean hasCommandOption(String id) {
        return commandOptions.containsKey(id);
    }

    public CommandOption getCommandOption(String id) {
        return commandOptions.get(id);
    }

    public void addCommandOptionListener(CommandOptionListener listener) {
        commandOptionListeners.add(listener);
    }

    public void removeCommandOptionListener(CommandOptionListener listener) {
        commandOptionListeners.remove(listener);
    }

    /**
     * Returns the main Yamcs configuration
     */
    public YConfiguration getConfig() {
        return config;
    }

    /**
     * Returns the configuration specification for the config returned by {@link #getConfig()}.
     */
    public Spec getSpec() {
        return spec;
    }

    private int getOnlineInstanceCount() {
        return (int) instances.values().stream().filter(ysi -> ysi.state() != InstanceState.OFFLINE).count();
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
                LOG.warn("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        MdbFactory.remove(instanceName);
        StreamConfig.removeInstance(instanceName);
        LOG.info("Re-loading instance '{}'", instanceName);

        YConfiguration instanceConfig = loadInstanceConfig(instanceName);
        ysi.init(instanceConfig);
        ysi.startAsync();
        try {
            ysi.awaitRunning();
        } catch (IllegalStateException e) {
            Throwable t = ExceptionUtil.unwind(e.getCause());
            LOG.warn("Failed to start instance", t);
            throw new UncheckedExecutionException(t);
        }
        return ysi;
    }

    private YConfiguration loadInstanceConfig(String instanceName) {
        Path configFile = instanceDefDir.resolve(configFileName(instanceName));
        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                String confPath = configFile.toAbsolutePath().toString();
                return new YConfiguration("yamcs." + instanceName, is, confPath);
            } catch (IOException e) {
                throw new ConfigurationException("Cannot load configuration from " + configFile.toAbsolutePath(), e);
            }
        } else {
            return YConfiguration.getConfiguration("yamcs." + instanceName);
        }
    }

    private InstanceMetadata loadInstanceMetadata(String instanceName) throws IOException {
        Path metadataFile = instanceDefDir.resolve("yamcs." + instanceName + ".metadata");
        if (Files.exists(metadataFile)) {
            try (InputStream in = Files.newInputStream(metadataFile)) {
                Map<String, Object> map = new Yaml().loadAs(in, Map.class);
                return new InstanceMetadata(map);
            }
        }
        return new InstanceMetadata();
    }

    /**
     * Stop the instance (it will be offline after this)
     * 
     * @param instanceName
     *            the name of the instance
     * 
     * @return the instance
     * @throws IOException
     */
    public YamcsServerInstance stopInstance(String instanceName) throws IOException {
        YamcsServerInstance ysi = instances.get(instanceName);

        if (ysi.state() != InstanceState.OFFLINE) {
            try {
                ysi.stop();
            } catch (IllegalStateException e) {
                LOG.error("Instance did not terminate normally", e);
            }
        }
        YarchDatabase.removeInstance(instanceName);
        MdbFactory.remove(instanceName);
        Path f = instanceDefDir.resolve(configFileName(instanceName));
        if (Files.exists(f)) {
            LOG.debug("Renaming {} to {}.offline", f.toAbsolutePath(), f.getFileName());
            Files.move(f, f.resolveSibling(configFileName(instanceName) + ".offline"));
        }

        return ysi;
    }

    public void removeInstance(String instanceName) throws IOException {
        stopInstance(instanceName);
        Files.deleteIfExists(instanceDefDir.resolve(configFileName(instanceName)));
        Files.deleteIfExists(instanceDefDir.resolve(configFileName(instanceName) + ".offline"));
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

        if (getOnlineInstanceCount() >= maxOnlineInstances) {
            throw new LimitExceededException("Number of online instances already at the limit " + maxOnlineInstances);
        }

        if (ysi.state() == InstanceState.OFFLINE) {
            Path f = instanceDefDir.resolve(configFileName(instanceName) + ".offline");
            if (Files.exists(f)) {
                Files.move(f, instanceDefDir.resolve(configFileName(instanceName)));
            }
            YConfiguration instanceConfig = loadInstanceConfig(instanceName);
            ysi.init(instanceConfig);
        }
        ysi.startAsync();
        ysi.awaitRunning();
        return ysi;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Add the definition of an additional configuration section to the root Yamcs spec (yamcs.yaml).
     * 
     * @param key
     *            the name of this section. This represent a direct subkey of the main app config
     * @param spec
     *            the specification of this configuration section.
     */
    public void addConfigurationSection(String key, Spec spec) {
        addConfigurationSection(ConfigScope.YAMCS, key, spec);
    }

    /**
     * Add the definition of an additional configuration section to a particulat configuration type
     * 
     * @param scope
     *            the scope where this section belongs. When using file-based configuration this can be thought of as
     *            the type of the configuration file.
     * @param key
     *            the name of this section. This represent a direct subkey of the main app config
     * @param spec
     *            the specification of this configuration section.
     */
    public void addConfigurationSection(ConfigScope scope, String key, Spec spec) {
        Map<String, Spec> specs = sectionSpecs.computeIfAbsent(scope, x -> new HashMap<>());
        specs.put(key, spec);
    }

    public Map<String, Spec> getConfigurationSections(ConfigScope scope) {
        Map<String, Spec> specs = sectionSpecs.get(scope);
        return specs != null ? specs : Collections.emptyMap();
    }

    /**
     * Creates a new yamcs instance.
     * 
     * If the instance already exists an IllegalArgumentException is thrown
     * 
     * @param name
     *            the name of the new instance
     * 
     * @param metadata
     *            the metadata associated to this instance (labels or other attributes)
     * @param offline
     *            if true, the instance will be created offline and it does not need a config
     * @param config
     *            the configuration for this instance (equivalent of yamcs.instance.yaml)
     * @return the newly created instance
     */
    public synchronized YamcsServerInstance addInstance(String name, InstanceMetadata metadata, boolean offline,
            YConfiguration config) {
        if (instances.containsKey(name)) {
            throw new IllegalArgumentException(String.format("There already exists an instance named '%s'", name));
        }
        LOG.info("Loading {} instance '{}'", offline ? "offline" : "online", name);
        YamcsServerInstance ysi = new YamcsServerInstance(name, metadata);

        ysi.addStateListener(new InstanceStateListener() {
            @Override
            public void failed(Throwable failure) {
                LOG.error("Instance {} failed", name, ExceptionUtil.unwind(failure));
            }
        });

        instances.put(name, ysi);
        if (!offline) {
            ysi.init(config);
        }

        ManagementService.getInstance().registerYamcsInstance(ysi);
        return ysi;
    }

    /**
     * Create a new instance based on a template.
     * 
     * @param name
     *            the name of the instance
     * @param templateName
     *            the name of an available template
     * @param templateArgs
     *            arguments to use while processing the template
     * @param labels
     *            labels associated to this instance
     * @param customMetadata
     *            custom metadata associated with this instance.
     * @throws IOException
     *             when a disk operation failed
     * @return the newly create instance
     */
    public synchronized YamcsServerInstance createInstance(String name, String templateName,
            Map<String, Object> templateArgs, Map<String, String> labels, Map<String, Object> customMetadata)
            throws IOException {
        if (instances.containsKey(name)) {
            throw new IllegalArgumentException(String.format("There already exists an instance named '%s'", name));
        }
        if (!instanceTemplates.containsKey(templateName)) {
            throw new IllegalArgumentException(String.format("Unknown template '%s'", templateName));
        }

        Template template = instanceTemplates.get(templateName);

        // Build instance metadata as a combination of internal properties and custom metadata from the caller
        InstanceMetadata metadata = new InstanceMetadata();
        metadata.setTemplate(templateName);
        metadata.setTemplateArgs(templateArgs);
        metadata.setTemplateSource(template.getSource());
        metadata.setLabels(labels);
        customMetadata.forEach((k, v) -> metadata.put(k, v));

        String processed = template.process(metadata.getTemplateArgs());

        Path confFile = instanceDefDir.resolve(configFileName(name));
        try (Writer writer = Files.newBufferedWriter(confFile)) {
            writer.write(processed);
        }

        Path metadataFile = instanceDefDir.resolve("yamcs." + name + ".metadata");
        try (Writer writer = Files.newBufferedWriter(metadataFile)) {
            Map<String, Object> metadataMap = metadata.toMap();
            new Yaml().dump(metadataMap, writer);
        }

        YConfiguration instanceConfig;
        try (InputStream fis = Files.newInputStream(confFile)) {
            String subSystem = "yamcs." + name;
            String confPath = confFile.toString();
            instanceConfig = new YConfiguration(subSystem, fis, confPath);
        }

        return addInstance(name, metadata, false, instanceConfig);
    }

    public synchronized YamcsServerInstance reconfigureInstance(String name, Map<String, Object> templateArgs,
            Map<String, String> labels) throws IOException {
        YamcsServerInstance ysi = instances.get(name);
        if (ysi == null) {
            throw new IllegalArgumentException(String.format("Unknown instance '%s'", name));
        }

        String templateName = ysi.getTemplate();
        if (!instanceTemplates.containsKey(templateName)) {
            throw new IllegalArgumentException(String.format("Unknown template '%s'", templateName));
        }
        Template template = instanceTemplates.get(templateName);

        // Build instance metadata as a combination of internal properties and custom metadata from the caller
        InstanceMetadata metadata = ysi.metadata;
        metadata.setLabels(labels);
        metadata.setTemplateArgs(templateArgs);
        metadata.setTemplateSource(template.getSource());

        String processed = template.process(metadata.getTemplateArgs());

        Path confFile = instanceDefDir.resolve(configFileName(name));
        try (Writer writer = Files.newBufferedWriter(confFile)) {
            writer.write(processed);
        }

        Path metadataFile = instanceDefDir.resolve("yamcs." + name + ".metadata");
        try (Writer writer = Files.newBufferedWriter(metadataFile)) {
            Map<String, Object> metadataMap = metadata.toMap();
            new Yaml().dump(metadataMap, writer);
        }

        return ysi;
    }

    private String deriveServerId() {
        try {
            String id;
            if (config.containsKey(CFG_SERVER_ID_KEY)) {
                id = config.getString(CFG_SERVER_ID_KEY);
            } else {
                id = InetAddress.getLocalHost().getHostName();
            }
            serverId = id;
            LOG.debug("Using serverId {}", serverId);
            return serverId;
        } catch (ConfigurationException e) {
            throw e;
        } catch (UnknownHostException e) {
            String msg = "Cannot resolve local host. Make sure it's defined properly or alternatively add 'serverId: <name>' to yamcs.yaml";
            LOG.warn(msg);
            throw new ConfigurationException(msg, e);
        }
    }

    private void deriveSecretKey() {
        if (config.containsKey(CFG_SECRET_KEY)) {
            // Should maybe only allow base64 encoded secret keys
            secretKey = config.getString(CFG_SECRET_KEY).getBytes(StandardCharsets.UTF_8);
        } else {
            LOG.warn("Generating random non-persisted secret key."
                    + " Cryptographic verifications will not work across server restarts."
                    + " Set 'secretKey: <secret>' in yamcs.yaml to avoid this message.");
            secretKey = CryptoUtils.generateRandomSecretKey();
        }
    }

    public static List<YamcsServerInstance> getInstances() {
        return new ArrayList<>(YAMCS.instances.values());
    }

    public YamcsServerInstance getInstance(String yamcsInstance) {
        return instances.get(yamcsInstance);
    }

    public Set<Template> getInstanceTemplates() {
        return new HashSet<>(instanceTemplates.values());
    }

    public Template getInstanceTemplate(String name) {
        return instanceTemplates.get(name);
    }

    /**
     * Returns the time service for a given instance
     */
    public static TimeService getTimeService(String yamcsInstance) {
        if (YAMCS.instances.containsKey(yamcsInstance)) {
            return YAMCS.instances.get(yamcsInstance).getTimeService();
        } else {
            if (mockupTimeService != null) {
                return mockupTimeService;
            } else {
                return realtimeTimeService; // happens from unit tests
            }
        }
    }

    public SecurityStore getSecurityStore() {
        return securityStore;
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

    /**
     * Returns the service matching the specified class.
     * <p>
     * This method requires that there be only one matching service, else it will throw an exception.
     * 
     * @return The matching singleton service, else {@code null}.
     * @throws IllegalStateException
     *             There is more than one matching service.
     */
    public <T extends YamcsService> T getService(String yamcsInstance, Class<T> serviceClass) {
        List<T> services = getServices(yamcsInstance, serviceClass);
        if (services.size() == 1) {
            return services.get(0);
        } else if (services.size() > 2) {
            throw new IllegalStateException(serviceClass.getName() + " is not a singleton service");
        } else {
            return null;
        }
    }

    public <T extends YamcsService> List<T> getServices(String yamcsInstance, Class<T> serviceClass) {
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

    /**
     * Returns the global service matching the specified class.
     * <p>
     * This method requires that there be only one matching service, else it will throw an exception.
     * 
     * @return The matching singleton service, else {@code null}.
     * @throws IllegalStateException
     *             There is more than one matching service.
     */
    public <T extends YamcsService> T getGlobalService(Class<T> serviceClass) {
        List<T> services = getGlobalServices(serviceClass);
        if (services.size() == 1) {
            return services.get(0);
        } else if (services.size() > 2) {
            throw new IllegalStateException(serviceClass.getName() + " is not a singleton service");
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends YamcsService> List<T> getGlobalServices(Class<T> serviceClass) {
        List<T> services = new ArrayList<>();
        if (globalServiceList != null) {
            for (ServiceWithConfig swc : globalServiceList) {
                if (serviceClass.isInstance(swc.service)) {
                    services.add((T) swc.service);
                }
            }
        }
        return services;
    }

    static ServiceWithConfig createService(String instance, String serviceClass, String serviceName,
            YConfiguration args, boolean enabledAtStartup)
            throws ConfigurationException, ValidationException {
        YamcsService service = null;

        service = YObjectLoader.loadObject(serviceClass);

        if (args instanceof YConfiguration) {
            // try {
            Spec spec = service.getSpec();
            if (spec != null) {
                if (LOG.isDebugEnabled()) {
                    Map<String, Object> unsafeArgs = ((YConfiguration) args).getRoot();
                    Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                    LOG.debug("Raw args for {}: {}", serviceName, safeArgs);
                }

                args = spec.validate((YConfiguration) args);

                if (LOG.isDebugEnabled()) {
                    Map<String, Object> unsafeArgs = ((YConfiguration) args).getRoot();
                    Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                    LOG.debug("Initializing {} with resolved args: {}", serviceName, safeArgs);
                }
            }
            // service.init(instance, serviceName, (YConfiguration) args);
            // } catch (InitException e) { // TODO should add this to throws instead
            // throw new ConfigurationException(e);
            // }
        }
        return new ServiceWithConfig(service, serviceClass, serviceName, args, enabledAtStartup);
    }

    // starts a service that has stopped or not yet started
    static YamcsService startService(String instance, String serviceName, List<ServiceWithConfig> serviceList)
            throws ConfigurationException, ValidationException, InitException {
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
                    swc = createService(instance, swc.serviceClass, serviceName, swc.args, swc.enableAtStartup);
                    swc.service.init(instance, swc.getName(), swc.args);
                    serviceList.set(i, swc);
                    swc.service.startAsync();
                    break;
                }
                return swc.service;
            }
        }
        return null;
    }

    public void startGlobalService(String serviceName)
            throws ConfigurationException, ValidationException, InitException {
        startService(null, serviceName, globalServiceList);
    }

    public CrashHandler getCrashHandler(String yamcsInstance) {
        YamcsServerInstance ys = getInstance(yamcsInstance);
        if (ys != null) {
            return ys.getCrashHandler();
        } else {
            return globalCrashHandler; // may happen if the instance name is not valid (in unit tests)
        }
    }

    public CrashHandler getGlobalCrashHandler() {
        return globalCrashHandler;
    }

    public Path getConfigDirectory() {
        return options.configDirectory;
    }

    public Path getDataDirectory() {
        return options.dataDir;
    }

    /**
     * Path of the Yamcs incoming directory. This global option is deprecated. Links that need an incoming directory,
     * should read this information directly from that link's configuration.
     */
    @Deprecated
    public Path getIncomingDirectory() {
        return incomingDir;
    }

    public Path getCacheDirectory() {
        return options.cacheDir;
    }

    /**
     * Register a listener that will be called when Yamcs has fully started. If you register a listener after Yamcs has
     * already started, your callback will not be executed.
     */
    public void addReadyListener(ReadyListener readyListener) {
        readyListeners.add(readyListener);
    }

    /**
     * @return the (singleton) server
     */
    public static YamcsServer getServer() {
        return YAMCS;
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();

        // Run jcommander before setting up logging.
        // We want this to use standard streams.
        parseArgs(args);

        System.setProperty("jxl.nowarnings", "true");
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            System.setProperty("javax.net.ssl.trustStore",
                    YAMCS.options.configDirectory.resolve("trustStore").toString());
        }

        try {
            setupLogging();

            // Bootstrap YConfiguration such that it only considers physical files.
            // Not classpath resources.
            YConfiguration.setResolver(new FileBasedConfigurationResolver(YAMCS.options.configDirectory));

            // Properties of the form ${foo} will be expanded based on the presence of application.properties,
            // or just default to java system properties.
            var propertiesFile = YAMCS.options.configDirectory.resolve("application.properties");
            if (Files.exists(propertiesFile)) {
                try (var fileIn = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                    YAMCS.properties.load(fileIn);
                }
            }
            YConfiguration.setPropertyProvider(propertyName -> {
                var value = YAMCS.properties.getProperty(propertyName);
                return value != null ? value : System.getProperty(propertyName);
            });

            YAMCS.prepareStart();
        } catch (Exception e) {
            Throwable t = ExceptionUtil.unwind(e);
            if (t instanceof ValidationException) {
                String path = ((ValidationException) t).getContext().getPath();
                LOG.error("{}: {}", path, e.getMessage());
                if (YAMCS.options.check) {
                    System.out.println("Configuration Invalid");
                }
                RdbStorageEngine.getInstance().shutdown();
                YamcsLogManager.shutdown();
                System.exit(-1);
            } else {
                LOG.error("Failure while attempting to validate configuration", t);
                RdbStorageEngine.getInstance().shutdown();
                YamcsLogManager.shutdown();
                System.exit(-1);
            }
        }

        if (YAMCS.options.check) {
            System.out.println("Configuration OK");
            System.exit(0);
        }

        ResourceLeakDetector.setLevel(YAMCS.options.nettyLeakDetection);
        if (ResourceLeakDetector.isEnabled()) {
            LOG.info("Netty leak detection: " + ResourceLeakDetector.getLevel());
        }

        // Good to go!
        try {
            LOG.info("Yamcs {}, build {}", YamcsVersion.VERSION, YamcsVersion.REVISION);
            YAMCS.start();
            YAMCS.reportReady(System.nanoTime() - t0);
        } catch (Exception e) {
            LOG.error("Could not start Yamcs", ExceptionUtil.unwind(e));
            System.exit(-1);
        }
    }

    private static void parseArgs(String[] args) {
        try {
            JCommander jcommander = new JCommander(YAMCS.options);
            jcommander.setProgramName("yamcsd");
            jcommander.parse(args);
            if (YAMCS.options.help) {
                jcommander.usage();
                System.exit(0);
            } else if (YAMCS.options.version) {
                System.out.println("Yamcs " + YamcsVersion.VERSION + ", build " + YamcsVersion.REVISION);
                PluginManager pluginManager = new PluginManager();
                for (Plugin plugin : ServiceLoader.load(Plugin.class)) {
                    PluginMetadata meta = pluginManager.getMetadata(plugin.getClass());
                    System.out.println(meta.getName() + " " + meta.getVersion());
                }
                System.exit(0);
            }
        } catch (ParameterException | IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private static void setupLogging() throws SecurityException, IOException {
        if (YAMCS.options.check) {
            Log.forceStandardStreams(Level.WARNING);
            return;
        }

        if (System.getProperty("java.util.logging.config.file") != null) {
            LOG.info("Logging configuration overriden via java property");
        } else {
            Path configFile = YAMCS.options.configDirectory.resolve("logging.properties").toAbsolutePath();
            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    YamcsLogManager.setup(in);
                    LOG.info("Logging enabled using {}", configFile);
                }
            } else {
                setupDefaultLogging();
            }
        }

        // Intercept stdout/stderr for sending to the log system. Only catches line-terminated
        // string, but this should cover most uses cases.
        // NOTE: stream redirect gets disabled on shutdown, so keep the check dynamic.
        Logger stdoutLogger = Logger.getLogger("stdout");
        System.setOut(new PrintStream(System.out) {
            @Override
            public void println(String x) {
                if (YAMCS.options.noStreamRedirect) {
                    super.println(x);
                } else {
                    stdoutLogger.info(x);
                }
            }

            @Override
            public void println(Object x) {
                if (YAMCS.options.noStreamRedirect) {
                    super.println(x);
                } else {
                    stdoutLogger.info(String.valueOf(x));
                }
            }
        });
        Logger stderrLogger = Logger.getLogger("stderr");
        System.setErr(new PrintStream(System.err) {
            @Override
            public void println(String x) {
                if (YAMCS.options.noStreamRedirect) {
                    super.println(x);
                } else {
                    stderrLogger.severe(x);
                }
            }

            @Override
            public void println(Object x) {
                if (YAMCS.options.noStreamRedirect) {
                    super.println(x);
                } else {
                    stdoutLogger.info(String.valueOf(x));
                }
            }
        });
    }

    private static void setupDefaultLogging() throws SecurityException, IOException {
        Level logLevel = toLevel(YAMCS.options.verbose);

        // Not sure. This seems to be the best programmatic way. Changing Logger
        // instances directly only works on the weak instance.
        String defaultHandler = ConsoleHandler.class.getName();
        String defaultFormatter = ConsoleFormatter.class.getName();
        StringBuilder buf = new StringBuilder();
        buf.append("handlers=").append(defaultHandler).append("\n");
        buf.append(defaultHandler).append(".level=").append(logLevel).append("\n");
        buf.append(defaultHandler).append(".formatter=").append(defaultFormatter).append("\n");

        if (YAMCS.options.logConfig != null) {
            try (InputStream in = Files.newInputStream(YAMCS.options.logConfig)) {
                Properties props = new Properties();
                props.load(in);
                props.forEach((logger, verbosity) -> {
                    Level loggerLevel = toLevel(Integer.parseInt((String) verbosity));
                    buf.append(logger).append(".level=").append(loggerLevel).append("\n");
                });
            }
        } else {
            // This sets up the level for *everything*.
            buf.append(".level=").append(logLevel);
        }

        try (InputStream in = new ByteArrayInputStream(buf.toString().getBytes())) {
            YamcsLogManager.setup(in);
        }
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            Formatter formatter = handler.getFormatter();
            if (formatter instanceof ConsoleFormatter) {
                ((ConsoleFormatter) formatter).setEnableAnsiColors(!YAMCS.options.noColor);
            }
        }
    }

    private static Level toLevel(int verbosity) {
        switch (verbosity) {
        case 0:
            return Level.OFF;
        case 1:
            return Level.WARNING;
        case 2:
            return Level.INFO;
        case 3:
            return Level.FINE;
        default:
            return Level.ALL;
        }
    }

    public void prepareStart() throws ValidationException, IOException, InitException {
        if (timer.isShutdown()) {// happening in unit tests
            timer = new ScheduledThreadPoolExecutor(1,
                    new ThreadFactoryBuilder().setNameFormat("YamcsServer-general-executor").build());
        }

        ManagementService.getInstance().init();

        pluginManager = new PluginManager();
        pluginManager.discoverPlugins();

        // Load the UTC-TAI.history file.
        // Give priority to a file in etc folder.
        Path utcTaiFile = options.configDirectory.resolve("UTC-TAI.history");
        if (Files.exists(utcTaiFile)) {
            try (InputStream in = Files.newInputStream(utcTaiFile)) {
                TimeEncoding.setUp(in);
            }
        } else {
            // Default to a bundled version from classpath
            TimeEncoding.setUp();
        }

        validateMainConfiguration();
        discoverTemplates();

        // Prevent RDBFactory from installing shutdown hooks, shutdown is organised by YamcsServer.
        RDBFactory.setRegisterShutdownHooks(false);

        // Create also services and instances so that they can validate too.
        addGlobalServicesAndInstances();
    }

    public void validateMainConfiguration() throws ValidationException {
        Spec serviceSpec = new Spec();
        serviceSpec.addOption("class", OptionType.STRING).withRequired(true);
        serviceSpec.addOption("args", OptionType.ANY);
        serviceSpec.addOption("name", OptionType.STRING);
        serviceSpec.addOption("enabledAtStartup", OptionType.BOOLEAN);

        Spec bucketSpec = new Spec();
        bucketSpec.addOption("name", OptionType.STRING).withRequired(true);
        bucketSpec.addOption("path", OptionType.STRING);
        bucketSpec.addOption("maxSize", OptionType.INTEGER);
        bucketSpec.addOption("maxObjects", OptionType.INTEGER);

        spec = new Spec();
        spec.addOption("services", OptionType.LIST).withElementType(OptionType.MAP)
                .withSpec(serviceSpec);
        spec.addOption("instances", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("buckets", OptionType.LIST).withElementType(OptionType.MAP)
                .withSpec(bucketSpec);
        spec.addOption("dataDir", OptionType.STRING).withDefault("yamcs-data");
        spec.addOption("cacheDir", OptionType.STRING).withDefault("cache");
        spec.addOption("incomingDir", OptionType.STRING).withDefault("yamcs-incoming")
                .withDeprecationMessage("remove \"incomingDir\" property from yamcs.yaml. "
                        + "Links that were using this option, should instead provide a link-specific option");
        spec.addOption(CFG_SERVER_ID_KEY, OptionType.STRING);
        spec.addOption(CFG_SECRET_KEY, OptionType.STRING).withSecret(true);
        spec.addOption("disabledPlugins", OptionType.LIST).withElementType(OptionType.STRING)
                .withDeprecationMessage("use: \"enabled\" property inside the plugin's configuration section instead");
        spec.addOption("archive", OptionType.ANY);
        spec.addOption("rdbConfig", OptionType.ANY);

        Map<String, Spec> extraSections = getConfigurationSections(ConfigScope.YAMCS);
        extraSections.forEach((key, sectionSpec) -> {
            spec.addOption(key, OptionType.MAP).withSpec(sectionSpec)
                    .withApplySpecDefaults(true);
        });

        config = YConfiguration.getConfiguration("yamcs");
        config = spec.validate(config);
    }

    public void start() throws PluginException {
        // Before starting anything, register a shutdown hook that will attempt graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shuttingDown = true;
                shutDown();
            }
        });

        pluginManager.loadPlugins();
        startServices();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            String msg = String.format("Uncaught exception '%s' in thread %s: %s", e, t,
                    Arrays.toString(e.getStackTrace()));
            LOG.error(msg);
            globalCrashHandler.handleCrash("UncaughtException", msg);
        });
    }

    /**
     * Returns true when Yamcs is shutting down.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    private void discoverTemplates() throws IOException {
        Path templatesDir = options.configDirectory.resolve("instance-templates");
        if (!Files.exists(templatesDir)) {
            return;
        }

        try (Stream<Path> dirStream = Files.list(templatesDir)) {
            dirStream.filter(Files::isDirectory).forEach(p -> {
                Path templateFile = p.resolve("template.yaml");
                if (Files.exists(templateFile)) {
                    try {
                        String name = p.getFileName().toString();
                        String source = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
                        Template template = new Template(name, source);

                        Path metaFile = p.resolve("meta.yaml");
                        Map<String, Object> metaDef = new HashMap<>();
                        if (Files.exists(metaFile)) {
                            try (InputStream in = Files.newInputStream(metaFile)) {
                                metaDef = new Yaml().load(in);
                            }
                        }

                        template.setDescription(YConfiguration.getString(metaDef, "description", null));
                        if (metaDef.containsKey("variables")) {
                            List<Map<String, Object>> varDefs = YConfiguration.getList(metaDef, "variables");
                            for (Map<String, Object> varDef : varDefs) {
                                String type = (String) varDef.getOrDefault("type", Variable.class.getName());
                                Variable variable = YObjectLoader.loadObject(type);
                                variable.setName(YConfiguration.getString(varDef, "name"));
                                variable.setLabel(YConfiguration.getString(varDef, "label", null));
                                variable.setRequired(YConfiguration.getBoolean(varDef, "required", true));
                                variable.setHelp(YConfiguration.getString(varDef, "help", null));
                                variable.setInitial(YConfiguration.getString(varDef, "initial", null));
                                if (varDef.containsKey("choices")) {
                                    variable.setChoices(YConfiguration.getList(varDef, "choices"));
                                }
                                template.addVariable(variable);
                            }
                        }

                        addInstanceTemplate(template);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (ParseException e) {
                        throw new ConfigurationException(e);
                    }
                }
            });
        }
    }

    public void addInstanceTemplate(Template template) {
        instanceTemplates.put(template.getName(), template);
    }

    public void addGlobalServicesAndInstances() throws IOException, ValidationException, InitException {
        serverId = deriveServerId();
        deriveSecretKey();

        if (config.containsKey("crashHandler")) {
            globalCrashHandler = loadCrashHandler(config);
        } else {
            globalCrashHandler = new LogCrashHandler();
        }
        if (options.dataDir == null) {
            options.dataDir = Path.of(config.getString("dataDir"));
        }
        YarchDatabase.setHome(options.dataDir.toString());
        incomingDir = Path.of(config.getString("incomingDir"));
        instanceDefDir = options.dataDir.resolve("instance-def");

        if (YConfiguration.configDirectory != null) {
            options.cacheDir = YConfiguration.configDirectory.toPath().toAbsolutePath();
        } else if (options.cacheDir == null) {
            options.cacheDir = Path.of(config.getString("cacheDir")).toAbsolutePath();
        }
        Files.createDirectories(options.cacheDir);

        Path globalDir = options.dataDir.resolve(GLOBAL_INSTANCE);
        Files.createDirectories(globalDir);
        Files.createDirectories(instanceDefDir);

        if (config.containsKey("services")) {
            List<YConfiguration> services = config.getServiceConfigList("services");
            globalServiceList = createServices(null, services, LOG);
            initServices(null, globalServiceList);
        }

        try {
            securityStore = new SecurityStore();
            LOG.debug("Security: " + (securityStore.isEnabled() ? "enabled" : "disabled"));
        } catch (InitException e) {
            if (e.getCause() instanceof ValidationException) {
                throw (ValidationException) e.getCause();
            }
            throw new ConfigurationException(e);
        }

        // Load user-configured instances. These are the ones that are explictly mentioned in yamcs.yaml
        int instanceCount = 0;
        if (config.containsKey("instances")) {
            for (String name : config.<String> getList("instances")) {
                if (instances.containsKey(name)) {
                    throw new ConfigurationException("Duplicate instance specified: '" + name + "'");
                }
                YConfiguration instanceConfig = YConfiguration.getConfiguration("yamcs." + name);
                addInstance(name, new InstanceMetadata(), false, instanceConfig);
                instanceCount++;
            }
        }

        // Load instances saved in storage
        try (Stream<Path> paths = Files.list(instanceDefDir)) {
            for (Path instanceDir : paths.collect(Collectors.toList())) {
                String dirname = instanceDir.getFileName().toString();
                Matcher m = INSTANCE_PATTERN.matcher(dirname);
                if (!m.matches()) {
                    continue;
                }

                String instanceName = m.group(1);
                boolean online = m.group(2) == null;
                if (online) {
                    instanceCount++;
                    if (instanceCount > maxOnlineInstances) {
                        throw new ConfigurationException("Instance limit exceeded: " + instanceCount);
                    }
                    YConfiguration instanceConfig = loadInstanceConfig(instanceName);
                    InstanceMetadata instanceMetadata = loadInstanceMetadata(instanceName);
                    addInstance(instanceName, instanceMetadata, false, instanceConfig);
                } else {
                    if (instances.size() > maxNumInstances) {
                        LOG.warn("Number of instances exceeds the maximum {}, offline instance {} not loaded",
                                maxNumInstances, instanceName);
                        continue;
                    }
                    InstanceMetadata instanceMetadata = loadInstanceMetadata(instanceName);
                    addInstance(instanceName, instanceMetadata, true, null);
                }
            }
        }
    }

    static CrashHandler loadCrashHandler(YConfiguration config) throws IOException {
        if (config.containsKey("crashHandler", "args")) {
            return YObjectLoader.loadObject(config.getSubString("crashHandler", "class"),
                    config.getSubMap("crashHandler", "args"));
        } else {
            return YObjectLoader.loadObject(config.getSubString("crashHandler", "class"));
        }
    }

    private void startServices() {
        if (globalServiceList != null) {
            startServices(globalServiceList);
        }

        for (YamcsServerInstance ysi : instances.values()) {
            if (ysi.state() != InstanceState.OFFLINE) {
                ysi.startAsync();
            }
        }
    }

    private void reportReady(long bootTime) throws IOException {
        int instanceCount = getOnlineInstanceCount();
        int serviceCount = globalServiceList.size() + getInstances().stream()
                .map(instance -> instance.services != null ? instance.services.size() : 0)
                .reduce(0, Integer::sum);

        String msg = String.format("Yamcs started in %dms. Started %d of %d instances and %d services",
                NANOSECONDS.toMillis(bootTime), instanceCount, instances.size(), serviceCount);

        if (options.noStreamRedirect) {
            System.out.println(msg);
        } else {
            // Associate the message with a specific logger
            LOG.info(msg);
        }

        if (SDNotify.isSupported()) {
            SDNotify.sendStartupNotification();
        }

        // Report start success to internal listeners
        readyListeners.forEach(ReadyListener::onReady);
    }

    public Processor getProcessor(String yamcsInstance, String processorName) {
        YamcsServerInstance ysi = getInstance(yamcsInstance);
        if (ysi == null) {
            return null;
        }
        return ysi.getProcessor(processorName);
    }

    public ScheduledThreadPoolExecutor getThreadPoolExecutor() {
        return timer;
    }

    static String configFileName(String yamcsInstance) {
        return "yamcs." + yamcsInstance + ".yaml";
    }
}
