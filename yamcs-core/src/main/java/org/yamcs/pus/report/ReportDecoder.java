package org.yamcs.pus.report;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ContainerParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.satellite.ReportCell;
import org.yamcs.protobuf.satellite.ReportRow;
import org.yamcs.protobuf.satellite.ScheduledRequestId;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;

import com.google.protobuf.ByteString;

/** Extracts and normalizes one PUS report page at a time using the instance MDB. */
final class ReportDecoder {
    private static final int PUS_SERVICE_OFFSET = 7;
    private static final int PUS_SUBTYPE_OFFSET = 8;
    private static final int PUS_TC_SOURCE_ID_OFFSET = 9;

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(11, 10, "Schedule detail", "time_based_schedule_detail_report",
                    "scheduled_activity_details", RowShape.ARRAY,
                    "subschedule_id group_id release_time tc_packet.packet_version tc_packet.packet_type "
                            + "tc_packet.secondary_header_flag tc_packet.apid tc_packet.sequence_flags "
                            + "tc_packet.sequence_count tc_packet.packet_length tc_packet.packet_data",
                    9, 11, 16),
            new Definition(11, 13, "Schedule summary", "time_based_schedule_summary_report",
                    "scheduled_activity_summaries", RowShape.ARRAY,
                    "subschedule_id group_id release_time request_id.source_id request_id.apid request_id.sequence_count",
                    12, 14, 17),
            new Definition(11, 19, "Sub-schedule status", "time_based_subschedule_status_report",
                    "subschedule_statuses", RowShape.ARRAY, "subschedule_id status", 18),
            new Definition(11, 27, "Scheduling group status", "time_based_scheduling_group_status_report",
                    "scheduling_group_statuses", RowShape.ARRAY, "group_id status", 26),
            storeReport(6, "Application-process storage control",
                    "application_process_storage_control_configuration_report", RowShape.SINGLE,
                    "packet_store_id n_count apid_reports[].apid apid_reports[].service_count "
                            + "apid_reports[].services[].service_type apid_reports[].services[].subservice_count "
                            + "apid_reports[].services[].subservices[]", 5),
            storeReport(13, "Packet store content summary", "packet_store_content_summary_report",
                    RowShape.PACKET_STORES, "packet_store_id oldest_stored_packet_time newest_stored_packet_time "
                            + "open_retrieval_start_time percentage_filled from_open_retrieval_start_time_percentage_filled",
                    12),
            storeReport(19, "Packet store status", "packet_store_status_report",
                    RowShape.PACKET_STORES, "packet_store_id storage_status open_retrieval_status", 18),
            storeReport(23, "Packet store configuration", "packet_store_configuration_report",
                    RowShape.PACKET_STORES, "packet_store_id packet_store_size packet_store_type", 22),
            storeReport(36, "Housekeeping storage control", "housekeeping_report_storage_control_configuration_report",
                    RowShape.SINGLE, storageStructureFields(), 35),
            storeReport(38, "Diagnostic storage control", "diagnostic_report_storage_control_configuration_report",
                    RowShape.SINGLE, storageStructureFields(), 37),
            storeReport(40, "Event blocking storage control", "event_report_blocking_storage_control_configuration_report",
                    RowShape.SINGLE, "packet_store_id n_count apid_reports[].apid apid_reports[].event_count "
                            + "apid_reports[].event_ids[]", 39),
            new Definition(130, 4, "Current TM mode", "current_tm_node_report",
                    "current_tm_mode_report", RowShape.SINGLE, "tm_mode last_transition_time last_trigger remaining_time",
                    3),
            new Definition(130, 8, "Configuration tables", "table_report", "table_report", RowShape.TABLES,
                    "table_id type_count type_reports[].structure_type type_reports[].apid type_reports[].structure_id",
                    7),
            new Definition(130, 14, "Event overlay configuration", "event_overlay_config_report",
                    "event_overlay_config_report", RowShape.EVENTS, "event_id enabled_flag timeout type_count "
                            + "type_reports[].structure_type type_reports[].apid type_reports[].structure_id",
                    13),
            new Definition(130, 16, "Event overlay status", "event_overlays_status_report",
                    "event_overlays_status_report", RowShape.EVENTS, "event_id enabled_flag active_flag remaining_time",
                    15),
            new Definition(130, 18, "Active TM configuration", "active_tm_config_report",
                    "active_tm_config_report", RowShape.ACTIVE_CONFIG,
                    "structure_type apid structure_id source_mask generation_state store_gate_state tx_state", 17),
            new Definition(130, 22, "Pass state", "pass_state_report", "pass_state_report", RowShape.SINGLE,
                    "tm_mode los_time dump_active_flag current_slot_type current_slot_id allotted_time coordinates.X "
                            + "coordinates.Y n_count slot_reports[].slot_type slot_reports[].slot_id "
                            + "slot_reports[].allotted_time slot_reports[].done_flag",
                    21));

    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<Integer, ReportProfile> byRequest = new LinkedHashMap<>();
    private final SequenceContainer root;
    private final XtceTmExtractor extractor;

    ReportDecoder(String instance, Mdb mdb, SequenceContainer root, YConfiguration args) {
        this.root = root == null ? mdb.getRootSequenceContainer() : root;
        if (this.root == null) {
            throw new ConfigurationException("Satellite reports require a root telemetry container");
        }
        var config = new ProcessorConfig();
        config.setSubscribePacketNameContainers(false);
        extractor = new XtceTmExtractor(mdb, new ProcessorData(instance, "SatelliteReports", mdb, config));

        String spaceSystem = this.root.getSubsystemName();
        String defaultPrefix = spaceSystem.endsWith("/") ? spaceSystem : spaceSystem + "/";
        var overrides = args.getConfigOrEmpty("reportProfiles");
        var correlations = args.getConfigOrEmpty("correlation");
        for (Definition definition : DEFINITIONS) {
            String type = definition.type();
            Binding binding = bind(mdb, definition, defaultPrefix, overrides.getConfigOrEmpty(type),
                    correlations.containsKey(type) ? correlations.getConfig(type) : null);
            bindings.put(type, binding);
            for (int subtype : definition.requestSubtypes()) {
                byRequest.put(messageType(definition.service(), subtype), binding.profile());
            }
            extractor.startProviding(binding.container());
            extractor.startProviding(binding.pageNumber());
            extractor.startProviding(binding.totalPages());
            extractor.startProviding(binding.payload());
            if (binding.echo() != null) {
                extractor.startProviding(binding.echo());
            }
        }
        validateKeys(overrides, bindings.keySet());
        validateKeys(correlations, bindings.keySet());
    }

    Collection<ReportProfile> profiles() {
        return bindings.values().stream().map(Binding::profile).toList();
    }

    ReportProfile profile(String type) {
        Binding binding = bindings.get(type);
        return binding == null ? null : binding.profile();
    }

    ReportProfile expectedFor(int service, int subtype) {
        return byRequest.get(messageType(service, subtype));
    }

    DecodedPage decode(byte[] bytes, long generationTime, long receptionTime, int seq, SequenceContainer rootOverride) {
        if (bytes == null || bytes.length <= PUS_SUBTYPE_OFFSET || (bytes[0] & 0xf8) != 0x08) {
            return null;
        }
        String type = Byte.toUnsignedInt(bytes[PUS_SERVICE_OFFSET]) + "-"
                + Byte.toUnsignedInt(bytes[PUS_SUBTYPE_OFFSET]);
        Binding binding = bindings.get(type);
        if (binding == null) {
            return null;
        }

        SequenceContainer packetRoot = rootOverride == null ? root : rootOverride;
        var result = extractor.processPacket(bytes, generationTime, receptionTime, seq, packetRoot);
        if (result.getContainerResult().stream().noneMatch(r -> r.getContainer() == binding.container())) {
            return null;
        }
        var page = packetValue(result, binding.pageNumber(), binding.container());
        var total = packetValue(result, binding.totalPages(), binding.container());
        int pageNumber = page == null ? 0 : (int) page.getRawValue().toLong();
        int totalPages = total == null ? 0 : (int) total.getRawValue().toLong();
        var echoValue = packetValue(result, binding.echo(), binding.container());
        String echo = echoValue == null || echoValue.getEngValue() == null
                ? null
                : canonicalEcho(ValueUtility.toGbp(echoValue.getEngValue()));

        List<ReportRow> rows = List.of();
        String issue = null;
        if (CcsdsPacket.getCcsdsPacketLength(bytes) + 7 != bytes.length) {
            issue = "Packet length differs from CCSDS header";
        } else if (page == null || total == null) {
            issue = "Missing pagination fields in " + binding.profile().containerName();
        } else if (pageNumber < 1 || pageNumber > totalPages || totalPages > 0xffff) {
            issue = "Invalid page number " + pageNumber + " of " + totalPages;
        } else {
            var payload = packetValue(result, binding.payload(), binding.container());
            if (payload == null || payload.getEngValue() == null) {
                issue = "Missing or incomplete payload " + binding.payload().getQualifiedName();
            } else {
                rows = decodeRows(binding, payload);
            }
        }
        return new DecodedPage(binding.profile(), pageNumber, totalPages, receptionTime, generationTime, seq,
                echo, rows, issue);
    }

    static String canonicalEcho(Value value) {
        if (value == null || !value.hasType()) {
            return null;
        }
        return switch (value.getType()) {
        case SINT32 -> value.hasSint32Value() ? "integer:" + value.getSint32Value() : null;
        case SINT64, ENUMERATED -> value.hasSint64Value() ? "integer:" + value.getSint64Value() : null;
        case UINT32 -> value.hasUint32Value() ? "integer:" + Integer.toUnsignedString(value.getUint32Value()) : null;
        case UINT64 -> value.hasUint64Value() ? "integer:" + Long.toUnsignedString(value.getUint64Value()) : null;
        case STRING -> value.hasStringValue() ? "string:" + value.getStringValue() : null;
        case BINARY -> value.hasBinaryValue()
                ? "binary:" + Base64.getEncoder().encodeToString(value.getBinaryValue().toByteArray())
                : null;
        case BOOLEAN -> value.hasBooleanValue() ? "boolean:" + value.getBooleanValue() : null;
        case TIMESTAMP -> value.hasTimestampValue() ? "timestamp:" + value.getTimestampValue() : null;
        case FLOAT -> value.hasFloatValue() ? canonicalDecimal(value.getFloatValue()) : null;
        case DOUBLE -> value.hasDoubleValue() ? canonicalDecimal(value.getDoubleValue()) : null;
        default -> null;
        };
    }

    private static String canonicalDecimal(double value) {
        return Double.isFinite(value) ? "decimal:" + Double.toHexString(value == 0 ? 0 : value) : null;
    }

    private static Binding bind(Mdb mdb, Definition definition, String defaultPrefix, YConfiguration overrides,
            YConfiguration correlation) {
        validateKeys(overrides, Set.of("container", "pageNumberParameter", "totalPagesParameter", "payloadParameter"));
        String containerName = overrides.getString("container", defaultPrefix + definition.containerName());
        SequenceContainer container = mdb.getSequenceContainer(containerName);
        if (container == null || !containerName.equals(container.getQualifiedName())) {
            throw new ConfigurationException("Satellite report " + definition.type() + " has no MDB container "
                    + containerName);
        }

        String paginationPrefix = defaultPrefix + "spare_s" + definition.service();
        Parameter page = resolve(mdb, container, overrides.getString("pageNumberParameter",
                paginationPrefix + "_page_number"));
        Parameter total = resolve(mdb, container, overrides.getString("totalPagesParameter",
                paginationPrefix + "_total_pages"));
        requireUnsignedEncoding(container, page.getQualifiedName(), page.getParameterType(), 16);
        requireUnsignedEncoding(container, total.getQualifiedName(), total.getParameterType(), 16);
        if (page == total) {
            throw invalidField(container, page.getQualifiedName(), "page number and total page count must be distinct");
        }

        Parameter payload = resolve(mdb, container,
                overrides.getString("payloadParameter", defaultPrefix + definition.payload()));
        DataType rowType = payload.getParameterType();
        if (definition.shape().arrayMember != null) {
            String member = definition.shape().arrayMember;
            rowType = arrayElement(container, payload.getQualifiedName() + "." + member,
                    resolveType(container, payload.getQualifiedName(), rowType, member));
        } else if (definition.shape() == RowShape.ARRAY) {
            rowType = arrayElement(container, payload.getQualifiedName(), rowType);
        }
        if (!(rowType instanceof AggregateParameterType aggregate)) {
            throw invalidField(container, payload.getQualifiedName(), "expected aggregate report rows");
        }
        String rowPath = payload.getQualifiedName();
        if (definition.shape().arrayMember != null) {
            rowPath += "." + definition.shape().arrayMember + "[]";
        } else if (definition.shape() == RowShape.ARRAY) {
            rowPath += "[]";
        }
        for (String path : definition.requiredFields().split(" ")) {
            validateFieldType(container, rowPath, aggregate, path);
        }
        for (String optional : List.of("time_range_retrieval_status", "virtual_channel")) {
            if (aggregate.getMember(optional) != null) {
                validateFieldType(container, rowPath, aggregate, optional);
            }
        }

        Map<String, Integer> wireWidths = switch (definition.type()) {
        case "11-10" -> Map.of("tc_packet.packet_version", 3, "tc_packet.packet_type", 1,
                "tc_packet.secondary_header_flag", 1, "tc_packet.apid", 11, "tc_packet.sequence_flags", 2,
                "tc_packet.sequence_count", 14, "tc_packet.packet_length", 16);
        case "11-13" -> Map.of("request_id.source_id", 16, "request_id.apid", 11, "request_id.sequence_count", 14);
        case "130-22" -> Map.of("current_slot_id", 8, "slot_reports[].slot_id", 8);
        default -> Map.of();
        };
        for (var width : wireWidths.entrySet()) {
            String path = width.getKey();
            requireUnsignedEncoding(container, rowPath + "." + path,
                    resolveType(container, rowPath, aggregate, path), width.getValue());
        }

        Parameter echo = null;
        String argument = null;
        String attribute = null;
        if (correlation != null) {
            validateKeys(correlation, Set.of("telemetryParameter", "commandArgument", "commandHistoryAttribute"));
            echo = resolve(mdb, container, correlation.getString("telemetryParameter"));
            argument = correlation.getString("commandArgument", null);
            attribute = correlation.getString("commandHistoryAttribute", null);
            if ((argument == null) == (attribute == null)
                    || (argument != null && argument.isBlank()) || (attribute != null && attribute.isBlank())) {
                throw invalidField(container, echo.getQualifiedName(),
                        "correlation requires either commandArgument or commandHistoryAttribute");
            }
            switch (echo.getParameterType().getValueType()) {
            case SINT32, SINT64, UINT32, UINT64, ENUMERATED, STRING, BINARY, BOOLEAN, TIMESTAMP, FLOAT, DOUBLE -> {
                // Scalar values can carry the same request ID in telemetry and commanding.
            }
            default -> throw invalidField(container, echo.getQualifiedName(), "expected a scalar correlation parameter");
            }
        }
        var profile = new ReportProfile(definition.type(), definition.title(), definition.service(), definition.subtype(),
                containerName, argument, attribute);
        return new Binding(profile, definition.shape(), container, page, total, payload, aggregate, echo);
    }

    private static void validateKeys(YConfiguration config, Set<String> allowed) {
        for (String key : config.getKeys()) {
            if (!allowed.contains(key)) {
                throw new ConfigurationException(config.getPath(), "Unknown satellite report configuration key " + key);
            }
        }
    }

    private static Parameter resolve(Mdb mdb, SequenceContainer container, String qualifiedName) {
        Parameter parameter = mdb.getParameter(qualifiedName);
        if (parameter == null || !qualifiedName.equals(parameter.getQualifiedName())) {
            throw invalidField(container, qualifiedName, "MDB parameter does not exist");
        }
        var entries = mdb.getParameterEntries(parameter);
        if (entries == null
                || entries.stream().noneMatch(e -> isAncestor(e.getSequenceContainer(), container))) {
            throw invalidField(container, qualifiedName, "parameter is not present in the report container");
        }
        return parameter;
    }

    private static boolean isAncestor(SequenceContainer entryContainer, SequenceContainer reportContainer) {
        for (SequenceContainer current = reportContainer; current != null; current = current.getBaseContainer()) {
            if (entryContainer == current) {
                return true;
            }
        }
        return false;
    }

    private static void requireUnsignedEncoding(SequenceContainer container, String field, DataType type, int bits) {
        if (!(type instanceof IntegerParameterType integer) || integer.isSigned()
                || !(integer.getEncoding() instanceof IntegerDataEncoding encoding)
                || encoding.getEncoding() != IntegerDataEncoding.Encoding.UNSIGNED
                || encoding.getSizeInBits() != bits) {
            throw invalidField(container, field, "expected an unsigned " + bits + "-bit integer encoding");
        }
    }

    private static DataType arrayElement(SequenceContainer container, String field, DataType type) {
        if (type instanceof ArrayParameterType array && array.getNumberOfDimensions() == 1) {
            return array.getElementType();
        }
        throw invalidField(container, field, "expected a one-dimensional array");
    }

    private static DataType resolveType(SequenceContainer container, String payload, DataType type, String path) {
        for (String component : path.split("\\.")) {
            boolean isArray = component.endsWith("[]");
            String name = isArray ? component.substring(0, component.length() - 2) : component;
            if (!(type instanceof AggregateParameterType aggregate) || aggregate.getMember(name) == null) {
                throw invalidField(container, payload + "." + path, "MDB member is missing");
            }
            type = aggregate.getMember(name).getType();
            if (isArray) {
                type = arrayElement(container, payload + "." + path, type);
            }
        }
        return type;
    }

    private static void validateFieldType(SequenceContainer container, String rowPath, DataType rowType, String path) {
        var type = resolveType(container, rowPath, rowType, path).getValueType();
        String member = path.substring(path.lastIndexOf('.') + 1);
        boolean integer = switch (type) {
        case SINT32, SINT64, UINT32, UINT64 -> true;
        default -> false;
        };
        boolean compatible = switch (member) {
        case "release_time", "oldest_stored_packet_time", "newest_stored_packet_time", "open_retrieval_start_time",
                "last_transition_time", "los_time" -> type == Value.Type.TIMESTAMP;
        case "packet_data" -> type == Value.Type.BINARY;
        case "dump_active_flag" -> type == Value.Type.BOOLEAN;
        case "status", "storage_status", "open_retrieval_status", "time_range_retrieval_status", "enabled_flag",
                "active_flag", "generation_state", "store_gate_state", "tx_state", "done_flag" ->
            type == Value.Type.BOOLEAN || type == Value.Type.ENUMERATED || integer;
        case "apid", "packet_store_id", "packet_store_type", "tm_mode", "last_trigger", "table_id", "structure_type",
                "current_slot_type", "slot_type" -> type == Value.Type.ENUMERATED || integer;
        case "percentage_filled", "from_open_retrieval_start_time_percentage_filled", "remaining_time", "timeout",
                "allotted_time" -> type == Value.Type.FLOAT || type == Value.Type.DOUBLE || integer;
        default -> integer;
        };
        if (!compatible) {
            throw invalidField(container, rowPath + "." + path, "incompatible MDB value type " + type);
        }
    }

    private static ConfigurationException invalidField(SequenceContainer container, String field, String reason) {
        return new ConfigurationException("Invalid satellite report field " + field + " in "
                + container.getQualifiedName() + ": " + reason);
    }

    private static ContainerParameterValue packetValue(ContainerProcessingResult result, Parameter parameter,
            SequenceContainer report) {
        if (parameter == null) {
            return null;
        }
        ContainerParameterValue match = null;
        for (var value : result.getParameterResult()) {
            if (value.getParameter() == parameter && value instanceof ContainerParameterValue pv
                    && pv.getSequenceEntry() instanceof ParameterEntry entry
                    && isAncestor(entry.getSequenceContainer(), report)) {
                if (match != null) {
                    return null;
                }
                match = pv;
            }
        }
        return match;
    }

    private static List<ReportRow> decodeRows(Binding binding, ContainerParameterValue payload) {
        var value = payload.getEngValue();
        var raw = payload.getRawValue();
        if (binding.shape() == RowShape.SINGLE) {
            return List.of(decodeRow(binding, (AggregateValue) value, (AggregateValue) raw));
        }
        if (binding.shape().arrayMember != null) {
            value = ((AggregateValue) value).getMemberValue(binding.shape().arrayMember);
            raw = ((AggregateValue) raw).getMemberValue(binding.shape().arrayMember);
        }
        var array = (ArrayValue) value;
        var rawArray = (ArrayValue) raw;
        List<ReportRow> rows = new ArrayList<>(array.flatLength());
        for (int i = 0; i < array.flatLength(); i++) {
            rows.add(decodeRow(binding, (AggregateValue) array.getElementValue(i),
                    (AggregateValue) rawArray.getElementValue(i)));
        }
        return List.copyOf(rows);
    }

    private static ReportRow decodeRow(Binding binding, AggregateValue value, AggregateValue raw) {
        var row = ReportRow.newBuilder();
        for (var member : binding.rowType().getMemberList()) {
            String name = member.getName();
            var type = member.getType();
            var field = value.getMemberValue(name);
            String unit = null;
            if (binding.profile().type().equals("15-23") && name.equals("packet_store_size")) {
                field = raw.getMemberValue(name);
                unit = "bytes";
            } else if (name.endsWith("percentage_filled")) {
                unit = "%";
            } else if (type instanceof BaseDataType base && base.getUnitSet() != null && !base.getUnitSet().isEmpty()) {
                unit = base.getUnitSet().getFirst().getUnit();
            }
            row.addCells(cell(name, ValueUtility.toGbp(field), unit));
        }
        switch (binding.profile().type()) {
        case "11-10" -> addScheduledDetail(row, value, raw);
        case "11-13" -> addScheduledSummary(row, value, raw);
        case "130-22" -> {
            boolean applicable = value.getMemberValue("dump_active_flag").getBooleanValue()
                    && raw.getMemberValue("n_count").toLong() > 0;
            row.addCells(cell("current_slot_applicable", ValueUtility.toGbp(ValueUtility.getBooleanValue(applicable)),
                    null));
        }
        default -> {
        }
        }
        return row.build();
    }

    private static void addScheduledDetail(ReportRow.Builder row, AggregateValue value, AggregateValue raw) {
        var tc = (AggregateValue) raw.getMemberValue("tc_packet");
        byte[] data = tc.getMemberValue("packet_data").getBinaryValue();
        byte[] packet = new byte[6 + data.length];
        int apid = number(tc, "apid");
        int sequence = number(tc, "sequence_count");
        int packetId = number(tc, "packet_version") << 13 | number(tc, "packet_type") << 12
                | number(tc, "secondary_header_flag") << 11 | apid;
        int sequenceControl = number(tc, "sequence_flags") << 14 | sequence;
        ByteArrayUtils.encodeUnsignedShort(packetId, packet, 0);
        ByteArrayUtils.encodeUnsignedShort(sequenceControl, packet, 2);
        ByteArrayUtils.encodeUnsignedShort(number(tc, "packet_length"), packet, 4);
        System.arraycopy(data, 0, packet, 6, data.length);

        var request = scheduledRequest(value).setApid(apid).setSequenceCount(sequence)
                .setPacket(ByteString.copyFrom(packet));
        row.addCells(cell("tc_packet_bytes", ValueUtility.toGbp(ValueUtility.getBinaryValue(packet)), null));
        if (data.length >= 5 && (packetId & 0x1800) == 0x1800) {
            int sourceId = ByteArrayUtils.decodeUnsignedShort(packet, PUS_TC_SOURCE_ID_OFFSET);
            request.setSourceId(sourceId);
            row.addCells(cell("source_id", ValueUtility.toGbp(ValueUtility.getUint32Value(sourceId)), null));
            row.addCells(cell("service_type", ValueUtility.toGbp(
                    ValueUtility.getUint32Value(Byte.toUnsignedInt(packet[PUS_SERVICE_OFFSET]))), null));
            row.addCells(cell("subservice_type", ValueUtility.toGbp(
                    ValueUtility.getUint32Value(Byte.toUnsignedInt(packet[PUS_SUBTYPE_OFFSET]))), null));
        }
        row.setScheduledRequest(request);
    }

    private static void addScheduledSummary(ReportRow.Builder row, AggregateValue value, AggregateValue raw) {
        var request = (AggregateValue) raw.getMemberValue("request_id");
        row.setScheduledRequest(scheduledRequest(value)
                .setSourceId(number(request, "source_id"))
                .setApid(number(request, "apid"))
                .setSequenceCount(number(request, "sequence_count")));
    }

    private static ScheduledRequestId.Builder scheduledRequest(AggregateValue row) {
        var request = ScheduledRequestId.newBuilder();
        long releaseTime = row.getMemberValue("release_time").getTimestampValue();
        if (releaseTime != TimeEncoding.INVALID_INSTANT) {
            request.setReleaseTime(TimeEncoding.toProtobufTimestamp(releaseTime));
        }
        return request;
    }

    private static int number(AggregateValue value, String member) {
        return (int) value.getMemberValue(member).toLong();
    }

    private static ReportCell cell(String name, Value value, String unit) {
        var cell = ReportCell.newBuilder().setName(name).setLabel(label(name)).setValue(value);
        if (unit != null) {
            cell.setUnit(unit);
        }
        return cell.build();
    }

    private static String label(String name) {
        return switch (name) {
        case "apid" -> "APID";
        case "subschedule_id" -> "Sub-schedule ID";
        case "packet_store_size" -> "Capacity";
        case "packet_store_type" -> "Store mode";
        case "from_open_retrieval_start_time_percentage_filled" -> "Remaining to retrieve";
        case "tm_mode" -> "TM mode";
        case "los_time" -> "Loss-of-signal time";
        case "tc_packet" -> "Embedded TC";
        case "tc_packet_bytes" -> "Embedded TC bytes";
        case "current_slot_applicable" -> "Current slot applicable";
        case "tx_state" -> "Transmission state";
        default -> {
            String text = name.replace('_', ' ').replace(" id", " ID");
            yield text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
        }
        };
    }

    private static Definition storeReport(int subtype, String title, String container, RowShape shape,
            String requiredFields, int requestSubtype) {
        return new Definition(15, subtype, title, container, container + "_data", shape, requiredFields, requestSubtype);
    }

    private static String storageStructureFields() {
        return "packet_store_id n_count apid_reports[].apid apid_reports[].structure_id_count "
                + "apid_reports[].structure_ids[].structure_id apid_reports[].structure_ids[].subsampling_rate";
    }

    private static int messageType(int service, int subtype) {
        return (service << 8) | subtype;
    }

    private record Definition(int service, int subtype, String title, String containerName, String payload,
            RowShape shape, String requiredFields, int... requestSubtypes) {
        String type() {
            return service + "-" + subtype;
        }
    }

    private record Binding(ReportProfile profile, RowShape shape, SequenceContainer container, Parameter pageNumber,
            Parameter totalPages, Parameter payload, AggregateParameterType rowType, Parameter echo) {
    }

    private enum RowShape {
        ARRAY(null),
        SINGLE(null),
        PACKET_STORES("packet_stores"),
        TABLES("reports"),
        EVENTS("event_reports"),
        ACTIVE_CONFIG("config_reports");

        private final String arrayMember;

        RowShape(String arrayMember) {
            this.arrayMember = arrayMember;
        }

    }

    record DecodedPage(ReportProfile profile, int pageNumber, int totalPages, long receptionTime, long generationTime,
            int sequence, String echo, List<ReportRow> rows, String issue) {
    }
}
