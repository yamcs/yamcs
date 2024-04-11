package org.yamcs.http.api;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.http.BadRequestException;
import org.yamcs.mdb.ConditionParser;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo.Type;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.PolynomialCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo.SplinePointInfo;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.mdb.Mdb;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.util.NameReference;

public class GbpToXtceAssembler {

    public static Calibrator toCalibrator(CalibratorInfo ci) throws BadRequestException {

        if (ci.getType() == Type.POLYNOMIAL) {
            if (!ci.hasPolynomialCalibrator()) {
                throw new BadRequestException("PolynomialCalibrator field not set");
            }
            return toPolynomialCalibrator(ci.getPolynomialCalibrator());
        } else if (ci.getType() == Type.SPLINE) {
            if (!ci.hasSplineCalibrator()) {
                throw new BadRequestException("SplineCalibrator field not set");
            }
            return toSplineCalibrator(ci.getSplineCalibrator());
        } else {
            throw new BadRequestException("Unsupported calibrator type '" + ci.getType() + "'");
        }
    }

    public static PolynomialCalibrator toPolynomialCalibrator(PolynomialCalibratorInfo pci) {
        double[] c = new double[pci.getCoefficientCount()];
        for (int i = 0; i < c.length; i++) {
            c[i] = pci.getCoefficient(i);
        }
        return new PolynomialCalibrator(c);
    }

    public static SplineCalibrator toSplineCalibrator(SplineCalibratorInfo sci) {
        List<SplinePoint> c = new ArrayList<>(sci.getPointCount());
        for (int i = 0; i < sci.getPointCount(); i++) {
            SplinePointInfo spi = sci.getPoint(i);
            c.add(new SplinePoint(spi.getRaw(), spi.getCalibrated()));
        }
        return new SplineCalibrator(c);
    }

    // spaceSystemName is the name of the space system used to lookup parameters which may be part of context
    // specification in string format
    public static List<ContextCalibrator> toContextCalibratorList(Mdb mdb, String spaceSystemName,
            List<ContextCalibratorInfo> ccl) throws BadRequestException {
        List<ContextCalibrator> l = new ArrayList<>(ccl.size());
        for (ContextCalibratorInfo cci : ccl) {
            l.add(toContextCalibrator(mdb, spaceSystemName, cci));
        }
        return l;
    }

    public static ContextCalibrator toContextCalibrator(Mdb mdb, String spaceSystemName,
            ContextCalibratorInfo cci) throws BadRequestException {
        MatchCriteria mc = null;
        if (cci.hasContext()) {
            mc = toMatchCriteria(mdb, spaceSystemName, cci.getContext());
        } else if (cci.getComparisonCount() > 0) {
            mc = toMatchCriteria(mdb, cci.getComparisonList());
        } else {
            throw new BadRequestException("No context provided in the ContextAlarmInfo");
        }
        return new ContextCalibrator(mc, toCalibrator(cci.getCalibrator()));
    }

    private static MatchCriteria toMatchCriteria(Mdb mdb, List<ComparisonInfo> comparisonList)
            throws BadRequestException {
        int n = comparisonList.size();
        if (n == 1) {
            return toComparison(mdb, comparisonList.get(0));
        } else {
            ComparisonList cl = new ComparisonList();
            for (ComparisonInfo ci : comparisonList) {
                cl.addComparison(toComparison(mdb, ci));
            }
            return cl;
        }
    }

    private static Comparison toComparison(Mdb mdb, ComparisonInfo ci) throws BadRequestException {
        if (!ci.hasParameter()) {
            throw new BadRequestException("ComparisonInfo has no parameter set");
        }
        ParameterInfo pi = ci.getParameter();
        if (!pi.hasQualifiedName()) {
            throw new BadRequestException("ComparisonInfo.ParameterInfo has no qualified name");
        }
        Parameter p = mdb.getParameter(pi.getQualifiedName());
        if (p == null) {
            throw new BadRequestException("Unknown parameter by name '" + pi.getQualifiedName());
        }

        ParameterInstanceRef pir = new ParameterInstanceRef(p);
        Comparison c = new Comparison(pir, ci.getValue(), toOperatorType(ci.getOperator()));
        c.validateValueType();
        return c;
    }

