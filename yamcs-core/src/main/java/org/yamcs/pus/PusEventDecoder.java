package org.yamcs.pus;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.ProcessorConfig;
import org.yamcs.Spec;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.pus.MessageTemplate.ParameterValueResolver;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;


import org.yamcs.Spec.OptionType;
import org.yamcs.StreamConfig.TmStreamConfigEntry;

/**
 * Generates Yamcs events from PUS event packets (PUS service 5).
 * <p>
 * This class reads a JSON configuration file that defines the text templates for Yamcs events, each corresponding to a
 * specific PUS eventId. The configuration file should contain entries in the following format:
 * 
 * <pre>
 * eventId: <text>
 * template: <template string>
 * </pre>
 * 
 * The template string can include free text and placeholders in the format `{parameter_name; format}`. The `format` is
 * optional. The `parameter_name` can be absolute or relative and can end in `.raw`, in which case the raw value is used
 * instead of the engineering value.
 */

public class PusEventDecoder extends AbstractYamcsService {
    TimeService timeService;
    Mdb mdb;
    List<StreamEventDecoder> decoders;
    Parameter eventIdParameter;
    EventFormatter eventFormatter;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        mdb = MdbFactory.getInstance(yamcsInstance);
        String idfqn = config.getString("eventIdParameter");
        eventIdParameter = mdb.getParameter(idfqn);
        if (eventIdParameter == null) {
            throw new ConfigurationException("Parameter " + idfqn + " not found");
        }
        if (!(eventIdParameter.getParameterType() instanceof EnumeratedParameterType)) {
            throw new ConfigurationException("Wrong type for " + idfqn + ". Expected EnumeratedParameterType but got "
                    + eventIdParameter.getParameterType());
        }
        StreamConfig streamConfig = StreamConfig.getInstance(yamcsInstance);
        decoders = new ArrayList<>();
        timeService = YamcsServer.getTimeService(yamcsInstance);

        if (config.containsKey("realtimeStreams")) {
            var realtimeEventStream = findStream(config.getString("realtimeEventStream"));

            List<String> streamNames = config.getList("realtimeStreams");
            for (String sn : streamNames) {
                TmStreamConfigEntry sce = streamConfig.getTmEntry(sn);
                if (sce == null) {
                    throw new ConfigurationException("No stream config found for '" + sn + "'");
                }
                createDecoder(sce, realtimeEventStream);
            }
        } else {
            Stream realtimeEventStream = null;
            List<TmStreamConfigEntry> sceList = streamConfig.getTmEntries();
            for (TmStreamConfigEntry sce : sceList) {
                if ("realtime".equals(sce.getProcessor())) {
                    if (realtimeEventStream == null) {
                        realtimeEventStream = findStream(config.getString("realtimeEventStream"));
                    }
                    createDecoder(sce, realtimeEventStream);
                }
            }
        }

        if (config.containsKey("dumpStreams")) {
            var dumpEventStream = findStream(config.getString("dumpEventStream"));

            List<String> streamNames = config.getList("dumpStreams");
            for (String sn : streamNames) {
                TmStreamConfigEntry sce = streamConfig.getTmEntry(sn);
                if (sce == null) {
                    throw new ConfigurationException("No stream config found for '" + sn + "'");
                }
                createDecoder(sce, dumpEventStream);
            }
        }

