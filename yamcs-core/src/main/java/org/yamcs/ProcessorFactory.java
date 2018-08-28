package org.yamcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to create processors as defined in yprocessor.yaml
 *
 * @author mache
 *
 */
public class ProcessorFactory {
    private static Logger log = LoggerFactory.getLogger(Processor.class.getName());

    /**
     * Returns the processor types as defined in <tt>processor.yaml</tt>
     */
    public static List<String> getProcessorTypes() {
        if (!YConfiguration.isDefined("processor")) {
            return Collections.emptyList();
        }
        YConfiguration conf = YConfiguration.getConfiguration("processor");
        return new ArrayList<>(conf.getKeys());
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
     */
    public static Processor create(String yamcsInstance, String name, String type, String creator, Object spec)
            throws ProcessorException, ConfigurationException {
        Map<String, Object> processorConfig = null;
        YConfiguration conf = YConfiguration.getConfiguration("processor");

        List<ServiceWithConfig> serviceList = new ArrayList<>();
        try {
            if (!conf.containsKey(type)) {
                throw new ConfigurationException("No processor type '" + type + "' found in " + conf.getFilename());
            }

            if (conf.containsKey(type, "services")) {
                serviceList = YamcsServer.createServices(yamcsInstance, conf.getList(type, "services"));
            } else {
                log.warn("Deprecated configuration in {}. Please use services", conf.getFilename());
                if (conf.containsKey(type, "telemetryProvider")) {
                    Map<String, Object> m = (Map<String, Object>) conf.getMap(type, "telemetryProvider");
                    String tmClass = YConfiguration.getString(m, "class");
                    Object tmArgs = m.get("args");
                    serviceList.add(YamcsServer.createService(yamcsInstance, tmClass, tmClass, tmArgs));
                }

                if (conf.containsKey(type, "parameterProviders")) {
                    serviceList.addAll(
                            YamcsServer.createServices(yamcsInstance, conf.getList(type, "parameterProviders")));
                }
                if (conf.containsKey(type, "commandReleaser")) {
                    Map<String, Object> m = (Map<String, Object>) conf.getMap(type, "commandReleaser");
                    String commandClass = YConfiguration.getString(m, "class");
                    Object commandArgs = m.get("args");
                    serviceList.add(YamcsServer.createService(yamcsInstance, commandClass, commandClass, commandArgs));
                }
            }
            if (conf.containsKey(type, "config")) {
                processorConfig = (Map<String, Object>) conf.getMap(type, "config");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Cannot load service", e);
        }
        return create(yamcsInstance, name, type, serviceList, creator, processorConfig, spec);
    }

    /**
     * Create a Processor by specifying the service.
     * 
     * The type is not used in this case, except for showing it in the yamcs monitor.
     * 
     * @param instance
     * @param name
     * @param type
     * @param creator
     * @param config
     * @return
     * @throws ProcessorException
     * @throws ConfigurationException
     **/
    public static Processor create(String instance, String name, String type, List<ServiceWithConfig> serviceList,
            String creator, Map<String, Object> config, Object spec) throws ProcessorException, ConfigurationException {
        Processor proc = new Processor(instance, name, type, creator);

        proc.init(serviceList, config, spec);
        return proc;
    }

    /**
     * creates a processor with the services already instantiated. used from unit tests
     */
    public static Processor create(String yamcsInstance, String name, ProcessorService... services)
            throws ProcessorException, ConfigurationException {
        List<ServiceWithConfig> serviceList = new ArrayList<>();
        for (ProcessorService service : services) {
            serviceList.add(
                    new ServiceWithConfig(service, service.getClass().getName(), service.getClass().getName(), null));
        }
        return create(yamcsInstance, name, "test", serviceList, "test", null, null);
    }
}