    private static OperatorType toOperatorType(ComparisonInfo.OperatorType ot) {
        switch (ot) {
        case EQUAL_TO:
            return OperatorType.EQUALITY;
        case NOT_EQUAL_TO:
            return OperatorType.INEQUALITY;
        case GREATER_THAN_OR_EQUAL_TO:
            return OperatorType.LARGEROREQUALTHAN;
        case GREATER_THAN:
            return OperatorType.LARGERTHAN;
        case SMALLER_THAN_OR_EQUAL_TO:
            return OperatorType.SMALLEROREQUALTHAN;
        case SMALLER_THAN:
            return OperatorType.SMALLERTHAN;
        default:
            throw new IllegalStateException("Unexpected operator " + ot);
        }
    }

    public static EnumerationAlarm toEnumerationAlarm(AlarmInfo ai) throws BadRequestException {
        if (ai.getStaticAlarmRangeCount() > 0) {
            throw new BadRequestException("Cannot set numeric alarm ranges for an enumerated parameter");
        }
        EnumerationAlarm ea = new EnumerationAlarm();

        ea.setAlarmList(toEnumerationAlarmList(ai.getEnumerationAlarmList()));
        if (ai.hasMinViolations()) {
            ea.setMinViolations(ai.getMinViolations());
        }
        return ea;
    }

    private static List<EnumerationAlarmItem> toEnumerationAlarmList(
            List<org.yamcs.protobuf.Mdb.EnumerationAlarm> enumerationAlarmList) throws BadRequestException {
        List<EnumerationAlarmItem> r = new ArrayList<>(enumerationAlarmList.size());
        for (org.yamcs.protobuf.Mdb.EnumerationAlarm ea : enumerationAlarmList) {
            r.add(new EnumerationAlarmItem(ea.getLabel(), toAlarmsLevel(ea.getLevel())));
        }
        return r;
    }

    private static AlarmLevels toAlarmsLevel(AlarmLevelType level) throws BadRequestException {
        switch (level) {
        case CRITICAL:
            return AlarmLevels.CRITICAL;
        case DISTRESS:
            return AlarmLevels.DISTRESS;
        case SEVERE:
            return AlarmLevels.SEVERE;
        case WARNING:
            return AlarmLevels.WARNING;
        case WATCH:
            return AlarmLevels.WATCH;
        case NORMAL:
            throw new BadRequestException("Normal alarm range does not need to be specified");
        default:
            throw new IllegalStateException("unknown alarm level " + level);
        }
    }

    public static NumericAlarm toNumericAlarm(AlarmInfo ai) throws BadRequestException {
        if (ai.getEnumerationAlarmCount() > 0) {
            throw new BadRequestException("Cannot set enumeration alarms for an numeric parameter");
        }
        NumericAlarm na = new NumericAlarm();
        if (ai.hasMinViolations()) {
            na.setMinViolations(ai.getMinViolations());
        }
        na.setStaticAlarmRanges(toStaticAlarmRanges(ai.getStaticAlarmRangeList()));
        return na;
    }

    public static AlarmRanges toStaticAlarmRanges(List<AlarmRange> alarmRangeList) throws BadRequestException {
        AlarmRanges ar = new AlarmRanges();
        for (AlarmRange a : alarmRangeList) {
            if (!a.hasLevel()) {
                throw new BadRequestException("no level specified for alarm");
            }
            switch (a.getLevel()) {
            case CRITICAL:
                ar.addCriticalRange(toDoubleRange(a));
                break;
            case DISTRESS:
                ar.addDistressRange(toDoubleRange(a));
                break;
            case SEVERE:
                ar.addSevereRange(toDoubleRange(a));
                break;
            case WARNING:
                ar.addWarningRange(toDoubleRange(a));
                break;
            case WATCH:
                ar.addWatchRange(toDoubleRange(a));
                break;
            case NORMAL:
                throw new BadRequestException("Normal alarm range does not need to be specified");
            default:
                break;
            }
        }
        return ar;
    }

    private static DoubleRange toDoubleRange(AlarmRange a) {
        boolean minIncl = false;
        boolean maxIncl = false;
        double min = Double.NEGATIVE_INFINITY;
        double max = Double.POSITIVE_INFINITY;
        if (a.hasMinInclusive()) {
            minIncl = true;
            min = a.getMinInclusive();
        } else if (a.hasMinExclusive()) {
            min = a.getMinExclusive();
        }
        if (a.hasMaxInclusive()) {
            maxIncl = true;
            max = a.getMaxInclusive();
        } else if (a.hasMaxExclusive()) {
            max = a.getMaxExclusive();
        }

        return new DoubleRange(min, max, minIncl, maxIncl);
    }

