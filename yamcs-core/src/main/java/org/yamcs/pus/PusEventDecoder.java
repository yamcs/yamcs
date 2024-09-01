package org.yamcs.pus;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.DoubleValue;
import org.yamcs.parameter.FloatValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.SInt32Value;
import org.yamcs.parameter.SInt64Value;
import org.yamcs.parameter.UInt32Value;
import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.time.TimeService;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;
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
            tmExtractor = new XtceTmExtractor(mdb);
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
            System.out.println("aici msg: " + msg + " apid: " + apid + " eventId: " + eventIdValue);
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
        private Map<String, List<TemplatePart>> templates = new HashMap<>();

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

                    List<TemplatePart> templateList = parseTemplateParts(template);

                    templates.put(eventId, templateList);
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
            List<TemplatePart> template = templates.get(key);

            if (template == null) {
                key = generateKey(eventId, null);
                template = templates.get(key);
            }

            if (template == null) {
                return null; // No template found
            }

            StringBuilder result = new StringBuilder();
            for (var tp : template) {
                System.out.println("aici tp: " + tp);
                var s = tp.format(params);
                if (s != null) {
                    result.append(s);
                }
            }
            return result.toString();
        }

        // Method to replace placeholders in the template with actual parameter values
        private List<TemplatePart> parseTemplateParts(String template) {
            List<TemplatePart> result = new ArrayList<>();
            int start = 0;

            while (start < template.length()) {
                int openBrace = template.indexOf('{', start);
                if (openBrace == -1) {
                    // Add the remaining text as a TextTemplatePart
                    result.add(new TextTemplatePart(template.substring(start)));
                    break;
                }

                // Add any text before the open brace as a TextTemplatePart
                if (openBrace > start) {
                    result.add(new TextTemplatePart(template.substring(start, openBrace)));
                }

                int closeBrace = template.indexOf('}', openBrace);
                if (closeBrace == -1) {
                    // Unmatched '{' found; treat it as plain text
                    result.add(new TextTemplatePart(template.substring(openBrace)));
                    break;
                }

                // Extract the placeholder content between '{' and '}'
                String placeholder = template.substring(openBrace + 1, closeBrace);
                result.add(new ParameterTemplatePart(placeholder));

                // Move the start position past the closing brace for the next iteration
                start = closeBrace + 1;
            }

            return result;
        }
    }

    interface TemplatePart {
        String format(ParameterValueList params);
    }

    record TextTemplatePart(String text) implements TemplatePart {
        @Override
        public String format(ParameterValueList params) {
            return text;
        }
    }

    class ParameterTemplatePart implements TemplatePart {
        Parameter para;
        String paraName;
        boolean raw;
        PathElement[] path;
        String format;

        ParameterTemplatePart(String s) {
            String[] a = s.split(";");
            if (a.length > 1) {
                this.format = a[1].trim();
                s = a[0].trim();
            }

            if (s.endsWith(".raw")) {
                this.raw = true;
                s = s.substring(0, s.length() - 4);
            }
            var idx = s.indexOf('.');
            if (idx == -1) {
                this.paraName = s;
            } else {
                this.paraName = s.substring(0, idx);
                this.path = AggregateUtil.parseReference(s.substring(idx + 1));
            }

            if (this.paraName.startsWith("/")) {
                this.para = mdb.getParameter(this.paraName);
                if (this.para == null) {
                    throw new ConfigurationException("Cannot find parameter " + this.paraName);
                }
            }
        }

        @Override
        public String format(ParameterValueList params) {
            System.out.println("params: " + params);
            ParameterValue pv = null;
            if (para != null) {
                pv = params.getFirstInserted(para);
            } else {
                for (var pv1 : params) {
                    if (paraName.equals(pv1.getParameter().getName())) {
                        pv = pv1;
                        break;
                    }
                }
            }
            if (pv == null) {
                return null;
            }
            Value v = raw ? pv.getRawValue() : pv.getEngValue();
            if (path != null) {
                v = AggregateUtil.getMemberValue(v, path);
            }
            if (v == null) {
                return null;
            }
            if (format != null) {
                try {
                    return format(v);
                } catch (IllegalFormatException e) {
                    log.warn("Invalid format {} for parameter {}: {}", format, paraName, e.getMessage());
                    return v.toString();
                }
            } else {
                return v.toString();
            }
        }

        @Override
        public String toString() {
            return "ParameterTemplatePart [para=" + para + ", paraName=" + paraName + ", raw=" + raw + ", path="
                    + Arrays.toString(path) + ", format=" + format + "]";
        }

        private String format(Value v) {
            if (v instanceof FloatValue v1) {
                return String.format(format, v1.getFloatValue());
            } else if (v instanceof DoubleValue v1) {
                return String.format(format, v1.getDoubleValue());
            } else if (v instanceof SInt32Value v1) {
                return String.format(format, v1.getSint32Value());
            } else if (v instanceof SInt64Value v1) {
                return String.format(format, v1.getSint64Value());
            } else if (v instanceof UInt32Value v1) {
                return String.format(format, v1.getUint32Value());
            } else if (v instanceof UInt64Value v1) {
                return String.format(format, v1.getUint64Value());
            } else {
                return v.toString();
            }
        }
    }
}
