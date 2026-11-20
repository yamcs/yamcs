package org.yamcs.simulator.pus;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterOrArgumentRef;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.util.DataTypeUtil;

/**
 * Fixed-layout telemetry templates from a flight MDB, keyed by APID, service, subtype, and SID/event ID.
 * Exact selectors and the inherited entry layout identify each typed report. Payload values are raw synthetic
 * samples: a representable integer near zero, the first encodable enumeration, and finite floats equal to one.
 * An unsupported layout is reported with its qualified container name so other reports can still be simulated.
 */
public final class PusMdbTelemetry {
    private static final Logger log = LoggerFactory.getLogger(PusMdbTelemetry.class);
    private static final int MAX_PACKET_BITS = (0xFFFF + 7) * 8;

    public record Report(int apid, int service, int subtype, int id, byte[] data) {
        public Report {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    private final int sourceDataOffset;
    private final List<Report> housekeeping;
    private final List<Report> events;

    private PusMdbTelemetry(int sourceDataOffset, List<Report> housekeeping, List<Report> events) {
        this.sourceDataOffset = sourceDataOffset;
        this.housekeeping = List.copyOf(housekeeping);
        this.events = List.copyOf(events);
    }

    public static PusMdbTelemetry load(Path file) {
        return load(List.of(file));
    }

    public static PusMdbTelemetry load(List<Path> files) {
        var specs = files.stream()
                .map(file -> YConfiguration.wrap(Map.of("type", "xtce", "spec", file.toAbsolutePath().toString())))
                .toList();
        var reports = new LinkedHashMap<Identity, List<Template>>();
        try {
            var mdb = MdbFactory.createInstance(specs, false, false);
            var leaves = mdb.getSequenceContainers().stream()
                    .filter(c -> mdb.getInheritingContainers(c) == null || mdb.getInheritingContainers(c).isEmpty())
                    .sorted(Comparator.comparing(SequenceContainer::getQualifiedName))
                    .toList();
            for (var leaf : leaves) {
                try {
                    var template = readTemplate(leaf);
                    if (template != null) {
                        reports.computeIfAbsent(template.identity, key -> new ArrayList<>()).add(template);
                    }
                } catch (IllegalArgumentException | ArithmeticException e) {
                    log.warn("Skipping simulator telemetry container {}: {}", leaf.getQualifiedName(), e.getMessage());
                }
            }
        } catch (DatabaseLoadException e) {
            throw new ConfigurationException("Cannot load simulator telemetry MDB " + files + ": " + e.getMessage(), e);
        }

        var housekeeping = new ArrayList<Report>();
        var events = new ArrayList<Report>();
        int offset = -1;
        for (var entry : reports.entrySet()) {
            var templates = entry.getValue();
            if (templates.size() != 1) {
                log.warn("Skipping simulator telemetry containers with ambiguous header {}: {}", entry.getKey(),
                        templates.stream().map(Template::name).toList());
                continue;
            }
            var template = templates.getFirst();
            if (offset == -1) {
                offset = template.sourceDataOffset;
            } else if (offset != template.sourceDataOffset) {
                log.warn("Skipping simulator telemetry container {}: source data starts at byte {}, expected {}",
                        template.name, template.sourceDataOffset, offset);
                continue;
            }
            var id = entry.getKey();
            var report = new Report(id.apid, id.service, id.subtype, id.id, template.body);
            (id.service == 3 ? housekeeping : events).add(report);
        }

        log.info("Read {} ST[03] and {} ST[05] simulator telemetry templates from {}",
                housekeeping.size(), events.size(), files);
        return new PusMdbTelemetry(offset, housekeeping, events);
    }

    /** Byte offset of the SID/event ID in a full packet, or -1 if no usable templates were found. */
    public int sourceDataOffset() {
        return sourceDataOffset;
    }

    public List<Report> housekeeping() {
        return housekeeping;
    }

    public List<Report> events() {
        return events;
    }

    private static Template readTemplate(SequenceContainer leaf) {
        var chain = new ArrayList<SequenceContainer>();
        var selectors = new ArrayList<Criterion>();
        boolean supportedCriteria = true;
        for (var container = leaf; container != null; container = container.getBaseContainer()) {
            chain.add(container);
            supportedCriteria &= collectCriteria(container.getRestrictionCriteria(), selectors);
        }

        var serviceCriterion = equality(selectors, Selector.SERVICE);
        var subserviceCriterion = equality(selectors, Selector.SUBSERVICE);
        if (serviceCriterion == null || subserviceCriterion == null) {
            return null;
        }
        int service = Math.toIntExact(serviceCriterion.value());
        int subtype = Math.toIntExact(subserviceCriterion.value());
        Selector idSelector;
        if (service == 3 && (subtype == 25 || subtype == 26)) {
            idSelector = Selector.STRUCTURE;
        } else if (service == 5 && subtype >= 1 && subtype <= 4) {
            idSelector = Selector.EVENT;
        } else {
            return null;
        }
        var idCriterion = equality(selectors, idSelector);
        if (idCriterion == null) {
            // A base container for service-wide decoding is not a typed report.
            return null;
        }

        var apidCriterion = equality(selectors, Selector.APID);
        if (apidCriterion == null || !supportedCriteria) {
            throw new IllegalArgumentException("requires conjunctive PUS selectors with an explicit APID");
        }
        int apid = Math.toIntExact(apidCriterion.value());
        int id = Math.toIntExact(idCriterion.value());
        if (apid < 1 || apid >= 0x7FF || id < 0 || id > 0xFF || idCriterion.ref.getMemberPath() != null) {
            throw new IllegalArgumentException("invalid APID or non-octet structure/event ID");
        }

        var values = Map.of(Selector.APID, apid, Selector.SERVICE, service, Selector.SUBSERVICE, subtype, idSelector, id);
        for (var criterion : selectors) {
            Integer actual = values.get(criterion.selector);
            if (actual == null || !criterion.matches(actual)) {
                throw new IllegalArgumentException("restriction cannot be reproduced: " + criterion.ref);
            }
        }

        Collections.reverse(chain);
        return encodeBody(leaf.getQualifiedName(), chain, idCriterion.ref.getParameter(),
                new Identity(apid, service, subtype, id));
    }

    private static Criterion equality(List<Criterion> selectors, Selector wanted) {
        return selectors.stream()
                .filter(criterion -> criterion.selector == wanted && criterion.operator == OperatorType.EQUALITY)
                .findFirst().orElse(null);
    }

    private static boolean collectCriteria(MatchCriteria criteria, List<Criterion> found) {
        if (criteria == null) {
            return true;
        } else if (criteria instanceof ANDedConditions and) {
            boolean supported = true;
            for (var part : and.getExpressionList()) {
                supported &= collectCriteria(part, found);
            }
            return supported;
        } else if (criteria instanceof ComparisonList list) {
            for (var comparison : list.getComparisonList()) {
                collectCriteria(comparison, found);
            }
            return true;
        } else if (criteria instanceof Comparison comparison) {
            found.add(Criterion.of(comparison.getRef(), comparison.getComparisonOperator(), comparison.getStringValue()));
            return true;
        } else if (criteria instanceof Condition condition && condition.getRightValue() != null) {
            found.add(Criterion.of(condition.getLeftRef(), condition.getComparisonOperator(), condition.getRightValue()));
            return true;
        }
        return false;
    }

    private enum Selector {
        APID, SERVICE, SUBSERVICE, STRUCTURE, EVENT, UNKNOWN;

        static Selector of(ParameterInstanceRef ref) {
            var path = ref.getMemberPath();
            String name = path == null || path.length == 0
                    ? ref.getParameter().getName()
                    : path[path.length - 1].getName();
            if (name == null) {
                return UNKNOWN;
            }
            return switch (name.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "apid", "ccsds_apid" -> APID;
            case "type", "pus_service_type", "service_type" -> SERVICE;
            case "subtype", "pus_subservice_type", "subservice_type" -> SUBSERVICE;
            case "structure_id", "hkid", "sid" -> STRUCTURE;
            case "event_id" -> EVENT;
            default -> UNKNOWN;
            };
        }
    }

    private record Criterion(Selector selector, ParameterInstanceRef ref, OperatorType operator, String literal) {
        static Criterion of(ParameterOrArgumentRef ref, OperatorType operator, String literal) {
            if (!(ref instanceof ParameterInstanceRef parameter)) {
                throw new IllegalArgumentException("telemetry restriction uses a command argument: " + ref);
            }
            return new Criterion(Selector.of(parameter), parameter, operator, literal);
        }

        long value() {
            DataType type = ref.getDataType();
            if (ref.getMemberPath() != null) {
                type = DataTypeUtil.getMemberType(type, ref.getMemberPath());
            }
            if (type instanceof EnumeratedDataType enumeration && ref.useCalibratedValue()) {
                return enumeration.getValueEnumerationList().stream()
                        .filter(choice -> choice.getLabel().equals(literal))
                        .mapToLong(ValueEnumeration::getValue)
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown selector label " + literal));
            }
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException e) {
                return Long.decode(literal);
            }
        }

        boolean matches(int actual) {
            long expected = value();
            return switch (operator) {
            case EQUALITY -> actual == expected;
            case INEQUALITY -> actual != expected;
            case LARGERTHAN -> actual > expected;
            case LARGEROREQUALTHAN -> actual >= expected;
            case SMALLERTHAN -> actual < expected;
            case SMALLEROREQUALTHAN -> actual <= expected;
            };
        }
    }