    public static List<EnumerationContextAlarm> toEnumerationContextAlarm(Mdb mdb,
            String spaceSystemName, List<ContextAlarmInfo> contextAlarmList) throws BadRequestException {
        List<EnumerationContextAlarm> l = new ArrayList<>(contextAlarmList.size());
        for (ContextAlarmInfo cai : contextAlarmList) {
            if (cai.hasContext()) {
                l.add(toEnumerationContextAlarm(mdb, spaceSystemName, cai));
            }
        }
        return l;
    }

    /**
     * 
     * @param mdb
     * @param spaceSystemName
     *            - the name of the space system used to lookup parameters reference by relative name in the context
     *            specification
     * @param cai
     * @return
     * @throws BadRequestException
     */
    public static EnumerationContextAlarm toEnumerationContextAlarm(Mdb mdb, String spaceSystemName,
            ContextAlarmInfo cai)
            throws BadRequestException {
        EnumerationContextAlarm eca = new EnumerationContextAlarm();
        if (cai.hasContext()) {
            eca.setContextMatch(toMatchCriteria(mdb, spaceSystemName, cai.getContext()));
        } else if (cai.getComparisonCount() > 0) {
            eca.setContextMatch(toMatchCriteria(mdb, cai.getComparisonList()));
        } else {
            throw new BadRequestException("No context provided in the ContextAlarmInfo");
        }

        if (!cai.hasAlarm()) {
            throw new BadRequestException("No alarm specified for the context");
        }
        AlarmInfo ai = cai.getAlarm();
        if (ai.getStaticAlarmRangeCount() > 0) {
            throw new BadRequestException("Cannot set numeric alarm ranges for an enumerated parameter");
        }
        eca.setAlarmList(toEnumerationAlarmList(ai.getEnumerationAlarmList()));
        return eca;
    }

    public static List<NumericContextAlarm> toNumericContextAlarm(Mdb mdb,
            String spaceSystemName, List<ContextAlarmInfo> contextAlarmList) throws BadRequestException {
        List<NumericContextAlarm> l = new ArrayList<>(contextAlarmList.size());
        for (ContextAlarmInfo cai : contextAlarmList) {
            l.add(toNumericContextAlarm(mdb, spaceSystemName, cai));
        }
        return l;
    }

    public static NumericContextAlarm toNumericContextAlarm(Mdb mdb, String spaceSystemName, ContextAlarmInfo cai)
            throws BadRequestException {
        NumericContextAlarm nca = new NumericContextAlarm();
        if (cai.hasContext()) {
            nca.setContextMatch(toMatchCriteria(mdb, spaceSystemName, cai.getContext()));
        } else if (cai.getComparisonCount() > 0) {
            nca.setContextMatch(toMatchCriteria(mdb, cai.getComparisonList()));
        } else {
            throw new BadRequestException("No context provided in the ContextAlarmInfo");
        }

        if (!cai.hasAlarm()) {
            throw new BadRequestException("No alarm specified for the context");
        }
        AlarmInfo ai = cai.getAlarm();
        if (ai.getEnumerationAlarmCount() > 0) {
            throw new BadRequestException("Cannot set enumeration alarms for an numeric parameter");
        }
        nca.setStaticAlarmRanges(toStaticAlarmRanges(ai.getStaticAlarmRangeList()));
        return nca;
    }

    private static MatchCriteria toMatchCriteria(Mdb mdb, String spaceSystemName, String context)
            throws BadRequestException {
        List<NameReference> unresolvedRefs = new ArrayList<>();

        ConditionParser condParser = new ConditionParser(pname -> {
            Parameter p = null;
            if (pname.startsWith("/")) {
                p = mdb.getParameter(pname);
            } else {
                p = mdb.getParameter(spaceSystemName + "/" + pname);
            }
            NameReference nr = new NameReference(pname, NameReference.Type.PARAMETER);
            if (p != null) {
                nr.resolved(p);
            } else {
                unresolvedRefs.add(nr);
            }
            return nr;
        });

        try {
            MatchCriteria mc = condParser.parseMatchCriteria(context);
            if (!unresolvedRefs.isEmpty()) {
                throw new BadRequestException("Unknown references in context expression: "
                        + unresolvedRefs.stream().map(unr -> unr.getReference()).collect(Collectors.joining(",")));
            }
            return mc;
        } catch (ParseException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
