package org.yamcs;

import org.yamcs.Spec.OptionType;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ContainerProcessingOptions;
import org.yamcs.mdb.MetaCommandProcessor;
import org.yamcs.parameter.ParameterCacheConfig;

/**
 * Configuration options for a processor
 */
public class ProcessorConfig {

    private static final String CONFIG_KEY_PARAMETER_CACHE = "parameterCache";
    private static final String CONFIG_KEY_ALARM = "alarm";
    private static final String CONFIG_KEY_GENERATE_EVENTS = "generateEvents";
    private static final String CONFIG_KEY_SUBSCRIBE_ALL = "subscribeAll";
    private static final String CONFIG_KEY_RECORD_INITIAL_VALUES = "recordInitialValues";
    private static final String CONFIG_KEY_RECORD_LOCAL_VALUES = "recordLocalValues";
    private static final String CONFIG_KEY_TM_PROCESSOR = "tmProcessor";
    private static final String CONFIG_KEY_MAX_TC_SIZE = "maxTcSize";
    private static final String CONFIG_KEY_CONTAINERLESS_CMDS = "allowContainerlessCommands";
    private static final String CONFIG_KEY_CHECK_COMMAND_CLEARANCE = "checkCommandClearance";
    private static final String CONFIG_KEY_CHECK_PARAMETER_VALIDITY_RANGES = "checkParameterValidityRanges";
    private static final String CONFIG_KEY_SUBSCRIBE_CONTAINER_ARCHPART = "subscribeContainerArchivePartitions";
    private static final String CONFIG_KEY_PERSIST_PARAMETERS = "persistParameters";

    boolean checkParameterAlarms = true;
    boolean parameterAlarmServerEnabled = false;
    boolean eventAlarmServerEnabled = false;
    int maxTcSize = 4096;
    boolean recordInitialValues = false;
    boolean recordLocalValues = false;
    int eventAlarmMinViolations = 1;
    boolean subscribeAll = false;
    boolean generateEvents = false;
    boolean checkCommandClearance = false;
    boolean checkParameterValidityRanges = true;
    boolean persistParameters = false;

    // if set to true, subscribe by default to all containers that have the useAsArchivePartiton flag set
    // used to have nice statistics showing the number of each packet received for the realtime and replay processors
    boolean subscribeContainerArchivePartitions = true;

    /**
     * If this is set to true, the {@link MetaCommandProcessor} will release commands without binary encoding if a
     * MetaCommand has no container associated.
     * <p>
     * The link is then responsible to encode the command somehow starting from the command name and argument
     * assignments.
     *
     */
    boolean allowContainerlessCommands = false;

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
                } else if (CONFIG_KEY_MAX_TC_SIZE.equals(key)) {
                    maxTcSize = config.getInt(key);
                } else if (CONFIG_KEY_CONTAINERLESS_CMDS.equals(key)) {
                    allowContainerlessCommands = config.getBoolean(key);
                } else if (CONFIG_KEY_CHECK_COMMAND_CLEARANCE.equals(key)) {
                    checkCommandClearance = config.getBoolean(key);
                } else if (CONFIG_KEY_CHECK_PARAMETER_VALIDITY_RANGES.equals(key)) {
                    checkParameterValidityRanges = config.getBoolean(key);
                } else if (CONFIG_KEY_SUBSCRIBE_CONTAINER_ARCHPART.equals(key)) {
                    subscribeContainerArchivePartitions = config.getBoolean(key);
                } else if (CONFIG_KEY_PERSIST_PARAMETERS.equals(key)) {
                    persistParameters = config.getBoolean(key);
                } else {
                    log.warn("Ignoring unknown config key '{}'", key);
                }
            }
        }
        containerProcOptions = new ContainerProcessingOptions(contProc);
        parameterCacheConfig = new ParameterCacheConfig(pcacheConfig, log);

    }

    public static Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption(CONFIG_KEY_ALARM, OptionType.ANY);
        spec.addOption(CONFIG_KEY_SUBSCRIBE_ALL, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_PARAMETER_CACHE, OptionType.ANY);
        spec.addOption(CONFIG_KEY_TM_PROCESSOR, OptionType.MAP).withSpec(ContainerProcessingOptions.getSpec());
        spec.addOption(CONFIG_KEY_RECORD_INITIAL_VALUES, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_RECORD_LOCAL_VALUES, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_GENERATE_EVENTS, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_MAX_TC_SIZE, OptionType.INTEGER).withDefault(4096);
        spec.addOption(CONFIG_KEY_CONTAINERLESS_CMDS, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_CHECK_COMMAND_CLEARANCE, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(CONFIG_KEY_CHECK_PARAMETER_VALIDITY_RANGES, OptionType.ANY);
        spec.addOption(CONFIG_KEY_SUBSCRIBE_CONTAINER_ARCHPART, OptionType.BOOLEAN).withDefault(true);
        spec.addOption(CONFIG_KEY_PERSIST_PARAMETERS, OptionType.BOOLEAN).withDefault(false);
        return spec;
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
            throw new ConfigurationException(
                    "Deprecation: in processor.yaml, please replace config -> alarm -> check with config -> alarm -> parameterCheck");
        }
        checkParameterAlarms = alarmConfig.getBoolean("parameterCheck", checkParameterAlarms);
        if (alarmConfig.containsKey("server")) {
            throw new ConfigurationException(
                    "Deprecation: in processor.yaml, please replace config -> alarm -> server with config -> alarm -> parameterServer");
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

    public boolean allowContainerlessCommands() {
        return allowContainerlessCommands;
    }

    public boolean checkCommandClearance() {
        return checkCommandClearance;
    }

    public boolean checkParameterValidityRanges() {
        return checkParameterValidityRanges;
    }

    public boolean subscribeContainerArchivePartitions() {
        return subscribeContainerArchivePartitions;
    }

    public void setSubscribeContainerArchivePartitions(boolean b) {
        this.subscribeContainerArchivePartitions = b;
    }

    public boolean persistParameters() {
        return persistParameters;
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