    private static Template encodeBody(String name, List<SequenceContainer> chain, Parameter idParameter, Identity id) {
        var patches = new ArrayList<Patch>();
        int position = 0;
        int bodyStart = -1;
        int bodyEnd = -1;
        for (var container : chain) {
            for (var entry : container.getEntryList()) {
                if (!(entry instanceof ParameterEntry pe) || entry.getIncludeCondition() != null) {
                    throw new IllegalArgumentException("unsupported sequence entry in " + container.getQualifiedName());
                }
                var parameter = pe.getParameter();
                int location = entry.getLocationInContainerInBits();
                position = entry.getReferenceLocation() == ReferenceLocationType.CONTAINER_START
                        ? location : Math.addExact(position, location);
                checkPosition(position);
                int repetitions = entry.getRepeatEntry() == null ? 1 : count(entry.getRepeatEntry().getCount());
                int gap = entry.getRepeatEntry() == null ? 0 : entry.getRepeatEntry().getOffsetSizeInBits();
                var dimensions = pe instanceof ArrayParameterEntry array ? array.getSize() : null;
                for (int i = 0; i < repetitions; i++) {
                    if (parameter == idParameter) {
                        int end = append(parameter.getParameterType(), dimensions, position, null);
                        if (bodyStart != -1 || position % 8 != 0 || end - position != 8) {
                            throw new IllegalArgumentException("expected one byte-aligned SID/event ID");
                        }
                        bodyStart = position;
                        patches.add(new Patch(position, 8, ByteOrder.BIG_ENDIAN, id.id));
                        position = end;
                    } else {
                        if (bodyStart != -1 && position < bodyEnd) {
                            throw new IllegalArgumentException("overlapping telemetry field " + parameter.getQualifiedName());
                        }
                        position = append(parameter.getParameterType(), dimensions, position,
                                bodyStart != -1 ? patches : null);
                    }
                    position = Math.addExact(position, gap);
                    checkPosition(position);
                    if (bodyStart != -1) {
                        bodyEnd = Math.max(bodyEnd, position);
                    }
                }
            }
        }
        if (bodyStart == -1) {
            throw new IllegalArgumentException("SID/event ID is absent from the packet layout");
        }
        byte[] body = new byte[PusPackedFields.bytesForBits(bodyEnd - bodyStart)];
        var bits = new BitBuffer(body);
        for (var patch : patches) {
            bits.setPosition(patch.position - bodyStart);
            bits.setByteOrder(patch.order);
            bits.putBits(patch.value, patch.width);
        }
        return new Template(name, id, bodyStart / 8, body);
    }

