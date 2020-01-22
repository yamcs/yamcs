package org.yamcs;

import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterCacheConfig;
import org.yamcs.xtceproc.ContainerProcessingOptions;

/**
 * Configuration options for a processor
 * 
 * @author nm
 *
 */
public class ProcessorConfig {
  

    private static final String CONFIG_KEY_PARAMETER_CACHE = "parameterCache";
    private static final String CONFIG_KEY_ALARM = "alarm";
    private static final String CONFIG_KEY_GENERATE_EVENTS = "generateEvents";
    private static final String CONFIG_KEY_SUBSCRIBE_ALL = "subscribeAll";
    private static final String CONFIG_KEY_RECORD_INITIAL_VALUES = "recordInitialValues";
    private static final String CONFIG_KEY_RECORD_LOCAL_VALUES = "recordLocalValues";
    private static final String CONFIG_KEY_TM_PROCESSOR = "tmProcessor";

    boolean checkParameterAlarms = true;
    boolean parameterAlarmServerEnabled = false;
    boolean eventAlarmServerEnabled = false;
    int maxTcSize = 4096;
    boolean recordInitialValues = false;
    boolean recordLocalValues = false;
    int eventAlarmMinViolations = 1;
    boolean subscribeAll = false;
    boolean generateEvents = false;

    final ContainerProcessingOptions containerProcOptions;
    final ParameterCacheConfig parameterCacheConfig;
    static Log log = new Log(ProcessorConfig.class);

    public ProcessorConfig(YConfiguration config) {
        
        YConfiguration contProc = YConfiguration.emptyConfig();
        YConfiguration pcacheConfig = YConfiguration.emptyConfig();

        if (config != null) {
            for (String key : config.getRoot().keySet()) {
                if (CONFIG_KEY_ALARM.equals(key)) {
                    parseAlarmConfig(config.getConfig(key));
                } else if (CONFIG_KEY_SUBSCRIBE_ALL.equals(key)) {
                    subscribeAll = config.getBoolean(CONFIG_KEY_SUBSCRIBE_ALL);
                } else if (CONFIG_KEY_PARAMETER_CACHE.equals(key)) {
                    pcacheConfig = config.getConfig(key);
                } else if (CONFIG_KEY_TM_PROCESSOR.equals(key)) {
                    contProc = config.getConfig(key);
                } else if (CONFIG_KEY_RECORD_INITIAL_VALUES.equals(key)) {
                    recordInitialValues = config.getBoolean(key);
                } else if (CONFIG_KEY_RECORD_LOCAL_VALUES.equals(key)) {
                    recordLocalValues = config.getBoolean(key);
                } else if (CONFIG_KEY_GENERATE_EVENTS.equals(key)) {
                    generateEvents = config.getBoolean(key);
                } else {
                    log.warn("Ignoring unknown config key '{}'", key);
                }
            }
        }
        containerProcOptions = new ContainerProcessingOptions(contProc);
        parameterCacheConfig = new ParameterCacheConfig(pcacheConfig, log);
        
    }

    /**
     * Default configuration
     */
    public ProcessorConfig() {
        containerProcOptions = new ContainerProcessingOptions();
        parameterCacheConfig = new ParameterCacheConfig();
    }

    private void parseAlarmConfig(YConfiguration alarmConfig) {
        
        if (alarmConfig.containsKey("check")) {
            log.warn(
                    "Deprecation: in processor.yaml, please replace config -> alarm -> check with config -> alarm -> parameterCheck");
            checkParameterAlarms = alarmConfig.getBoolean("check");
        }
        checkParameterAlarms = alarmConfig.getBoolean("parameterCheck", checkParameterAlarms);
        if (alarmConfig.containsKey("server")) {
            log.warn(
                    "Deprecation: in processor.yaml, please replace config -> alarm -> server with config -> alarm -> parameterServer");
            parameterAlarmServerEnabled = "enabled".equalsIgnoreCase(alarmConfig.getString("server", null));
        }
        if (alarmConfig.containsKey("parameterServer")) {
            parameterAlarmServerEnabled = "enabled".equalsIgnoreCase(alarmConfig.getString("parameterServer"));
        }
        if (parameterAlarmServerEnabled) {
            checkParameterAlarms = true;
        }

        eventAlarmServerEnabled = "enabled".equalsIgnoreCase(alarmConfig.getString("eventServer", null));
        eventAlarmMinViolations = alarmConfig.getInt("eventAlarmMinViolations", eventAlarmMinViolations);
    }


    public int getMaxCommandSize() {
        return maxTcSize;
    }

    public int getEventAlarmMinViolations() {
        return eventAlarmMinViolations;
    }

    public boolean generateEvents() {
        return generateEvents;
    }

    public ContainerProcessingOptions getContainerProcessingOptions() {
        return containerProcOptions;
    }
    
    @Override
    public String toString() {
        return "ProcessorConfig [checkParameterAlarms=" + checkParameterAlarms + ", parameterAlarmServerEnabled="
                + parameterAlarmServerEnabled + ", eventAlarmServerEnabled=" + eventAlarmServerEnabled + ", maxTcSize="
                + maxTcSize + ", recordInitialValues=" + recordInitialValues + ", recordLocalValues="
                + recordLocalValues + ", eventAlarmMinViolations=" + eventAlarmMinViolations + ", subscribeAll="
                + subscribeAll + ", generateEvents=" + generateEvents + ", containerProcOptions=" + containerProcOptions
                + ", parameterCacheConfig=" + parameterCacheConfig + "]";
    }
}
