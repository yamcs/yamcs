package org.yamcs.web.rest.mdb;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.DatatypeConverter;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Mdb;
import org.yamcs.protobuf.Mdb.AbsoluteTimeInfo;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.AlgorithmInfo.Scope;
import org.yamcs.protobuf.Mdb.ArgumentAssignmentInfo;
import org.yamcs.protobuf.Mdb.ArgumentInfo;
import org.yamcs.protobuf.Mdb.ArgumentTypeInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.CommandContainerInfo;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.DataEncodingInfo;
import org.yamcs.protobuf.Mdb.DataEncodingInfo.Type;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.FixedValueInfo;
import org.yamcs.protobuf.Mdb.InputParameterInfo;
import org.yamcs.protobuf.Mdb.JavaExpressionCalibratorInfo;
import org.yamcs.protobuf.Mdb.OutputParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.PolynomialCalibratorInfo;
import org.yamcs.protobuf.Mdb.RepeatInfo;
import org.yamcs.protobuf.Mdb.SequenceEntryInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo.SplinePointInfo;
import org.yamcs.protobuf.Mdb.TransmissionConstraintInfo;
import org.yamcs.protobuf.Mdb.UnitInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.YamcsManagement.HistoryInfo;
import org.yamcs.protobuf.YamcsManagement.SpaceSystemInfo;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryDataType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedDataType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;

public class XtceToGpbAssembler {

    public enum DetailLevel {
        LINK, SUMMARY, FULL
    }

    public static ContainerInfo toContainerInfo(SequenceContainer c, DetailLevel detail) {
        ContainerInfo.Builder cb = ContainerInfo.newBuilder();

        cb.setName(c.getName());
        cb.setQualifiedName(c.getQualifiedName());

        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
            if (c.getShortDescription() != null) {
                cb.setShortDescription(c.getShortDescription());
            }
            if (c.getLongDescription() != null) {
                cb.setLongDescription(c.getLongDescription());
            }
            if (c.getAliasSet() != null) {
                Map<String, String> aliases = c.getAliasSet().getAliases();
                for (Entry<String, String> me : aliases.entrySet()) {
                    cb.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
                }
            }
            if (c.getRateInStream() != null) {
                cb.setMaxInterval(c.getRateInStream().getMaxInterval());
            }
            if (c.getSizeInBits() != -1) {
                cb.setSizeInBits(c.getSizeInBits());
            }
            if (c.getBaseContainer() != null) {
                if (detail == DetailLevel.SUMMARY) {
                    cb.setBaseContainer(toContainerInfo(c.getBaseContainer(), DetailLevel.LINK));
                } else if (detail == DetailLevel.FULL) {
                    cb.setBaseContainer(toContainerInfo(c.getBaseContainer(), DetailLevel.FULL));
                }
            }
            if (c.getRestrictionCriteria() != null) {
                if (c.getRestrictionCriteria() instanceof ComparisonList) {
                    ComparisonList xtceList = (ComparisonList) c.getRestrictionCriteria();
                    for (Comparison comparison : xtceList.getComparisonList()) {
                        cb.addRestrictionCriteria(toComparisonInfo(comparison));
                    }
                } else if (c.getRestrictionCriteria() instanceof Comparison) {
                    cb.addRestrictionCriteria(toComparisonInfo((Comparison) c.getRestrictionCriteria()));
                }
            }
            for (SequenceEntry entry : c.getEntryList()) {
                if (detail == DetailLevel.SUMMARY) {
                    cb.addEntry(toSequenceEntryInfo(entry, DetailLevel.SUMMARY));
                } else if (detail == DetailLevel.FULL) {
                    cb.addEntry(toSequenceEntryInfo(entry, DetailLevel.FULL));
                }
            }
        }

