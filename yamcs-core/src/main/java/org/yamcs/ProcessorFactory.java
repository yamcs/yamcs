package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.logging.Log;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.YObjectLoader;

/**
 * Used to create processors as defined in processor.yaml
 *
 * @author nm
 *
 */
public class ProcessorFactory {

    /**
     * Returns the processor types as defined in {@code processor.yaml}
     */
    public static Map<String, ProcessorConfig> getProcessorTypes() {
        if (!YConfiguration.isDefined("processor")) {
            return Collections.emptyMap();
        }
        YConfiguration conf = YConfiguration.getConfiguration("processor");
        Map<String, ProcessorConfig> result = new HashMap<>();
        for (String processorName : conf.getKeys()) {
            YConfiguration processorConfig = conf.getConfig(processorName).getConfigOrEmpty("config");
            result.put(processorName, new ProcessorConfig(processorConfig));
        }
        return result;
    }

    /**
     * Create a processor with the give name, type, creator and spec
     *
     * type is used to load the tm, parameter and command classes as defined in processor.yaml spec if not null is
     * passed as an extra argument to those classes - it is used for example when creating replay processors to pass on
     * the data that has to be replayed. should probably be changed from string to some sort of object.
     *
     * @param yamcsInstance
     * @param name
     * @param type
     * @param creator
     * @param spec
     * @return a new processor
     * @throws ProcessorException
     * @throws ConfigurationException
     * @throws ValidationException
     */
    public static Processor create(String yamcsInstance, String name, String type, String creator, Object spec)
            throws ProcessorException, ConfigurationException, ValidationException, InitException {
        YConfiguration pc = null;
        YConfiguration conf = YConfiguration.getConfiguration("processor");

        List<ProcessorServiceWithConfig> serviceList;
        try {
            if (!conf.containsKey(type)) {
                throw new ConfigurationException("No processor type '" + type + "' found in " + conf.getPath());
            }
            conf = conf.getConfig(type);
            Log targetLog = new Log(ProcessorFactory.class, yamcsInstance);
            targetLog.setContext(name);
            serviceList = createServices(yamcsInstance, conf.getServiceConfigList("services"), targetLog);

            pc = conf.getConfigOrEmpty("config");
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load service", e);
        }
        pc = ProcessorConfig.getSpec().validate(pc);
        ProcessorConfig processorConfig = new ProcessorConfig(pc);
        return create(yamcsInstance, name, type, serviceList, creator, processorConfig, spec);
    }

    /**
     * Create a Processor by specifying the service.
     * 
     * 
     * @param instance
     * @param name
     * @param type
     * @param creator
     * @param config
     * @return
     * @throws ProcessorException
     * @throws ConfigurationException
     * @throws ValidationException
     **/
    public static Processor create(String instance, String name, String type,
            List<ProcessorServiceWithConfig> serviceList,
            String creator, ProcessorConfig config, Object spec)
            throws ProcessorException, ConfigurationException, InitException, ValidationException {
        if (config == null) {
            throw new NullPointerException("config cannot be null");
        }
        Processor proc = new Processor(instance, name, type, creator);
        YamcsServerInstance ysi = YamcsServer.getServer().getInstance(instance);
        if (ysi != null) {// Unit Tests create processors outside of any instance
            ysi.addProcessor(proc);
        }
        proc.init(serviceList, config, spec);
        return proc;
    }

    /**
     * creates a processor with the services already instantiated. used from unit tests
     * 
     * @throws ValidationException
     */
    public static Processor create(String yamcsInstance, String name, ProcessorService... services)
            throws ProcessorException, ConfigurationException, InitException, ValidationException {
        List<ProcessorServiceWithConfig> serviceList = new ArrayList<>();
        for (ProcessorService service : services) {
            serviceList.add(
                    new ProcessorServiceWithConfig(service, service.getClass().getName(), service.getClass().getName(),
                            YConfiguration.emptyConfig()));
        }
        return create(yamcsInstance, name, "test", serviceList, "test", new ProcessorConfig(), null);
    }

    /**
     * creates a processor with the services already instantiated. used from unit tests
     * 
     * @throws ValidationException
     */
    public static Processor create(String yamcsInstance, String name, ProcessorServiceWithConfig... serviceList)
            throws ProcessorException, ConfigurationException, InitException, ValidationException {
        return create(yamcsInstance, name, "test", Arrays.asList(serviceList), "test", new ProcessorConfig(), null);
    }

    static List<ProcessorServiceWithConfig> createServices(String instance, List<YConfiguration> servicesConfig,
            Log targetLog)
            throws ValidationException, IOException {
        ManagementService managementService = ManagementService.getInstance();
        Set<String> names = new HashSet<>();
        List<ProcessorServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for (YConfiguration servconf : servicesConfig) {
            String servclass;
            YConfiguration config;
            String name = null;
            servclass = servconf.getString("class");
            if (servconf.containsKey("config")) {
                config = servconf.getConfig("config");
            } else if (servconf.containsKey("args")) {
                config = servconf.getConfig("args");
            } else {
                config = YConfiguration.emptyConfig();
            }

            name = servconf.getString("name", servclass.substring(servclass.lastIndexOf('.') + 1));
            String candidateName = name;
            int count = 1;
            while (names.contains(candidateName)) {
                candidateName = name + "-" + count;
                count++;
            }
            name = candidateName;

            targetLog.info("Loading processor service {}", name);
            ProcessorServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, name, config, targetLog);
                serviceList.add(swc);
            } catch (NoClassDefFoundError e) {
                targetLog.error("Cannot create service {}, with arguments {}: class {} not found", name, config,
                        servclass);
                throw e;
            } catch (ValidationException e) {
                throw e;
            } catch (Exception e) {
                targetLog.error("Cannot create service {}, with arguments {}: {}", name, config, e.getMessage());
                throw e;
            }
            if (managementService != null) {
                managementService.registerService(instance, name, swc.service);
            }
            names.add(name);
        }

        return serviceList;
    }

    static ProcessorServiceWithConfig createService(String instance, String serviceClass, String serviceName,
            YConfiguration config, Log targetLog)
            throws ConfigurationException, ValidationException, IOException {
        ProcessorService service = null;

        service = YObjectLoader.loadObject(serviceClass);
        Spec spec = service.getSpec();
        if (spec != null) {
            if (targetLog.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = config.getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                targetLog.debug("Raw args for {}: {}", serviceName, safeArgs);
            }

            config = spec.validate((YConfiguration) config);

            if (targetLog.isDebugEnabled()) {
                Map<String, Object> unsafeArgs = config.getRoot();
                Map<String, Object> safeArgs = spec.maskSecrets(unsafeArgs);
                targetLog.debug("Initializing {} with resolved args: {}", serviceName, safeArgs);
            }
        }
        return new ProcessorServiceWithConfig(service, serviceClass, serviceName, config);
    }
}