    private static int append(DataType type, List<IntegerValue> dimensions, int position, List<Patch> output) {
        if (type instanceof AggregateDataType aggregate) {
            for (Member member : aggregate.getMemberList()) {
                position = append(member.getType(), null, position, output);
            }
            return position;
        }
        if (type instanceof ArrayDataType array) {
            int length = 1;
            var sizes = dimensions == null ? array.getSize() : dimensions;
            if (sizes == null || sizes.isEmpty()) {
                throw new IllegalArgumentException("unspecified array dimensions for " + type.getName());
            }
            for (var dimension : sizes) {
                length = Math.multiplyExact(length, count(dimension));
            }
            if (length > MAX_PACKET_BITS) {
                throw new IllegalArgumentException("array is too large: " + type.getName());
            }
            for (int i = 0; i < length; i++) {
                position = append(array.getElementType(), null, position, output);
            }
            return position;
        }
        var encoding = type instanceof BaseDataType primitive ? primitive.getEncoding() : null;
        if (encoding == null || encoding.getSizeInBits() < 1) {
            throw new IllegalArgumentException("field has no fixed encoding: " + type.getName());
        }
        int width = encoding.getSizeInBits();
        int end = Math.addExact(position, width);
        checkPosition(end);
        if (output == null) {
            return end;
        }
        if (encoding.getFromBinaryTransformAlgorithm() != null) {
            throw new IllegalArgumentException("field needs a custom decoder: " + type.getName());
        }
        if (encoding instanceof IntegerDataEncoding integer && width <= 64
                && integer.getEncoding() != IntegerDataEncoding.Encoding.STRING) {
            output.add(new Patch(position, width, integer.getByteOrder(), integerSample(type, integer, width)));
        } else if (encoding instanceof FloatDataEncoding floating
                && floating.getEncoding() == FloatDataEncoding.Encoding.IEEE754_1985
                && (width == 32 || width == 64)) {
            long sample = width == 32 ? Float.floatToRawIntBits(1.0f) : Double.doubleToRawLongBits(1.0);
            output.add(new Patch(position, width, floating.getByteOrder(), sample));
        } else if (encoding instanceof BooleanDataEncoding && width == 1) {
            // Zero is a valid false value in the pre-initialized body.
        } else if (encoding instanceof BinaryDataEncoding binary
                && binary.getType() == BinaryDataEncoding.Type.FIXED_SIZE && width % 8 == 0) {
            // Fixed binary data can use the zero bytes already in the body.
        } else if (encoding instanceof StringDataEncoding string && string.getSizeType() == StringDataEncoding.SizeType.FIXED
                && position % 8 == 0 && width % 8 == 0) {
            byte[] space = " ".getBytes(Charset.forName(string.getEncoding()));
            if (space.length != 1) {
                throw new IllegalArgumentException("unsupported fixed string charset for " + type.getName());
            }
            for (int i = 0; i < width; i += 8) {
                output.add(new Patch(position + i, 8, ByteOrder.BIG_ENDIAN, space[0]));
            }
        } else {
            throw new IllegalArgumentException("unsupported fixed encoding for " + type.getName());
        }
        return end;
    }