        return cb.build();
    }

    public static SequenceEntryInfo toSequenceEntryInfo(SequenceEntry e, DetailLevel detail) {
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
            if (detail == DetailLevel.SUMMARY) {
                b.setContainer(toContainerInfo(ce.getRefContainer(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setContainer(toContainerInfo(ce.getRefContainer(), DetailLevel.FULL));
            }
        } else if (e instanceof ParameterEntry) {
            ParameterEntry pe = (ParameterEntry) e;
            if (detail == DetailLevel.SUMMARY) {
                b.setParameter(toParameterInfo(pe.getParameter(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setParameter(toParameterInfo(pe.getParameter(), DetailLevel.FULL));
            }
        } else if (e instanceof ArgumentEntry) {
            ArgumentEntry ae = (ArgumentEntry) e;
            b.setArgument(toArgumentInfo(ae.getArgument()));
        } else if (e instanceof FixedValueEntry) {
            FixedValueEntry fe = (FixedValueEntry) e;
            FixedValueInfo.Builder feb = FixedValueInfo.newBuilder();
            if (fe.getName() != null) {
                feb.setName(fe.getName());
            }
            if (fe.getSizeInBits() != -1) {
                feb.setSizeInBits(fe.getSizeInBits());
            }
            feb.setHexValue(StringConverter.arrayToHexString(fe.getBinaryValue()));
            b.setFixedValue(feb.build());
        } else {
            throw new IllegalStateException("Unexpected entry " + e);
        }

        return b.build();
    }

    public static FixedValueInfo toFixedValueInfo(FixedValueEntry entry) {
        FixedValueInfo.Builder b = FixedValueInfo.newBuilder();
        if (entry.getName() != null) {
            b.setName(entry.getName());
        }
        if (entry.getSizeInBits() != -1) {
            b.setSizeInBits(entry.getSizeInBits());
        }
        b.setHexValue(StringConverter.arrayToHexString(entry.getBinaryValue()));
        return b.build();
    }

    public static RepeatInfo toRepeatInfo(Repeat xtceRepeat, DetailLevel detail) {
        RepeatInfo.Builder b = RepeatInfo.newBuilder();
        b.setBitsBetween(xtceRepeat.getOffsetSizeInBits());
        if (xtceRepeat.getCount() instanceof FixedIntegerValue) {
            FixedIntegerValue val = (FixedIntegerValue) xtceRepeat.getCount();
            b.setFixedCount(val.getValue());
        } else if (xtceRepeat.getCount() instanceof DynamicIntegerValue) {
            DynamicIntegerValue val = (DynamicIntegerValue) xtceRepeat.getCount();
            if (detail == DetailLevel.SUMMARY) {
                b.setDynamicCount(
                        toParameterInfo(val.getParameterInstnaceRef().getParameter(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setDynamicCount(
                        toParameterInfo(val.getParameterInstnaceRef().getParameter(), DetailLevel.FULL));
            }
        } else {
            throw new IllegalStateException("Unexpected repeat count " + xtceRepeat.getCount());
        }

        return b.build();
    }

    public static CommandContainerInfo toCommandContainerInfo(CommandContainer container, DetailLevel detail) {
        CommandContainerInfo.Builder ccb = CommandContainerInfo.newBuilder();
        ccb.setName(container.getName());
        if (container.getQualifiedName() != null) {
            ccb.setQualifiedName(container.getQualifiedName());
        }
        if (container.getShortDescription() != null) {
            ccb.setShortDescription(container.getShortDescription());
        }
        if (container.getLongDescription() != null) {
            ccb.setLongDescription(container.getLongDescription());
        }
        if (container.getAliasSet() != null) {
            Map<String, String> aliases = container.getAliasSet().getAliases();
            for (Entry<String, String> me : aliases.entrySet()) {
                ccb.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
            }
        }

        for (SequenceEntry entry : container.getEntryList()) {
            ccb.addEntry(toSequenceEntryInfo(entry, detail));
        }

        return ccb.build();
    }

    /**
     * @param detail
     *            whether base commands should be expanded
     */
    public static CommandInfo toCommandInfo(MetaCommand cmd, DetailLevel detail) {
        CommandInfo.Builder cb = CommandInfo.newBuilder();

        cb.setName(cmd.getName());
        cb.setQualifiedName(cmd.getQualifiedName());

        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
            if (cmd.getShortDescription() != null) {
                cb.setShortDescription(cmd.getShortDescription());
            }
            if (cmd.getLongDescription() != null) {
                cb.setLongDescription(cmd.getLongDescription());
            }
            if (cmd.getAliasSet() != null) {
                Map<String, String> aliases = cmd.getAliasSet().getAliases();
                for (Entry<String, String> me : aliases.entrySet()) {
                    cb.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
                }
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

            if (cmd.getCommandContainer() != null) {
                CommandContainer container = cmd.getCommandContainer();
                cb.setCommandContainer(toCommandContainerInfo(container, DetailLevel.FULL));
            }

            if (detail == DetailLevel.SUMMARY) {
                if (cmd.getBaseMetaCommand() != null) {
                    cb.setBaseCommand(toCommandInfo(cmd.getBaseMetaCommand(), DetailLevel.LINK));
                }
            } else if (detail == DetailLevel.FULL) {
                if (cmd.getBaseMetaCommand() != null) {
                    cb.setBaseCommand(toCommandInfo(cmd.getBaseMetaCommand(), DetailLevel.FULL));
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
            b.setType(toArgumentTypeInfo(xtceType));
            if (!b.hasInitialValue()) {
                String initialValue = null;
                initialValue = getDataTypeInitialValue((BaseDataType) xtceArgument.getArgumentType());
                if (initialValue != null) {
                    b.setInitialValue(initialValue);
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
        b.setParameter(toParameterInfo(xtceComparison.getParameter(), DetailLevel.LINK));
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

    public static ParameterInfo toParameterInfo(Parameter p, DetailLevel detail) {
        ParameterInfo.Builder b = ParameterInfo.newBuilder();

        b.setName(p.getName());
        b.setQualifiedName(p.getQualifiedName());

        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
            if (p.getShortDescription() != null) {
                b.setShortDescription(p.getShortDescription());
            }
            DataSource xtceDs = p.getDataSource();
            if (xtceDs != null) {
                b.setDataSource(DataSourceType.valueOf(xtceDs.name()));
            }
            if (p.getParameterType() != null) {
                b.setType(toParameterTypeInfo(p.getParameterType(), detail));
            }
        }

        if (detail == DetailLevel.FULL) {
            if (p.getLongDescription() != null) {
                b.setLongDescription(p.getLongDescription());
            }
            if (p.getAliasSet() != null) {
                Map<String, String> aliases = p.getAliasSet().getAliases();
                for (Entry<String, String> me : aliases.entrySet()) {
                    b.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
                }
            }
        }

        return b.build();
    }

    public static ParameterTypeInfo toParameterTypeInfo(ParameterType parameterType, DetailLevel detail) {
        ParameterTypeInfo.Builder infob = ParameterTypeInfo.newBuilder();

        infob.setEngType(parameterType.getTypeAsString());

        if (parameterType instanceof BaseDataType) {
            BaseDataType bdt = (BaseDataType) parameterType;
            for (UnitType ut : bdt.getUnitSet()) {
                infob.addUnitSet(toUnitInfo(ut));
            }
        }
        if (detail == DetailLevel.FULL) {
            if (parameterType instanceof BaseDataType) {
                BaseDataType bdt = (BaseDataType) parameterType;
                if (bdt.getEncoding() != null) {
                    infob.setDataEncoding(toDataEncodingInfo(bdt.getEncoding()));
                }
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
            } else if (parameterType instanceof AbsoluteTimeParameterType) {
                AbsoluteTimeParameterType apt = (AbsoluteTimeParameterType) parameterType;
                AbsoluteTimeInfo.Builder timeb = AbsoluteTimeInfo.newBuilder();
                if (apt.getInitialValue() != null) {
                    timeb.setInitialValue(TimeEncoding.toString(apt.getInitialValue()));
                }
                if (apt.needsScaling()) {
                    timeb.setScale(apt.getScale());
                    timeb.setOffset(apt.getOffset());
                }
                ReferenceTime referenceTime = apt.getReferenceTime();
                if (referenceTime != null) {
                    TimeEpoch epoch = referenceTime.getEpoch();
                    if (epoch != null) {
                        if (epoch.getCommonEpoch() != null) {
                            timeb.setEpoch(epoch.getCommonEpoch().toString());
                        } else {
                            timeb.setEpoch(epoch.getDateTime());
                        }
                    }
                    if (referenceTime.getOffsetFrom() != null) {
                        Parameter p = referenceTime.getOffsetFrom().getParameter();
                        timeb.setOffsetFrom(toParameterInfo(p, DetailLevel.LINK));
                    }
                }
                infob.setAbsoluteTimeInfo(timeb);
            }
        }
        return infob.build();
    }

    private static String getDataTypeInitialValue(BaseDataType dataType) {
        if (dataType == null) {
            return null;
        }
        if (dataType instanceof IntegerDataType) {
            IntegerDataType idt = (IntegerDataType) dataType;
            Long l = idt.getInitialValue();
            if (l == null) {
                return null;
            } else {
                if (idt.isSigned()) {
                    return l.toString();
                } else {
                    return Long.toUnsignedString(l);
                }
            }
        } else if (dataType instanceof FloatArgumentType) {
            return ((FloatArgumentType) dataType).getInitialValue() != null
                    ? ((FloatArgumentType) dataType).getInitialValue() + ""
                    : null;
        } else if (dataType instanceof EnumeratedDataType) {
            return ((EnumeratedDataType) dataType).getInitialValue();
        } else if (dataType instanceof StringDataType) {
            return ((StringDataType) dataType).getInitialValue();
        } else if (dataType instanceof BinaryDataType) {
            byte[] initialValue = ((BinaryDataType) dataType).getInitialValue();
            return initialValue != null ? DatatypeConverter.printHexBinary(initialValue) : null;
        } else if (dataType instanceof BooleanDataType) {
            return ((BooleanDataType) dataType).getInitialValue() != null
                    ? ((BooleanDataType) dataType).getInitialValue().toString()
                    : null;
        }
        return null;
    }

    public static ArgumentTypeInfo toArgumentTypeInfo(ArgumentType argumentType) {
        ArgumentTypeInfo.Builder infob = ArgumentTypeInfo.newBuilder();

        if (((BaseDataType) argumentType).getEncoding() != null) {
            infob.setDataEncoding(toDataEncodingInfo(((BaseDataType) argumentType).getEncoding()));
        }

        infob.setEngType(argumentType.getTypeAsString());
        for (UnitType ut : argumentType.getUnitSet()) {
            infob.addUnitSet(toUnitInfo(ut));
        }

        if (argumentType instanceof IntegerArgumentType) {
            IntegerArgumentType iat = (IntegerArgumentType) argumentType;
            if (iat.getValidRange() != null) {
                infob.setRangeMin(iat.getValidRange().getMinInclusive());
                infob.setRangeMax(iat.getValidRange().getMaxInclusive());
            }
        } else if (argumentType instanceof FloatArgumentType) {
            FloatArgumentType fat = (FloatArgumentType) argumentType;
            if (fat.getValidRange() != null) {
                infob.setRangeMin(fat.getValidRange().getMin());
                infob.setRangeMax(fat.getValidRange().getMax());
            }
        } else if (argumentType instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType eat = (EnumeratedArgumentType) argumentType;
            for (ValueEnumeration xtceValue : eat.getValueEnumerationList()) {
                infob.addEnumValue(toEnumValue(xtceValue));
            }
        }
        return infob.build();
    }

    public static List<ComparisonInfo> toComparisons(MatchCriteria matchCriteria) {
        List<ComparisonInfo> comparisons = new ArrayList<>(2);
        if (matchCriteria instanceof Comparison) {
            comparisons.add(toComparisonInfo((Comparison) matchCriteria));
        } else if (matchCriteria instanceof ComparisonList) {
            ComparisonList xtceList = (ComparisonList) matchCriteria;
            for (Comparison xtceComparison : xtceList.getComparisonList()) {
                comparisons.add(toComparisonInfo(xtceComparison));
            }
        } else {
            throw new IllegalStateException("Unexpected match criteria " + matchCriteria);
        }
        return comparisons;
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
                Calibrator calibrator = fde.getDefaultCalibrator();
                infob.setDefaultCalibrator(toCalibratorInfo(calibrator));
            }
            if (fde.getContextCalibratorList() != null) {
                for (ContextCalibrator contextCalibrator : fde.getContextCalibratorList()) {
                    ContextCalibratorInfo.Builder contextCalibratorb = ContextCalibratorInfo.newBuilder();
                    MatchCriteria matchCriteria = contextCalibrator.getContextMatch();
                    contextCalibratorb.addAllComparison(toComparisons(matchCriteria));
                    contextCalibratorb.setCalibrator(toCalibratorInfo(contextCalibrator.getCalibrator()));
                    infob.addContextCalibrator(contextCalibratorb);
                }
            }
        } else if (xtceDataEncoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding ide = (IntegerDataEncoding) xtceDataEncoding;
            if (ide.getEncoding() == IntegerDataEncoding.Encoding.STRING) {
                infob.setType(Type.STRING);
                infob.setEncoding(toTextualEncoding(ide.getStringEncoding()));
            } else {
                infob.setType(Type.INTEGER);
                infob.setEncoding(ide.getEncoding().toString());
            }
            if (ide.getDefaultCalibrator() != null) {
                Calibrator calibrator = ide.getDefaultCalibrator();
                infob.setDefaultCalibrator(toCalibratorInfo(calibrator));
            }
            if (ide.getContextCalibratorList() != null) {
                for (ContextCalibrator contextCalibrator : ide.getContextCalibratorList()) {
                    ContextCalibratorInfo.Builder contextCalibratorb = ContextCalibratorInfo.newBuilder();
                    MatchCriteria matchCriteria = contextCalibrator.getContextMatch();
                    contextCalibratorb.addAllComparison(toComparisons(matchCriteria));
                    contextCalibratorb.setCalibrator(toCalibratorInfo(contextCalibrator.getCalibrator()));
                    infob.addContextCalibrator(contextCalibratorb);
                }
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
        case FIXED:
            result += sde.getSizeInBits();
            break;
        case LEADING_SIZE:
            result += sde.getSizeInBitsOfSizeTag();
            break;
        case TERMINATION_CHAR:
            String hexChar = Integer.toHexString(sde.getTerminationChar()).toUpperCase();
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }
            result += "0x" + hexChar;
            break;
        default:
            throw new IllegalStateException("Unexpected size type " + sde.getSizeType());
        }
        return result + ")";
    }

    public static Mdb.EnumValue toEnumValue(ValueEnumeration xtceValue) {
        Mdb.EnumValue.Builder b = Mdb.EnumValue.newBuilder();
        b.setValue(xtceValue.getValue());
        b.setLabel(xtceValue.getLabel());
        return b.build();
    }

    public static UnitInfo toUnitInfo(UnitType ut) {
        return UnitInfo.newBuilder().setUnit(ut.getUnit()).build();
    }

    public static CalibratorInfo toCalibratorInfo(Calibrator calibrator) {
        CalibratorInfo.Builder calibratorInfob = CalibratorInfo.newBuilder();
        if (calibrator instanceof PolynomialCalibrator) {
            calibratorInfob.setType("Polynomial");
            PolynomialCalibrator polynomialCalibrator = (PolynomialCalibrator) calibrator;
            PolynomialCalibratorInfo.Builder polyb = PolynomialCalibratorInfo.newBuilder();
            for (double coefficient : polynomialCalibrator.getCoefficients()) {
                polyb.addCoefficient(coefficient);
            }
            calibratorInfob.setPolynomialCalibrator(polyb);
        } else if (calibrator instanceof SplineCalibrator) {
            calibratorInfob.setType("Spline");
            SplineCalibrator splineCalibrator = (SplineCalibrator) calibrator;
            SplineCalibratorInfo.Builder splineb = SplineCalibratorInfo.newBuilder();
            for (SplinePoint point : splineCalibrator.getPoints()) {
                splineb.addPoint(SplinePointInfo.newBuilder()
                        .setRaw(point.getRaw())
                        .setCalibrated(point.getCalibrated()));
            }
            calibratorInfob.setSplineCalibrator(splineb);
        } else if (calibrator instanceof JavaExpressionCalibrator) {
            calibratorInfob.setType("Java Expression");
            JavaExpressionCalibrator javaCalibrator = (JavaExpressionCalibrator) calibrator;
            JavaExpressionCalibratorInfo.Builder javab = JavaExpressionCalibratorInfo.newBuilder();
            javab.setFormula(javaCalibrator.getFormula());
            calibratorInfob.setJavaExpressionCalibrator(javab);
        } else if (calibrator instanceof MathOperationCalibrator) {
            calibratorInfob.setType("Math Operation");
            // MathOperationCalibrator mathOperationCalibrator = (MathOperationCalibrator) calibrator;
        } else {
            throw new IllegalArgumentException("Unexpected calibrator type " + calibrator.getClass());
        }
        return calibratorInfob.build();
    }

    public static AlarmInfo toAlarmInfo(NumericAlarm numericAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(numericAlarm.getMinViolations());
        AlarmRanges staticRanges = numericAlarm.getStaticAlarmRanges();
        if (staticRanges.getWatchRange() != null) {
            AlarmRange watchRange = ParameterValue.toGpbAlarmRange(AlarmLevelType.WATCH, staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRange(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = ParameterValue.toGpbAlarmRange(AlarmLevelType.WARNING,
                    staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRange(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = ParameterValue.toGpbAlarmRange(AlarmLevelType.DISTRESS,
                    staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRange(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = ParameterValue.toGpbAlarmRange(AlarmLevelType.CRITICAL,
                    staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRange(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = ParameterValue.toGpbAlarmRange(AlarmLevelType.SEVERE,
                    staticRanges.getSevereRange());
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

    public static Mdb.EnumerationAlarm toEnumerationAlarm(EnumerationAlarmItem xtceAlarmItem) {
        Mdb.EnumerationAlarm.Builder resultb = Mdb.EnumerationAlarm.newBuilder();
        resultb.setLabel(xtceAlarmItem.getEnumerationLabel());
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

    public static AlgorithmInfo toAlgorithmInfo(Algorithm a, DetailLevel detail) {
        AlgorithmInfo.Builder b = AlgorithmInfo.newBuilder();

        b.setName(a.getName());
        b.setQualifiedName(a.getQualifiedName());

        if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
            if (a.getShortDescription() != null) {
                b.setShortDescription(a.getShortDescription());
            }
            if (a.getLongDescription() != null) {
                b.setLongDescription(a.getLongDescription());
            }
            if (a.getAliasSet() != null) {
                Map<String, String> aliases = a.getAliasSet().getAliases();
                for (Entry<String, String> me : aliases.entrySet()) {
                    b.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
                }
            }
            switch (a.getScope()) {
            case GLOBAL:
                b.setScope(Scope.GLOBAL);
                break;
            case COMMAND_VERIFICATION:
                b.setScope(Scope.COMMAND_VERIFICATION);
                break;
            default:
                throw new IllegalStateException("Unexpected scope " + a.getScope());
            }

            if (a instanceof CustomAlgorithm) {
                CustomAlgorithm ca = (CustomAlgorithm) a;
                if (ca.getLanguage() != null) {
                    b.setLanguage(ca.getLanguage());
                }
                if (ca.getAlgorithmText() != null) {
                    b.setText(ca.getAlgorithmText());
                }
            }
        }

        if (detail == DetailLevel.FULL) {
            for (InputParameter p : a.getInputSet()) {
                b.addInputParameter(toInputParameterInfo(p));
            }
            for (OutputParameter p : a.getOutputSet()) {
                b.addOutputParameter(toOutputParameterInfo(p));
            }
            TriggerSetType triggerSet = a.getTriggerSet();
            if (triggerSet != null) {
                for (OnParameterUpdateTrigger trig : triggerSet.getOnParameterUpdateTriggers()) {
                    b.addOnParameterUpdate(toParameterInfo(trig.getParameter(), DetailLevel.SUMMARY));
                }
                for (OnPeriodicRateTrigger trig : triggerSet.getOnPeriodicRateTriggers()) {
                    b.addOnPeriodicRate(trig.getFireRate());
                }
            }
        }

        return b.build();
    }

    public static InputParameterInfo toInputParameterInfo(InputParameter xtceInput) {
        InputParameterInfo.Builder resultb = InputParameterInfo.newBuilder();
        resultb.setParameter(
                toParameterInfo(xtceInput.getParameterInstance().getParameter(), DetailLevel.SUMMARY));
        if (xtceInput.getInputName() != null) {
            resultb.setInputName(xtceInput.getInputName());
        }
        resultb.setParameterInstance(xtceInput.getParameterInstance().getInstance());
        resultb.setMandatory(xtceInput.isMandatory());
        return resultb.build();
    }

    public static OutputParameterInfo toOutputParameterInfo(OutputParameter xtceOutput) {
        OutputParameterInfo.Builder resultb = OutputParameterInfo.newBuilder();
        resultb.setParameter(toParameterInfo(xtceOutput.getParameter(), DetailLevel.SUMMARY));
        if (xtceOutput.getOutputName() != null) {
            resultb.setOutputName(xtceOutput.getOutputName());
        }
        return resultb.build();
    }

    public static SpaceSystemInfo toSpaceSystemInfo(RestRequest req, SpaceSystem ss) {
        SpaceSystemInfo.Builder b = SpaceSystemInfo.newBuilder();
        b.setName(ss.getName());
        b.setQualifiedName(ss.getQualifiedName());
        if (ss.getShortDescription() != null) {
            b.setShortDescription(ss.getShortDescription());
        }
        if (ss.getLongDescription() != null) {
            b.setLongDescription(ss.getLongDescription());
        }
        Header h = ss.getHeader();
        if (h != null) {
            if (h.getVersion() != null) {
                b.setVersion(h.getVersion());
            }

            History[] sortedHistory = h.getHistoryList().toArray(new History[] {});
            Arrays.sort(sortedHistory);
            for (History history : sortedHistory) {
                HistoryInfo.Builder historyb = HistoryInfo.newBuilder();
                if (history.getVersion() != null) {
                    historyb.setVersion(history.getVersion());
                }
                if (history.getDate() != null) {
                    historyb.setDate(history.getDate());
                }
                if (history.getMessage() != null) {
                    historyb.setMessage(history.getMessage());
                }
                if (history.getAuthor() != null) {
                    historyb.setAuthor(history.getAuthor());
                }
                b.addHistory(historyb);
            }
        }
        boolean aggregate = req.getQueryParameterAsBoolean("aggregate", false);
        b.setParameterCount(ss.getParameterCount(aggregate));
        b.setContainerCount(ss.getSequenceContainerCount(aggregate));
        b.setCommandCount(ss.getMetaCommandCount(aggregate));
        b.setAlgorithmCount(ss.getAlgorithmCount(aggregate));

        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSub(toSpaceSystemInfo(req, sub));
        }
        return b.build();
    }
}
