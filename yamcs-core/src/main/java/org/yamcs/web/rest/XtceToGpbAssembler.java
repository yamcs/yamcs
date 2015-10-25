package org.yamcs.web.rest;

import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.ArgumentAssignmentInfo;
import org.yamcs.protobuf.Mdb.ArgumentInfo;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.Mdb.TransmissionConstraintInfo;
import org.yamcs.protobuf.Mdb.UnitInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.Comparison.OperatorType;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.UnitType;

public class XtceToGpbAssembler {
    
    /**
     * @param detail whether base commands should be expanded
     */
    public static CommandInfo toCommandInfo(MetaCommand cmd, String instanceURL, boolean detail) {
        CommandInfo.Builder cb = CommandInfo.newBuilder();
        
        cb.setQualifiedName(cmd.getQualifiedName());
        cb.setUrl(instanceURL + "/commands" + cmd.getQualifiedName());
        
        if (detail) {
            if (cmd.getShortDescription() != null) {
                cb.setShortDescription(cmd.getShortDescription());
            }
            if (cmd.getLongDescription() != null) {
                cb.setLongDescription(cmd.getLongDescription());
            }
            Map<String, String> aliases = cmd.getAliasSet().getAliases();
            for(Entry<String, String> me : aliases.entrySet()) {
                cb.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
            }
            
            if (cmd.getDefaultSignificance() != null) {
                cb.setSignificance(toSignificanceInfo(cmd.getDefaultSignificance()));
            }
            if (cmd.getArgumentList() != null) {
                for (Argument xtceArgument : cmd.getArgumentList()) {
                    cb.addArgument(toArgumentInfo(xtceArgument));
                }
            }
            if (cmd.getArgumentAssignmentList() != null) {
                for (ArgumentAssignment xtceAssignment : cmd.getArgumentAssignmentList()) {
                    cb.addArgumentAssignment(toArgumentAssignmentInfo(xtceAssignment));
                }
            }
            cb.setAbstract(cmd.isAbstract());
            if (cmd.getTransmissionConstraintList() != null) {
                for (TransmissionConstraint xtceConstraint : cmd.getTransmissionConstraintList()) {
                    cb.addConstraint(toTransmissionConstraintInfo(xtceConstraint));
                }
            }
            
            if (cmd.getBaseMetaCommand() != null) {
                cb.setBaseCommand(toCommandInfo(cmd.getBaseMetaCommand(), instanceURL, detail));
            }   
        }
    
        return cb.build();
    }
    
    public static ArgumentInfo toArgumentInfo(Argument xtceArgument) {
        ArgumentInfo.Builder b = ArgumentInfo.newBuilder();
        b.setName(xtceArgument.getName());
        if (xtceArgument.getShortDescription() != null) {
            b.setDescription(xtceArgument.getShortDescription());
        }
        if (xtceArgument.getInitialValue() != null) {
            b.setInitialValue(xtceArgument.getInitialValue());
        }
        if (xtceArgument.getArgumentType() != null) {
            ArgumentType xtceType = xtceArgument.getArgumentType();
            b.setType(xtceType.getTypeAsString());
            if (xtceType.getUnitSet() != null) {
                for (UnitType xtceUnit : xtceType.getUnitSet()) {
                    b.addUnitSet(toUnitInfo(xtceUnit));        
                }
            }
        }
        return b.build();
    }
    
    public static ArgumentAssignmentInfo toArgumentAssignmentInfo(ArgumentAssignment xtceArgument) {
        ArgumentAssignmentInfo.Builder b = ArgumentAssignmentInfo.newBuilder();
        b.setName(xtceArgument.getArgumentName());
        b.setValue(xtceArgument.getArgumentValue());
        return b.build();
    }
    
    public static TransmissionConstraintInfo toTransmissionConstraintInfo(TransmissionConstraint xtceConstraint) {
        TransmissionConstraintInfo.Builder b = TransmissionConstraintInfo.newBuilder();
        if (xtceConstraint.getMatchCriteria() instanceof Comparison) {
            b.addComparison(toComparisonInfo((Comparison) xtceConstraint.getMatchCriteria()));
        } else if (xtceConstraint.getMatchCriteria() instanceof ComparisonList) {
            ComparisonList xtceList = (ComparisonList) xtceConstraint.getMatchCriteria();
            for (Comparison xtceComparison : xtceList.getComparisonList()) {
                b.addComparison(toComparisonInfo(xtceComparison));
            }
        } else {
            throw new IllegalStateException("Unexpected match criteria " + xtceConstraint.getMatchCriteria());
        }
        b.setTimeout(xtceConstraint.getTimeout());
        return b.build();
    }
    
    public static ComparisonInfo toComparisonInfo(Comparison xtceComparison) {
        ComparisonInfo.Builder b = ComparisonInfo.newBuilder();
        b.setParameter(xtceComparison.getParameter().getQualifiedName());
        b.setOperator(toOperatorType(xtceComparison.getComparisonOperator()));
        b.setValue(xtceComparison.getStringValue());
        return b.build();
    }
    