    private static long integerSample(DataType type, IntegerDataEncoding encoding, int width) {
        var kind = encoding.getEncoding();
        boolean unsigned = kind == IntegerDataEncoding.Encoding.UNSIGNED;
        long max = unsigned ? (width >= 63 ? Long.MAX_VALUE : (1L << width) - 1)
                : (width == 64 ? Long.MAX_VALUE : (1L << (width - 1)) - 1);
        long sample = max >= 1 ? 1 : 0;
        if (type instanceof EnumeratedDataType enumeration) {
            long min = switch (kind) {
            case UNSIGNED -> 0;
            case TWOS_COMPLEMENT -> -max - 1;
            default -> -max;
            };
            sample = enumeration.getValueEnumerationList().stream()
                    .mapToLong(ValueEnumeration::getValue)
                    .filter(value -> value >= min && value <= max)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no encodable enum value for " + type.getName()));
        }
        if (sample < 0) {
            if (kind == IntegerDataEncoding.Encoding.SIGN_MAGNITUDE) {
                return (1L << (width - 1)) | -sample;
            } else if (kind == IntegerDataEncoding.Encoding.ONES_COMPLEMENT) {
                return ~(-sample);
            }
        }
        return sample;
    }

    private static int count(IntegerValue value) {
        if (!(value instanceof FixedIntegerValue fixed) || fixed.getValue() < 0 || fixed.getValue() > MAX_PACKET_BITS) {
            throw new IllegalArgumentException("non-fixed or out-of-range repeat/array count " + value);
        }
        return (int) fixed.getValue();
    }

    private static void checkPosition(int position) {
        if (position < 0 || position > MAX_PACKET_BITS) {
            throw new IllegalArgumentException("field lies outside a CCSDS packet: bit " + position);
        }
    }

    private record Identity(int apid, int service, int subtype, int id) {
    }

    private record Template(String name, Identity identity, int sourceDataOffset, byte[] body) {
    }

    private record Patch(int position, int width, ByteOrder order, long value) {
    }
}
