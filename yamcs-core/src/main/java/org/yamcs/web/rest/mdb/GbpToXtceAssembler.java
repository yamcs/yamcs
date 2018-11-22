package org.yamcs.web.rest.mdb;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.PolynomialCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo.Type;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo.SplinePointInfo;
import org.yamcs.utils.DoubleRange;
import org.yamcs.web.BadRequestException;
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
import org.yamcs.xtce.XtceDb;

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
            throw new BadRequestException("Unsuported calibrator type '" + ci.getType() + "'");
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

    public static List<ContextCalibrator> toContextCalibratorList(XtceDb xtcedb, List<ContextCalibratorInfo> ccl)
            throws BadRequestException {
        List<ContextCalibrator> l = new ArrayList<>(ccl.size());
        for (ContextCalibratorInfo cci : ccl) {
            l.add(toContextCalibrator(xtcedb, cci));
        }
        return l;
    }

    public static ContextCalibrator toContextCalibrator(XtceDb xtcedb, ContextCalibratorInfo cci)
            throws BadRequestException {
        MatchCriteria mc = toMatchCriteria(xtcedb, cci.getComparisonList());
        return new ContextCalibrator(mc, toCalibrator(cci.getCalibrator()));
    }

    private static MatchCriteria toMatchCriteria(XtceDb xtcedb, List<ComparisonInfo> comparisonList)
            throws BadRequestException {
        int n = comparisonList.size();
        if (n == 0) {
            return MatchCriteria.ALWAYS_MATCH;
        } else if (n == 1) {
            return toComparison(xtcedb, comparisonList.get(0));
        } else {
            ComparisonList cl = new ComparisonList();
            for (ComparisonInfo ci : comparisonList) {
                cl.addComparison(toComparison(xtcedb, ci));
            }
            return cl;
        }
    }

    private static Comparison toComparison(XtceDb xtcedb, ComparisonInfo ci) throws BadRequestException {
        if (!ci.hasParameter()) {
            throw new BadRequestException("ComparisonInfo has no parameter set");
        }
        ParameterInfo pi = ci.getParameter();
        if (!pi.hasQualifiedName()) {
            throw new BadRequestException("ComparisonInfo.ParameterInfo has no qualified name");
        }
        Parameter p = xtcedb.getParameter(pi.getQualifiedName());
        if (p == null) {
            throw new BadRequestException("Unknown parameter by name '" + pi.getQualifiedName());
        }

        ParameterInstanceRef pir = new ParameterInstanceRef(p);
        Comparison c = new Comparison(pir, ci.getValue(), toOperatorType(ci.getOperator()));
        c.resolveValueType();
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
        return ea;
    }

    private static List<EnumerationAlarmItem> toEnumerationAlarmList(
            List<org.yamcs.protobuf.Mdb.EnumerationAlarm> enumerationAlarmList) throws BadRequestException {
        List<EnumerationAlarmItem> r = new ArrayList<>(enumerationAlarmList.size());
        for(org.yamcs.protobuf.Mdb.EnumerationAlarm ea: enumerationAlarmList) {
            r.add(new EnumerationAlarmItem(ea.getLabel(), toAlarmsLevel(ea.getLevel())));
        }
        return r;
    }


    private static AlarmLevels toAlarmsLevel(AlarmLevelType level) throws BadRequestException {
        switch(level) {
        case CRITICAL:
           return AlarmLevels.critical;
        case DISTRESS:
            return AlarmLevels.distress;
        case SEVERE:
            return AlarmLevels.severe;
        case WARNING:
            return AlarmLevels.warning;
        case WATCH:
            return AlarmLevels.watch;
        case NORMAL:
            throw new BadRequestException("Normal alarm range does not need to be specified");
        default:
            throw new IllegalStateException("unknown alarm level "+level);
        }
    }

    public static NumericAlarm toNumericAlarm(AlarmInfo ai) throws BadRequestException {
        if (ai.getEnumerationAlarmCount() > 0) {
            throw new BadRequestException("Cannot set enumeration alarms for an numeric parameter");
        }
        NumericAlarm na = new NumericAlarm();
        na.setStaticAlarmRanges(toStaticAlarmRanges(ai.getStaticAlarmRangeList()));
        return na;
    }

    public static AlarmRanges toStaticAlarmRanges(List<AlarmRange> alarmRangeList) throws BadRequestException {
        AlarmRanges ar = new AlarmRanges();
        for(AlarmRange a: alarmRangeList) {
            if(!a.hasLevel()) {
                throw new BadRequestException("no level specified for alarm");
            }
            switch(a.getLevel()) {
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
        if(a.hasMinInclusive()) {
            minIncl = true;
            min = a.getMinInclusive();
        } else if(a.hasMinExclusive()) {
            min = a.getMinExclusive();
        }
        if(a.hasMaxInclusive()) {
            maxIncl = true;
            max = a.getMaxInclusive();
        } else if(a.hasMaxExclusive()) {
            max = a.getMaxExclusive();
        }
        
        return new DoubleRange(min, max, minIncl, maxIncl);
    }

    public static List<EnumerationContextAlarm> toEnumerationContextAlarm(XtceDb xtcedb, List<ContextAlarmInfo> contextAlarmList) throws BadRequestException {
        List<EnumerationContextAlarm> l = new ArrayList<>(contextAlarmList.size());
        for(ContextAlarmInfo cai: contextAlarmList) {
            l.add(toEnumerationContextAlarm(xtcedb, cai));
        }
        return l;
    }

    public static EnumerationContextAlarm toEnumerationContextAlarm(XtceDb xtcedb, ContextAlarmInfo cai) throws BadRequestException {
        EnumerationContextAlarm eca = new EnumerationContextAlarm();
        eca.setContextMatch(toMatchCriteria(xtcedb, cai.getComparisonList()));
        if(!cai.hasAlarm()) {
            throw new BadRequestException("No alarm specified for the context");
        }
        AlarmInfo ai = cai.getAlarm();
        if (ai.getStaticAlarmRangeCount() > 0) {
            throw new BadRequestException("Cannot set numeric alarm ranges for an enumerated parameter");
        }
        eca.setAlarmList(toEnumerationAlarmList(ai.getEnumerationAlarmList()));
        return eca;
    }

    public static List<NumericContextAlarm> toNumericContextAlarm(XtceDb xtcedb, List<ContextAlarmInfo> contextAlarmList) throws BadRequestException {
        List<NumericContextAlarm> l = new ArrayList<>(contextAlarmList.size());
        for(ContextAlarmInfo cai: contextAlarmList) {
            l.add(toNumericContextAlarm(xtcedb, cai));
        }
        return l;
    }
    public static NumericContextAlarm toNumericContextAlarm(XtceDb xtcedb, ContextAlarmInfo cai) throws BadRequestException {
        NumericContextAlarm nca = new NumericContextAlarm();
        nca.setContextMatch(toMatchCriteria(xtcedb, cai.getComparisonList()));
        if(!cai.hasAlarm()) {
            throw new BadRequestException("No alarm specified for the context");
        }
        AlarmInfo ai = cai.getAlarm();
        if (ai.getEnumerationAlarmCount() > 0) {
            throw new BadRequestException("Cannot set enumeration alarms for an numeric parameter");
        }
        nca.setStaticAlarmRanges(toStaticAlarmRanges(ai.getStaticAlarmRangeList()));
        return nca;
    }
}
