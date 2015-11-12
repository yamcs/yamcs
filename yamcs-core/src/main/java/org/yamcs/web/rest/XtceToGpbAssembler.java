package org.yamcs.web.rest;

import java.nio.ByteOrder;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.protobuf.Mdb;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.ArgumentAssignmentInfo;
import org.yamcs.protobuf.Mdb.ArgumentInfo;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.DataEncodingInfo;
import org.yamcs.protobuf.Mdb.DataEncodingInfo.Type;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.RepeatInfo;
import org.yamcs.protobuf.Mdb.SequenceEntryInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.Mdb.TransmissionConstraintInfo;
import org.yamcs.protobuf.Mdb.UnitInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;

public class XtceToGpbAssembler {
    
    public enum DetailLevel {
        LINK,
        SUMMARY,
        FULL
    }
    
    public static ContainerInfo toContainerInfo(SequenceContainer c, String instanceURL, DetailLevel detail) {
        ContainerInfo.Builder cb = ContainerInfo.newBuilder();
        
        cb.setName(c.getName());
        cb.setQualifiedName(c.getQualifiedName());
        cb.setUrl(instanceURL + "/containers" + c.getQualifiedName());
        
        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
            if (c.getShortDescription() != null) {
                cb.setShortDescription(c.getShortDescription());
            }
            if (c.getLongDescription() != null) {
                cb.setLongDescription(c.getLongDescription());
            }
            Map<String, String> aliases = c.getAliasSet().getAliases();
            for(Entry<String, String> me : aliases.entrySet()) {
                cb.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
            }
            if (c.getRateInStream() != null) {
                cb.setMaxInterval(c.getRateInStream().getMaxInterval());
            }
            if (c.getSizeInBits() != -1) {
                cb.setSizeInBits(c.getSizeInBits());
            }
            if (c.getBaseContainer() != null) {
                if (detail == DetailLevel.SUMMARY) {
                    cb.setBaseContainer(toContainerInfo(c.getBaseContainer(), instanceURL, DetailLevel.LINK));
                } else if (detail == DetailLevel.FULL) {
                    cb.setBaseContainer(toContainerInfo(c.getBaseContainer(), instanceURL, DetailLevel.FULL));
                }
            }
            if (c.getRestrictionCriteria() != null) {
                if (c.getRestrictionCriteria() instanceof ComparisonList) {
                    ComparisonList xtceList = (ComparisonList) c.getRestrictionCriteria();
                    for (Comparison comparison : xtceList.getComparisonList()) {
                        cb.addRestrictionCriteria(toComparisonInfo(comparison, instanceURL));
                    }
                } else if (c.getRestrictionCriteria() instanceof Comparison) {
                    cb.addRestrictionCriteria(toComparisonInfo((Comparison) c.getRestrictionCriteria(), instanceURL));
                }
            }
            for (SequenceEntry entry : c.getEntryList()) {
                if (detail == DetailLevel.SUMMARY) {
                    cb.addEntry(toSequenceEntryInfo(entry, instanceURL, DetailLevel.LINK));
                } else if (detail == DetailLevel.FULL) {
                    cb.addEntry(toSequenceEntryInfo(entry, instanceURL, DetailLevel.FULL));
                }
            }
        }
        