    public static ComparisonInfo.OperatorType toOperatorType(OperatorType xtceOperator) {
        switch (xtceOperator) {
        case EQUALITY:
            return ComparisonInfo.OperatorType.EQUAL_TO;
        case INEQUALITY:
            return ComparisonInfo.OperatorType.NOT_EQUAL_TO;
        case LARGEROREQUALTHAN:
            return ComparisonInfo.OperatorType.GREATER_THAN_OR_EQUAL_TO;
        case LARGERTHAN:
            return ComparisonInfo.OperatorType.GREATER_THAN;
        case SMALLEROREQUALTHAN:
            return ComparisonInfo.OperatorType.SMALLER_THAN_OR_EQUAL_TO;
        case SMALLERTHAN:
            return ComparisonInfo.OperatorType.SMALLER_THAN;
        default:
            throw new IllegalStateException("Unexpected operator " + xtceOperator);
        }
    }
    
    public static SignificanceInfo toSignificanceInfo(Significance xtceSignificance) {
        SignificanceInfo.Builder b = SignificanceInfo.newBuilder();
        switch (xtceSignificance.getConsequenceLevel()) {
        case none:
            b.setConsequenceLevel(SignificanceLevelType.NONE);
            break;
        case watch:
            b.setConsequenceLevel(SignificanceLevelType.WATCH);
            break;
        case warning:
            b.setConsequenceLevel(SignificanceLevelType.WARNING);
            break;
        case distress:
            b.setConsequenceLevel(SignificanceLevelType.DISTRESS);
            break;
        case critical:
            b.setConsequenceLevel(SignificanceLevelType.CRITICAL);
            break;
        case severe:
            b.setConsequenceLevel(SignificanceLevelType.SEVERE);
            break;
        default:
            throw new IllegalStateException("Unexpected level " + xtceSignificance.getConsequenceLevel());
        }
        if (xtceSignificance.getReasonForWarning() != null) {
            b.setReasonForWarning(xtceSignificance.getReasonForWarning());
        }
        
        return b.build();
    }
    
    public static ParameterInfo toParameterInfo(Parameter p, String instanceURL) {
        ParameterInfo.Builder b = ParameterInfo.newBuilder();
        
        b.setQualifiedName(p.getQualifiedName());
        b.setUrl(instanceURL + "/parameters" + p.getQualifiedName());
        
        if (p.getShortDescription() != null) {
            b.setShortDescription(p.getShortDescription());
        }
        if (p.getLongDescription() != null) {
            b.setLongDescription(p.getLongDescription());
        }
        Map<String, String> aliases = p.getAliasSet().getAliases();
        for(Entry<String, String> me : aliases.entrySet()) {
            b.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
        }
        DataSource xtceDs = p.getDataSource();
        if (xtceDs != null) {
            DataSourceType ds = DataSourceType.valueOf(xtceDs.name()); // I know, i know
            b.setDataSource(ds);
        }/* else { // TODO why do we need this here. For what reason was this introduced?
            log.warn("Datasource for parameter " + id.getName() + " is null, setting TELEMETERED by default");
            rpib.setDataSource(DataSourceType.TELEMETERED);
        }*/
        
        b.setType(toParameterTypeInfo(p.getParameterType()));
        
        return b.build();
    }

    public static ParameterTypeInfo toParameterTypeInfo(ParameterType parameterType) {
        ParameterTypeInfo.Builder rptb = ParameterTypeInfo.newBuilder();
        if (parameterType.getEncoding() != null) {
            rptb.setDataEncoding(parameterType.getEncoding().toString());
        }
        rptb.setEngType(parameterType.getTypeAsString());
        for (UnitType ut: parameterType.getUnitSet()) {
            rptb.addUnitSet(toUnitInfo(ut));
        }
        
        if (parameterType instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) parameterType;
            if (ipt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(toAlarmInfo(ipt.getDefaultAlarm()));
            }
        } else if (parameterType instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) parameterType;
            if (fpt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(toAlarmInfo(fpt.getDefaultAlarm()));
            }
        }
        return rptb.build();
    }

    public static UnitInfo toUnitInfo(UnitType ut) {
        return UnitInfo.newBuilder().setUnit(ut.getUnit()).build();
    }

    public static AlarmInfo toAlarmInfo(NumericAlarm numericAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(numericAlarm.getMinViolations());
        AlarmRanges staticRanges = numericAlarm.getStaticAlarmRanges();
        if (staticRanges.getWatchRange() != null) {
            AlarmRange watchRange = toAlarmRange(AlarmLevelType.WATCH, staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRanges(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = toAlarmRange(AlarmLevelType.WARNING, staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRanges(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = toAlarmRange(AlarmLevelType.DISTRESS, staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRanges(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = toAlarmRange(AlarmLevelType.CRITICAL, staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRanges(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = toAlarmRange(AlarmLevelType.SEVERE, staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRanges(severeRange);
        }
            
        return alarmInfob.build();
    }
    
    public static AlarmRange toAlarmRange(AlarmLevelType level, FloatRange alarmRange) {
        AlarmRange.Builder resultb = AlarmRange.newBuilder();
        resultb.setLevel(level);
        if (Double.isFinite(alarmRange.getMinInclusive()))
            resultb.setMinInclusive(alarmRange.getMinInclusive());
        if (Double.isFinite(alarmRange.getMaxInclusive()))
            resultb.setMaxInclusive(alarmRange.getMaxInclusive());
        return resultb.build();
    }
}