        eventFormatter = new EventFormatter(config.getString("eventTemplateFile"));
    }

    private void createDecoder(TmStreamConfigEntry sce, Stream eventStream) {
        SequenceContainer rootsc = sce.getRootContainer();
        if (rootsc == null) {
            rootsc = mdb.getRootSequenceContainer();
        }
        if (rootsc == null) {
            throw new ConfigurationException(
                    "MDB does not have a root sequence container and no container was specified for decoding packets from "
                            + sce.getName() + " stream");
        }

        Stream inputStream = findStream(sce.getName());
        var decoder = new StreamEventDecoder(inputStream, eventStream, rootsc);
        decoders.add(decoder);
    }

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("eventIdParameter", OptionType.STRING)
                .withRequired(true)
                .withDescription("Enumerated or string parameter that gives "
                        + "the name of the event and will use to determine the template to use");
        spec.addOption("eventTemplateFile", OptionType.STRING).withRequired(true)
                .withDescription("A file containing the templates for the events.");

        spec.addOption("rootSequenceContainer", OptionType.STRING).withRequired(false)
                .withDescription("The root container that will be used to parse the event packets."
                        + "If not specified, the root container configured for each stream will be used");
        spec.addOption("realtimeStreams", OptionType.LIST).withElementType(OptionType.STRING).withRequired(false)
                .withDescription("The list of realtime streams. "
                        + "The events resulting from data on these streams will be sent ot the events_realtime stream "
                        + "(and thus available when subscribing to the realtime processor)");
        spec.addOption("dumpStreams", OptionType.LIST).withElementType(OptionType.STRING).withRequired(false)
                .withDescription("The list of realtime streams. "
                        + "The events resulting from data on these streams will be sent ot the events_dump stream");
        spec.addOption("realtimeEventStream", OptionType.STRING).withDefault(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        spec.addOption("dumpEventStream", OptionType.STRING).withDefault(EventRecorder.DUMP_EVENT_STREAM_NAME);

        return spec;
    }

    @Override
    protected void doStart() {
        for (var d : decoders) {
            d.inputStream.addSubscriber(d);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (var d : decoders) {
            d.inputStream.removeSubscriber(d);
        }
        notifyStopped();
    }

    EventSeverity getSeverity(int pusSubtype) {
        return switch (pusSubtype) {
        case 1 -> EventSeverity.INFO;
        case 2 -> EventSeverity.WATCH;
        case 3 -> EventSeverity.DISTRESS;
        case 4 -> EventSeverity.CRITICAL;
        default -> EventSeverity.WATCH;
        };
    }

    class StreamEventDecoder implements StreamSubscriber {
        final Stream inputStream;
        final SequenceContainer rootsc;
        Stream eventStream;
        XtceTmExtractor tmExtractor;

        public StreamEventDecoder(Stream inputStream, Stream eventStream, SequenceContainer rootsc) {
            this.inputStream = inputStream;
            this.eventStream = eventStream;
            this.rootsc = rootsc;
            var pdata = new ProcessorData(yamcsInstance, "XTCEPROC", mdb, new ProcessorConfig(),
                    Collections.emptyMap());
            tmExtractor = new XtceTmExtractor(mdb, pdata);
            tmExtractor.provideAll();
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            byte[] packet = tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
            if (packet.length < PusPacket.TM_MIN_SIZE) {
                return;
            }
            int type = PusPacket.getType(packet);
            if (type != PusPacket.SERVICE_TYPE_EVENT) {
                return;
            }

            int apid = PusPacket.getApid(packet);
            int subtype = PusPacket.getSubtype(packet);

            long gentime = tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
            int seqCount = tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);

            ContainerProcessingResult cpr = tmExtractor.processPacket(packet, gentime, timeService.getMissionTime(),
                    seqCount, rootsc);
            var params = cpr.getTmParams();

            var eventIdValue = params.getFirstInserted(eventIdParameter);
            if (eventIdValue == null) {
                log.warn("Did not find {} in packet extraction", eventIdParameter.getQualifiedName());
                return;
            }
            String eventId = eventIdValue.getEngValue().getStringValue();

            String msg = eventFormatter.format(apid, eventId, params);
            if (msg == null) {
                log.warn("No template found for message apid={}, eventId={}", apid, eventId);
            } else {
                Event ev = Event.newBuilder()
                        .setType(eventId)
                        .setSeverity(getSeverity(subtype))
                        .setGenerationTime(gentime)
                        .setSeqNumber(seqCount)
                        .setMessage(msg).build();
                TupleDefinition tdef = eventStream.getDefinition();
                Tuple t = new Tuple(tdef, new Object[] { ev.getGenerationTime(),
                        ev.getSource(), ev.getSeqNumber(), ev });
                eventStream.emitTuple(t);
            }

        }
    }

    class EventFormatter {
        // Map to hold the templates with eventId and apid as keys
        private Map<String, MessageTemplate> templates = new HashMap<>();

        public EventFormatter(String fileName) throws ConfigurationException {
            LoaderOptions loaderOptions = new LoaderOptions();
            int maxAliases = Integer.parseInt(System.getProperty("org.yamcs.yaml.maxAliases", "200"));
            loaderOptions.setMaxAliasesForCollections(maxAliases);

            var yaml = new Yaml(loaderOptions);
            Object o;
            try {
                o = yaml.load(new FileReader(fileName));
            } catch (FileNotFoundException e) {
                throw new ConfigurationException("Cannot find event description file " + fileName);
            }
            if (!(o instanceof List<?>)) {
                throw new ConfigurationException("Error in file " + fileName + ": top-level structure must be a list.");
            }
            for (Object o1 : (List<?>) o) {
                if (o1 instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) o1;
                    String eventId = (String) map.get("eventId");
                    String template = (String) map.get("template");

                    MessageTemplate stemplate = new MessageTemplate(template, mdb);

                    templates.put(eventId, stemplate);
                } else {
                    throw new ConfigurationException(
                            "Error in file " + fileName + ": each list element must be a map.");
                }
            }
        }

        private String generateKey(String eventId, Integer apid) {
            return apid != null ? eventId + "-" + apid : eventId;
        }

        // Format method to replace the template parameters with actual values
        public String format(int apid, String eventId, ParameterValueList params) {
            String key = generateKey(eventId, apid);
            MessageTemplate template = templates.get(key);

            if (template == null) {
                key = generateKey(eventId, null);
                template = templates.get(key);
            }

            if (template == null) {
                return null; // No template found
            }
            return template.format(new ParameterValueResolver() {

                @Override
                public ParameterValue resolve(String name) {
                    for (var pv : params) {
                        if (name.equals(pv.getParameter().getName())) {
                            return pv;
                        }
                    }
                    return null;
                }

                @Override
                public ParameterValue resolve(Parameter p) {             
                    return params.getLastInserted(p);
                }
            });
        }
    }
}