        return cb.build();
    }
    
    public static SequenceEntryInfo toSequenceEntryInfo(SequenceEntry e, String instanceURL, DetailLevel detail) {
        SequenceEntryInfo.Builder b = SequenceEntryInfo.newBuilder();
        b.setLocationInBits(e.getLocationInContainerInBits());
        
        switch (e.getReferenceLocation()) {
        case containerStart:
            b.setReferenceLocation(SequenceEntryInfo.ReferenceLocationType.CONTAINER_START);
            break;
        case previousEntry:
            b.setReferenceLocation(SequenceEntryInfo.ReferenceLocationType.PREVIOUS_ENTRY);
            break;
        default:
            throw new IllegalStateException("Unexpected reference location " + e);
        }
        
        if (e instanceof ContainerEntry) {
            ContainerEntry ce = (ContainerEntry) e;
            if (detail == DetailLevel.LINK || detail == DetailLevel.SUMMARY) {
                b.setContainer(toContainerInfo(ce.getSequenceContainer(), instanceURL, DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setContainer(toContainerInfo(ce.getSequenceContainer(), instanceURL, DetailLevel.FULL));
            }
        } else if (e instanceof ParameterEntry) {
            ParameterEntry pe = (ParameterEntry) e;
            if (detail == DetailLevel.LINK || detail == DetailLevel.SUMMARY) {
                b.setParameter(toParameterInfo(pe.getParameter(), instanceURL, DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setParameter(toParameterInfo(pe.getParameter(), instanceURL, DetailLevel.FULL));
            }
        } else {
            throw new IllegalStateException("Unexpected entry " + e);
        }
        
        return b.build();
    }
    
    public static RepeatInfo toRepeatInfo(Repeat xtceRepeat, String instanceURL, DetailLevel detail) {
        RepeatInfo.Builder b = RepeatInfo.newBuilder();
        b.setBitsBetween(xtceRepeat.getOffsetSizeInBits());
        if (xtceRepeat.getCount() instanceof FixedIntegerValue) {
            FixedIntegerValue val = (FixedIntegerValue) xtceRepeat.getCount();
            b.setFixedCount(val.getValue());
        } else if (xtceRepeat.getCount() instanceof DynamicIntegerValue) {
            DynamicIntegerValue val = (DynamicIntegerValue) xtceRepeat.getCount();
            if (detail == DetailLevel.SUMMARY) {
                b.setDynamicCount(toParameterInfo(val.getParameter(), instanceURL, DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setDynamicCount(toParameterInfo(val.getParameter(), instanceURL, DetailLevel.FULL));
            }
        } else {
            throw new IllegalStateException("Unexpected repeat count " + xtceRepeat.getCount());
        }
        
        return b.build();
    }
    
    /**
     * @param detail whether base commands should be expanded
     */
    public static CommandInfo toCommandInfo(MetaCommand cmd, String instanceURL, DetailLevel detail) {
        CommandInfo.Builder cb = CommandInfo.newBuilder();
        
        cb.setName(cmd.getName());
        cb.setQualifiedName(cmd.getQualifiedName());
        cb.setUrl(instanceURL + "/commands" + cmd.getQualifiedName());
        
        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
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
                    cb.addConstraint(toTransmissionConstraintInfo(xtceConstraint, instanceURL));
                }
            }
            
            if (detail == DetailLevel.SUMMARY) {
                if (cmd.getBaseMetaCommand() != null) {
                    cb.setBaseCommand(toCommandInfo(cmd.getBaseMetaCommand(), instanceURL, DetailLevel.LINK));
                }
            } else if (detail == DetailLevel.FULL) {
                if (cmd.getBaseMetaCommand() != null) {
                    cb.setBaseCommand(toCommandInfo(cmd.getBaseMetaCommand(), instanceURL, DetailLevel.FULL));
                }
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
    
    public static TransmissionConstraintInfo toTransmissionConstraintInfo(TransmissionConstraint xtceConstraint, String instanceURL) {
        TransmissionConstraintInfo.Builder b = TransmissionConstraintInfo.newBuilder();
        if (xtceConstraint.getMatchCriteria() instanceof Comparison) {
            b.addComparison(toComparisonInfo((Comparison) xtceConstraint.getMatchCriteria(), instanceURL));
        } else if (xtceConstraint.getMatchCriteria() instanceof ComparisonList) {
            ComparisonList xtceList = (ComparisonList) xtceConstraint.getMatchCriteria();
            for (Comparison xtceComparison : xtceList.getComparisonList()) {
                b.addComparison(toComparisonInfo(xtceComparison, instanceURL));
            }
        } else {
            throw new IllegalStateException("Unexpected match criteria " + xtceConstraint.getMatchCriteria());
        }
        b.setTimeout(xtceConstraint.getTimeout());
        return b.build();
    }
    
    public static ComparisonInfo toComparisonInfo(Comparison xtceComparison, String instanceURL) {
        ComparisonInfo.Builder b = ComparisonInfo.newBuilder();
        b.setParameter(toParameterInfo(xtceComparison.getParameter(), instanceURL, DetailLevel.LINK));
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
    
    public static ParameterInfo toParameterInfo(Parameter p, String mdbURL, DetailLevel detail) {
        ParameterInfo.Builder b = ParameterInfo.newBuilder();
        
        b.setName(p.getName());
        b.setQualifiedName(p.getQualifiedName());
        b.setUrl(mdbURL + "/parameters" + p.getQualifiedName());
        
        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
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
                switch (xtceDs) {
                case TELEMETERED:
                    b.setDataSource(DataSourceType.TELEMETERED);
                    break;
                case LOCAL:
                    b.setDataSource(DataSourceType.LOCAL);
                    break;
                case COMMAND:
                    b.setDataSource(DataSourceType.COMMAND);
                    break;
                case COMMAND_HISTORY:
                    b.setDataSource(DataSourceType.COMMAND_HISTORY);
                    break;
                case CONSTANT:
                    b.setDataSource(DataSourceType.CONSTANT);
                    break;
                case DERIVED:
                    b.setDataSource(DataSourceType.DERIVED);
                    break;
                case SYSTEM:
                    b.setDataSource(DataSourceType.SYSTEM);
                    break;
                default:
                    throw new IllegalStateException("Unexpected data source " + xtceDs);
                }
            }
            if(p.getParameterType() != null)
                b.setType(toParameterTypeInfo(p.getParameterType()));
        }
        
        return b.build();
    }

    public static ParameterTypeInfo toParameterTypeInfo(ParameterType parameterType) {
        ParameterTypeInfo.Builder infob = ParameterTypeInfo.newBuilder();
        if (parameterType.getEncoding() != null) {
            infob.setDataEncoding(toDataEncodingInfo(parameterType.getEncoding()));
        }
        infob.setEngType(parameterType.getTypeAsString());
        for (UnitType ut: parameterType.getUnitSet()) {
            infob.addUnitSet(toUnitInfo(ut));
        }
        
        if (parameterType instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) parameterType;
            if (ipt.getDefaultAlarm() != null) {
                infob.setDefaultAlarm(toAlarmInfo(ipt.getDefaultAlarm()));
            }
        } else if (parameterType instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) parameterType;
            if (fpt.getDefaultAlarm() != null) {
                infob.setDefaultAlarm(toAlarmInfo(fpt.getDefaultAlarm()));
            }
        } else if (parameterType instanceof EnumeratedParameterType) {
            EnumeratedParameterType ept = (EnumeratedParameterType) parameterType;
            if (ept.getDefaultAlarm() != null) {
                infob.setDefaultAlarm(toAlarmInfo(ept.getDefaultAlarm()));
            }
            for (ValueEnumeration xtceValue : ept.getValueEnumerationList()) {
                infob.addEnumValue(toEnumValue(xtceValue));
            }
        }
        return infob.build();
    }
    
    // Simplifies the XTCE structure a bit for outside use.
    // String-encoded numeric types see some sort of two-step conversion from raw to eng
    // with the first to interpret the string (stored in a nested StringDataEncoding)
    // and the second to apply any regular integer calibrations (stored in the actual DataEncoding)
    // Below code will represent all of those things as type 'STRING' as the user should expect it.
    public static DataEncodingInfo toDataEncodingInfo(DataEncoding xtceDataEncoding) {
        DataEncodingInfo.Builder infob = DataEncodingInfo.newBuilder();
        infob.setLittleEndian(xtceDataEncoding.getByteOrder() == ByteOrder.LITTLE_ENDIAN);
        if (xtceDataEncoding.getSizeInBits() >= 0) {
            infob.setSizeInBits(xtceDataEncoding.getSizeInBits());
        }
        if (xtceDataEncoding instanceof BinaryDataEncoding) {
            infob.setType(Type.BINARY);
        } else if (xtceDataEncoding instanceof BooleanDataEncoding) {
            infob.setType(Type.BOOLEAN);
        } else if (xtceDataEncoding instanceof FloatDataEncoding) {
            FloatDataEncoding fde = (FloatDataEncoding) xtceDataEncoding;
            if (fde.getEncoding() == FloatDataEncoding.Encoding.STRING) {
                infob.setType(Type.STRING);
                infob.setEncoding(toTextualEncoding(fde.getStringDataEncoding()));
            } else {
                infob.setType(Type.FLOAT);
                infob.setEncoding(fde.getEncoding().toString());
            }
            if (fde.getDefaultCalibrator() != null) {
                infob.setDefaultCalibrator(fde.toString());
            }
        } else if (xtceDataEncoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding ide = (IntegerDataEncoding) xtceDataEncoding;
            if (ide.getEncoding() == IntegerDataEncoding.Encoding.string) {
                infob.setType(Type.STRING);
                infob.setEncoding(toTextualEncoding(ide.getStringEncoding()));
            } else {
                infob.setType(Type.INTEGER);
                infob.setEncoding(ide.getEncoding().toString());
            }
            if (ide.getDefaultCalibrator() != null) {
                infob.setDefaultCalibrator(ide.toString());
            }
        } else if (xtceDataEncoding instanceof StringDataEncoding) {
            infob.setType(Type.STRING);
            StringDataEncoding sde = (StringDataEncoding) xtceDataEncoding;
            infob.setEncoding(toTextualEncoding(sde));
        }
        return infob.build();
    }
    
    public static String toTextualEncoding(StringDataEncoding sde) {
        String result = sde.getSizeType() + "(";
        switch (sde.getSizeType()) {
        case Fixed:
            result += sde.getSizeInBits();
            break;
        case LeadingSize:
            result += sde.getSizeInBitsOfSizeTag();
            break;
        case TerminationChar:
            String hexChar = Integer.toHexString(sde.getTerminationChar()).toUpperCase();
            if (hexChar.length() == 1) hexChar = "0" + hexChar;
            result += "0x" + hexChar;
            break;
        default:
            throw new IllegalStateException("Unexpected size type " + sde.getSizeType());
        }
        return result + ")";
    }
    
    public static ParameterTypeInfo.EnumValue toEnumValue(ValueEnumeration xtceValue) {
        ParameterTypeInfo.EnumValue.Builder b = ParameterTypeInfo.EnumValue.newBuilder();
        b.setValue(xtceValue.getValue());
        b.setLabel(xtceValue.getLabel());
        return b.build();
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
            alarmInfob.addStaticAlarmRange(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = toAlarmRange(AlarmLevelType.WARNING, staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRange(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = toAlarmRange(AlarmLevelType.DISTRESS, staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRange(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = toAlarmRange(AlarmLevelType.CRITICAL, staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRange(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = toAlarmRange(AlarmLevelType.SEVERE, staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRange(severeRange);
        }
            
        return alarmInfob.build();
    }
    
    public static AlarmInfo toAlarmInfo(EnumerationAlarm enumerationAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(enumerationAlarm.getMinViolations());
        for (EnumerationAlarmItem item : enumerationAlarm.getAlarmList()) {
            alarmInfob.addEnumerationAlarm(toEnumerationAlarm(item));
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

    public static Mdb.EnumerationAlarm toEnumerationAlarm(EnumerationAlarmItem xtceAlarmItem) {
        Mdb.EnumerationAlarm.Builder resultb = Mdb.EnumerationAlarm.newBuilder();
        resultb.setValue(xtceAlarmItem.getEnumerationValue().getValue());
        resultb.setLabel(xtceAlarmItem.getEnumerationValue().getLabel());
        switch (xtceAlarmItem.getAlarmLevel()) {
        case normal:
            resultb.setLevel(AlarmLevelType.NORMAL);
            break;
        case watch:
            resultb.setLevel(AlarmLevelType.WATCH);
            break;
        case warning:
            resultb.setLevel(AlarmLevelType.WARNING);
            break;
        case distress:
            resultb.setLevel(AlarmLevelType.DISTRESS);
            break;
        case critical:
            resultb.setLevel(AlarmLevelType.CRITICAL);
            break;
        case severe:
            resultb.setLevel(AlarmLevelType.SEVERE);
            break;
        default:
            throw new IllegalStateException("Unexpected alarm level " + xtceAlarmItem.getAlarmLevel());
        }
        return resultb.build();
    }
}
