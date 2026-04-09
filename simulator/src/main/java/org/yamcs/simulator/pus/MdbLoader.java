package org.yamcs.simulator.pus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.BooleanExpression;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.ExpressionList;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.xml.XtceStaxReader;

/**
 * Simulator-side helper that reads the XTCE XML and extracts a few small pieces of information needed by the embedded
 * simulator (APIDs, Service 5 event definitions, and Service 5 event-report subtypes).
 * <p>
 * Yamcs server already loads the MDB via core {@code XtceLoader}, but the simulator code runs independently and does not
 * have a simple API hook to reuse that in-memory MDB.
 */
public class MdbLoader {

    private static final Logger log = LoggerFactory.getLogger(MdbLoader.class);

    private static final String EVENT_DEFINITION_ID = "event_definition_id";
    private static final String APID = "apid";

    private static volatile String cachedSpec;
    private static volatile SpaceSystem cachedSpaceSystem;

    public static Map<Integer, String> loadEventDefinitions(String resourcePath) {
        SpaceSystem ss = loadSpaceSystem(resourcePath);
        if (ss == null) return Map.of();

        Map<Integer, String> out = new LinkedHashMap<>();
        EnumeratedParameterType ptype = findEnumType(ss, EVENT_DEFINITION_ID);
        if (ptype != null) {
            for (ValueEnumeration ve : ptype.getValueEnumerationList()) {
                long v = ve.getValue();
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                    out.put((int) v, ve.getLabel());
                }
            }
        }
        if (out.isEmpty()) {
            log.warn("No event definitions found in MDB at {} (expected EnumeratedParameterType '{}')",
                    resourcePath, EVENT_DEFINITION_ID);
        } else {
            log.info("Loaded {} event definition(s) from MDB: {}", out.size(), out);
        }
        return out;
    }

    public static Map<String, Integer> loadApids(String resourcePath) {
        SpaceSystem ss = loadSpaceSystem(resourcePath);
        if (ss == null) return Map.of();

        Map<String, Integer> out = new LinkedHashMap<>();
        EnumeratedParameterType ptype = findEnumType(ss, APID);
        if (ptype != null) {
            for (ValueEnumeration ve : ptype.getValueEnumerationList()) {
                long v = ve.getValue();
                if (v >= 0 && v <= Integer.MAX_VALUE) {
                    out.put(ve.getLabel(), (int) v);
                }
            }
        }
        return out;
    }

    public static List<Integer> loadEventReportSubtypes(String resourcePath) {
        SpaceSystem ss = loadSpaceSystem(resourcePath);
        if (ss == null) return List.of();

        TreeSet<Integer> subtypes = new TreeSet<>();
        forEachContainer(ss, sc -> {
            if (!containsParameter(sc, EVENT_DEFINITION_ID)) return;
            Integer st = findEqualsInt(sc.getRestrictionCriteria(), "service_type");
            Integer sst = findEqualsInt(sc.getRestrictionCriteria(), "subservice_type");
            if (st != null && st == 5 && sst != null) subtypes.add(sst);
        });
        return List.copyOf(subtypes);
    }

    public static int loadHousekeepingPacketSize(String resourcePath, String apidLabel, int structureId) {
        SpaceSystem ss = loadSpaceSystem(resourcePath);
        if (ss == null) {
            return -1;
        }

        SequenceContainer match = findHousekeepingContainer(ss, apidLabel, structureId);
        return match == null ? -1 : match.getSizeInBits();
    }

    private static SpaceSystem loadSpaceSystem(String spec) {
        SpaceSystem cached = cachedSpaceSystem;
        if (cached != null && spec != null && spec.equals(cachedSpec)) return cached;

        try {
            SpaceSystem ss = new XtceStaxReader(resolveToReadableXml(spec).toString()).readXmlDocument();
            cachedSpec = spec;
            cachedSpaceSystem = ss;
            return ss;
        } catch (Exception e) {
            log.error("Failed to load MDB from {}", spec, e);
            return null;
        }
    }

    private static Path resolveToReadableXml(String spec) throws IOException {
        if (spec == null || spec.isBlank()) throw new IOException("No MDB spec provided");

        Path p = Paths.get(spec);
        if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();

        p = Paths.get("target", "yamcs").resolve(spec);
        if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();

        String resource = spec.startsWith("/") ? spec : "/" + spec;
        try (InputStream in = MdbLoader.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("MDB XML not found for spec '" + spec + "'");
            Path tmp = Files.createTempFile("yamcs-mdb-", ".xml");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp.toAbsolutePath().normalize();
        }
    }

    private static EnumeratedParameterType findEnumType(SpaceSystem ss, String name) {
        for (var t : ss.getParameterTypes()) {
            if (t instanceof EnumeratedParameterType ept && name.equals(ept.getName())) return ept;
        }
        for (SpaceSystem sub : ss.getSubSystems()) {
            EnumeratedParameterType found = findEnumType(sub, name);
            if (found != null) return found;
        }
        return null;
    }

    private static void forEachContainer(SpaceSystem ss, java.util.function.Consumer<SequenceContainer> consumer) {
        ss.getSequenceContainers().forEach(consumer);
        for (SpaceSystem sub : ss.getSubSystems()) forEachContainer(sub, consumer);
    }

    private static SequenceContainer findHousekeepingContainer(SpaceSystem ss, String apidLabel, int structureId) {
        for (SequenceContainer sc : ss.getSequenceContainers()) {
            SequenceContainer found = findHousekeepingContainer(sc, apidLabel, structureId);
            if (found != null) {
                return found;
            }
        }
        for (SpaceSystem sub : ss.getSubSystems()) {
            SequenceContainer found = findHousekeepingContainer(sub, apidLabel, structureId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static SequenceContainer findHousekeepingContainer(SequenceContainer sc, String apidLabel, int structureId) {
        if (!containsParameter(sc, "structure_id")) {
            return null;
        }
        Integer st = findEqualsInt(sc.getRestrictionCriteria(), "service_type");
        Integer sst = findEqualsInt(sc.getRestrictionCriteria(), "structure_id");
        String apid = findEqualsString(sc.getRestrictionCriteria(), "apid");
        if (st != null && st == 3 && sst != null && sst == structureId && (apidLabel == null || apidLabel.equals(apid))) {
            return sc;
        }
        return null;
    }

    private static boolean containsParameter(SequenceContainer sc, String parameterName) {
        for (SequenceEntry se : sc.getEntryList()) {
            if (se instanceof ParameterEntry pe) {
                Parameter p = pe.getParameter();
                if (p != null && parameterName.equals(p.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Integer findEqualsInt(MatchCriteria mc, String parameterName) {
        if (mc == null) return null;

        if (mc instanceof Comparison c) {
            if (c.getComparisonOperator() != OperatorType.EQUALITY) return null;
            return parseEq((c.getRef() instanceof org.yamcs.xtce.ParameterInstanceRef pir) ? pir.getParameter() : null,
                    c.getStringValue(), parameterName);
        }
        if (mc instanceof Condition cond) {
            if (cond.getComparisonOperator() != OperatorType.EQUALITY) return null;
            return parseEq((cond.getLeftRef() instanceof org.yamcs.xtce.ParameterInstanceRef pir) ? pir.getParameter()
                    : null, cond.getRightValue(), parameterName);
        }
        if (mc instanceof ComparisonList cl) {
            for (Comparison c : cl.getComparisonList()) {
                Integer v = findEqualsInt(c, parameterName);
                if (v != null) return v;
            }
            return null;
        }
        if (mc instanceof ExpressionList el) {
            for (BooleanExpression be : el.getExpressionList()) {
                Integer v = findEqualsInt(be, parameterName);
                if (v != null) return v;
            }
            return null;
        }
        return null;
    }

    private static String findEqualsString(MatchCriteria mc, String parameterName) {
        if (mc == null) return null;

        if (mc instanceof Comparison c) {
            if (c.getComparisonOperator() != OperatorType.EQUALITY) return null;
            return parseEqString((c.getRef() instanceof org.yamcs.xtce.ParameterInstanceRef pir) ? pir.getParameter()
                    : null, c.getStringValue(), parameterName);
        }
        if (mc instanceof Condition cond) {
            if (cond.getComparisonOperator() != OperatorType.EQUALITY) return null;
            return parseEqString((cond.getLeftRef() instanceof org.yamcs.xtce.ParameterInstanceRef pir) ? pir.getParameter()
                    : null, cond.getRightValue(), parameterName);
        }
        if (mc instanceof ComparisonList cl) {
            for (Comparison c : cl.getComparisonList()) {
                String v = findEqualsString(c, parameterName);
                if (v != null) return v;
            }
            return null;
        }
        if (mc instanceof ExpressionList el) {
            for (BooleanExpression be : el.getExpressionList()) {
                String v = findEqualsString(be, parameterName);
                if (v != null) return v;
            }
            return null;
        }
        return null;
    }

    private static Integer parseEq(Parameter p, String rhs, String expectedParamName) {
        if (p == null || rhs == null || !expectedParamName.equals(p.getName())) return null;
        try {
            return Integer.parseInt(rhs.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String parseEqString(Parameter p, String rhs, String expectedParamName) {
        if (p == null || rhs == null || !expectedParamName.equals(p.getName())) return null;
        return rhs.trim();
    }
}
