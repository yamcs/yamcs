package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;

/**
 * Stores the value of the streamConfiguration parameter from yamcs.instance.yaml Used to create the streams at Yamcs
 * startup and by various other services (recording, processor, ...)
 * 
 */
public class StreamConfig {
    public enum StandardStreamType {
        CMD_HIST, TM, PARAM, TC, EVENT, PARAMETER_ALARM, EVENT_ALARM, SQL_FILE, INVALID_TM;

        public static StandardStreamType fromString(String s) {
            for (StandardStreamType v : values()) {
                if (v.name().replace("_", "").equals(s.toUpperCase())) {
                    return v;
                }
            }
            throw new IllegalArgumentException("No stream type by this name");
        }
    }

    List<StreamConfigEntry> entries = new ArrayList<>();
    static Map<String, StreamConfig> instances = new HashMap<>();
    Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public static synchronized StreamConfig getInstance(String yamcsInstance) throws ConfigurationException {
        return instances.computeIfAbsent(yamcsInstance, StreamConfig::new);
    }

    public static synchronized void removeInstance(String instanceName) {
        instances.remove(instanceName);
    }

    @SuppressWarnings("unchecked")
    private StreamConfig(String yamcsInstance) {
        Mdb mdb = MdbFactory.getInstance(yamcsInstance);
        YamcsServerInstance instance = YamcsServer.getServer().getInstance(yamcsInstance);
        YConfiguration instanceConfig = instance.getConfig();
        if (!instanceConfig.containsKey("streamConfig")) {
            log.warn("No streamConfig defined for instance {}", yamcsInstance);
            return;
        }
        YConfiguration streamConfigAll = instanceConfig.getConfig("streamConfig");

        for (String streamType : streamConfigAll.getRoot().keySet()) {
            if ("alarm".equals(streamType)) {
                log.warn("Deprecation in streamConfig, please change 'alarm' into 'parameterAlarm'"
                        + " (since version 4.10 there is also eventAlarm)");
                streamType = "parameterAlarm";
            }
            StandardStreamType type = null;
            try {
                type = StandardStreamType.fromString(streamType);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Unknown stream type '" + streamType + "'");
            }
            Object o = streamConfigAll.get(streamType);
            if (o instanceof String) {
                addEntry(type, (String) o);
            } else if (o instanceof List) {
                List<Object> streamList = (List<Object>) o;

                for (int i = 0; i < streamList.size(); i++) {
                    Object o1 = streamList.get(i);
                    if (o1 instanceof String) {
                        addEntry(type, (String) o1);
                    } else if (o1 instanceof Map) {
                        YConfiguration streamConf = streamConfigAll.getConfigListIdx(streamType, i);
                        addEntry(mdb, type, streamConf);
                    }
                }
            } else {
                throw new ConfigurationException("invalid entry '" + o + "' in streamConfiguration");
            }
        }
    }

    private void addEntry(StandardStreamType type, String streamName) {
        StreamConfigEntry entry;
        if (type == StandardStreamType.TM) {
            entry = new TmStreamConfigEntry(streamName);
        } else if (type == StandardStreamType.TC) {
            entry = new TcStreamConfigEntry(streamName, null, null);
        } else {
            entry = new StreamConfigEntry(type, streamName, null);
        }

        entries.add(entry);
    }

    private void addEntry(Mdb mdb, StandardStreamType type, YConfiguration streamConf) {
        StreamConfigEntry entry;
        String streamName = streamConf.getString("name");
        boolean async = streamConf.getBoolean("async", false);

        SequenceContainer rootContainer = null;

        String processor = streamConf.getString("processor", null);

        if (type == StandardStreamType.TM) {
            if (streamConf.containsKey("rootContainer")) {
                String containerName = streamConf.getString("rootContainer");
                rootContainer = mdb.getSequenceContainer(containerName);
                if (rootContainer == null) {
                    throw new ConfigurationException("Unknown sequence container: " + containerName);
                }
            }
            entry = new TmStreamConfigEntry(streamName, processor, rootContainer, async);
        } else if (type == StandardStreamType.TC) {
            if (streamConf.containsKey("tcPatterns")) {
                List<String> patterns = streamConf.getList("tcPatterns");
                List<Pattern> patterns1 = patterns.stream().map(Pattern::compile).collect(Collectors.toList());
                entry = new TcStreamConfigEntry(streamName, processor, patterns1);
            } else {
                entry = new TcStreamConfigEntry(streamName, processor);
            }
        } else {
            entry = new StreamConfigEntry(type, streamName, processor);
        }
        entries.add(entry);
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
        return entries.stream().filter(sce -> sce.type == type).map(StreamConfigEntry::getName)
                .collect(Collectors.toList());
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

        /**
         * processor name see. If configured, it will be checked by {@link StreamTmPacketProvider} to select the stream
         * to connect to the given processor
         */
        String processor;

        public StreamConfigEntry(StandardStreamType type, String name, String processor) {
            super();
            this.type = type;
            this.name = name;
            this.processor = processor;
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

        /**
         * Return the name of the processor where this stream should be connected to or null if no such processor exists
         * 
         * @return
         */
        public String getProcessor() {
            return processor;
        }
    }

    public TmStreamConfigEntry getTmEntry(String streamName) {
        StreamConfigEntry sce = getEntry(StandardStreamType.TM, streamName);
        return (TmStreamConfigEntry) sce;
    }

    public List<TmStreamConfigEntry> getTmEntries() {
        return entries.stream().filter(TmStreamConfigEntry.class::isInstance).map(TmStreamConfigEntry.class::cast)
                .collect(Collectors.toList());
    }

    public TcStreamConfigEntry getTcEntry(String streamName) {
        StreamConfigEntry sce = getEntry(StandardStreamType.TC, streamName);
        return (TcStreamConfigEntry) sce;
    }

    public List<TcStreamConfigEntry> getTcEntries() {
        return entries.stream().filter(TcStreamConfigEntry.class::isInstance).map(TcStreamConfigEntry.class::cast)
                .collect(Collectors.toList());
    }

    public class TmStreamConfigEntry extends StreamConfigEntry {
        // used by the XtceTmRecoder to block or not the thread that provides the TM packet wh
        boolean async;
        // root container used for telemetry processing
        SequenceContainer rootContainer;

        public TmStreamConfigEntry(String name, String processor,
                SequenceContainer rootContainer, boolean async) {
            super(StandardStreamType.TM, name, processor);
            this.rootContainer = rootContainer;
            this.async = async;
        }

        public TmStreamConfigEntry(String streamName) {
            this(streamName, null, null, false);
        }

        public SequenceContainer getRootContainer() {
            return rootContainer;
        }

        public boolean isAsync() {

            return async;
        }
    }

    public class TcStreamConfigEntry extends StreamConfigEntry {
        // if not null, the commands will be placed in this stream only if their fully qualified name matches a
        // pattern from the list
        List<Pattern> tcPatterns;

        public TcStreamConfigEntry(String name, String processor, List<Pattern> tcPatterns) {
            super(StandardStreamType.TC, name, processor);
            this.tcPatterns = tcPatterns;
        }

        public TcStreamConfigEntry(String streamName) {
            this(streamName, null, null);
        }

        public TcStreamConfigEntry(String streamName, String processor) {
            this(streamName, processor, null);
        }

        public List<Pattern> getTcPatterns() {
            return tcPatterns;
        }
    }
}
