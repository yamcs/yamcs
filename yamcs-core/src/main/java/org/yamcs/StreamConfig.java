package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Stores the value of the streamConfiguration parameter from yamcs.instance.yaml Used to create the streams at Yamcs
 * startup and by various other services (recording, processor, ...)
 * 
 * 
 * 
 * @author nm
 *
 */
public class StreamConfig {
    public enum StandardStreamType {
        cmdHist, tm, param, tc, event, parameterAlarm, eventAlarm, sqlFile, invalidTm;
    }

    List<StreamConfigEntry> entries = new ArrayList<>();
    static Map<String, StreamConfig> instances = new HashMap<>();
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public static synchronized StreamConfig getInstance(String yamcsInstance) throws ConfigurationException {
        return instances.computeIfAbsent(yamcsInstance, k -> new StreamConfig(k));
    }

    @SuppressWarnings("unchecked")
    private StreamConfig(String yamcsInstance) {
        XtceDb xtceDb = XtceDbFactory.getInstance(yamcsInstance);
        YamcsServerInstance instance = YamcsServer.getServer().getInstance(yamcsInstance);
        YConfiguration instanceConfig = instance.getConfig();
        if (!instanceConfig.containsKey("streamConfig")) {
            log.warn("No streamConfig defined for instance {}", yamcsInstance);
            return;
        }
        YConfiguration streamConfig = instanceConfig.getConfig("streamConfig");

        for (Entry<String, Object> m : streamConfig.getRoot().entrySet()) {
            String streamType = m.getKey();
            if ("alarm".equals(streamType)) {
                log.warn("Deprecation in streamConfig, please change 'alarm' into 'parameterAlarm'"
                        + " (since version 4.10 there is also eventAlarm)");
                streamType = "parameterAlarm";
            }
            StandardStreamType type = null;
            try {
                type = StandardStreamType.valueOf(streamType);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Unknown stream type '" + streamType + "'");
            }
            Object o = m.getValue();
            if (o instanceof String) {
                String streamName = (String) o;
                entries.add(new StreamConfigEntry(type, streamName, null, null, false));
            } else if (o instanceof List) {
                List<Object> streamList = (List<Object>) m.getValue();
                for (Object o1 : streamList) {
                    String streamName = null;
                    boolean async = false;
                    SequenceContainer rootContainer = null;
                    String processor = null;
                    if (o1 instanceof String) {
                        streamName = (String) o1;
                    } else if (o1 instanceof Map) {
                        Map<String, Object> streamConf = (Map<String, Object>) o1;
                        if (!streamConf.containsKey("name")) {
                            throw new ConfigurationException("No name specified for stream config: " + o);
                        }
                        streamName = (String) streamConf.get("name");
                        if (streamConf.containsKey("async")) {
                            async = (Boolean) streamConf.get("async");
                        }
                        if (streamConf.containsKey("rootContainer")) {
                            String containerName = (String) streamConf.get("rootContainer");
                            rootContainer = xtceDb.getSequenceContainer(containerName);
                            if (rootContainer == null) {
                                throw new ConfigurationException("Unknown sequence container: " + containerName);
                            }
                        }
                        if (streamConf.containsKey("rootContainer")) {
                            processor = (String) streamConf.get("processor");
                        }
                    }
                    entries.add(new StreamConfigEntry(type, streamName, processor, rootContainer, async));
                }
            }

        }
    }

    /**
     * get all stream configurations
     * 
     * @return a list of stream configuration
     */
    public List<StreamConfigEntry> getEntries() {
        return entries;
    }

    public List<String> getStreamNames(StandardStreamType type) {
        return entries.stream().filter(sce -> sce.type == type).map(sce -> sce.getName()).collect(Collectors.toList());
    }

    /**
     * get stream configuration of a specific type. Returns an empty list if no stream of that type has been defined
     * 
     * @return a list of stream configuration of the given type
     */
    public List<StreamConfigEntry> getEntries(StandardStreamType type) {
        List<StreamConfigEntry> r = new ArrayList<>();
        for (StreamConfigEntry sce : entries) {
            if (sce.type == type) {
                r.add(sce);
            }
        }
        return r;
    }

    /**
     * returns the stream config with the given type and name or null if it has not been defined
     * 
     * @param type
     * @param streamName
     * @return
     */
    public StreamConfigEntry getEntry(StandardStreamType type, String streamName) {
        for (StreamConfigEntry sce : entries) {
            if (sce.type == type && sce.name.equals(streamName)) {
                return sce;
            }
        }
        return null;
    }

    public static class StreamConfigEntry {
        StandardStreamType type;
        // name of the stream or of the file to be loaded if the type is sqlFile
        String name;

        // root container used for telemetry processing
        SequenceContainer rootContainer;

        boolean async;

        /**
         * processor name see. If configured, it will be checked by {@link StreamTmPacketProvider} to select the stream
         * to connect to the given processor
         */
        String processor;

        public StreamConfigEntry(StandardStreamType type, String name, String processor,
                SequenceContainer rootContainer, boolean async) {
            super();
            this.type = type;
            this.name = name;
            this.processor = processor;
            this.rootContainer = rootContainer;
            this.async = async;
        }

        public StandardStreamType getType() {
            return type;
        }

        /**
         * 
         * @return stream name
         */
        public String getName() {
            return name;
        }

        public SequenceContainer getRootContainer() {
            return rootContainer;
        }

        public boolean isAsync() {
            return async;
        }

        /**
         * Return the name of the processor where this stream should be connected to or null if no such processor exists
         * 
         * @return
         */
        public Object getProcessor() {
            return processor;
        }
    }
}
