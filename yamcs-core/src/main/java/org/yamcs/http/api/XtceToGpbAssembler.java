package org.yamcs.http.api;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yamcs.logging.Log;
import org.yamcs.mdb.MatchCriteriaEvaluator;
import org.yamcs.mdb.MatchCriteriaEvaluatorFactory;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.Mdb;
import org.yamcs.protobuf.Mdb.AbsoluteTimeInfo;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.AlarmLevelType;
import org.yamcs.protobuf.Mdb.AlarmRange;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.AlgorithmInfo.Scope;
import org.yamcs.protobuf.Mdb.AncillaryDataInfo;
import org.yamcs.protobuf.Mdb.ArgumentAssignmentInfo;
import org.yamcs.protobuf.Mdb.ArgumentDimensionInfo;
import org.yamcs.protobuf.Mdb.ArgumentInfo;
import org.yamcs.protobuf.Mdb.ArgumentMemberInfo;
import org.yamcs.protobuf.Mdb.ArgumentTypeInfo;
import org.yamcs.protobuf.Mdb.ArrayInfo;
import org.yamcs.protobuf.Mdb.CalibratorInfo;
import org.yamcs.protobuf.Mdb.CheckWindowInfo;
import org.yamcs.protobuf.Mdb.CommandContainerInfo;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ComparisonInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.ContextCalibratorInfo;
import org.yamcs.protobuf.Mdb.DataEncodingInfo;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.FixedValueInfo;
import org.yamcs.protobuf.Mdb.HistoryInfo;
import org.yamcs.protobuf.Mdb.IndirectParameterRefInfo;
import org.yamcs.protobuf.Mdb.InputParameterInfo;
import org.yamcs.protobuf.Mdb.JavaExpressionCalibratorInfo;
import org.yamcs.protobuf.Mdb.MathElement;
import org.yamcs.protobuf.Mdb.MemberInfo;
import org.yamcs.protobuf.Mdb.NumberFormatTypeInfo;
import org.yamcs.protobuf.Mdb.OutputParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterDimensionInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.PolynomialCalibratorInfo;
import org.yamcs.protobuf.Mdb.RepeatInfo;
import org.yamcs.protobuf.Mdb.SequenceEntryInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.Mdb.SpaceSystemInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo;
import org.yamcs.protobuf.Mdb.SplineCalibratorInfo.SplinePointInfo;
import org.yamcs.protobuf.Mdb.TransmissionConstraintInfo;
import org.yamcs.protobuf.Mdb.UnitInfo;
import org.yamcs.protobuf.Mdb.VerifierInfo;
import org.yamcs.protobuf.Mdb.VerifierInfo.TerminationActionType;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.AncillaryData;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayArgumentType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.History;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NumberFormatType;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterOrArgumentRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;

public class XtceToGpbAssembler {
    static final Log log = new Log(XtceToGpbAssembler.class);

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
            if (c.getAncillaryData() != null) {
                for (AncillaryData data : c.getAncillaryData()) {
                    cb.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
                }
            }
            cb.setArchivePartition(c.useAsArchivePartition());
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
                cb.setRestrictionCriteriaExpression(toExpressionString(c.getRestrictionCriteria()));
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
        case CONTAINER_START:
            b.setReferenceLocation(SequenceEntryInfo.ReferenceLocationType.CONTAINER_START);
            break;
        case PREVIOUS_ENTRY:
            b.setReferenceLocation(SequenceEntryInfo.ReferenceLocationType.PREVIOUS_ENTRY);
            break;
        default:
            throw new IllegalStateException("Unexpected reference location " + e);
        }

        if (e instanceof ContainerEntry ce) {
            if (detail == DetailLevel.SUMMARY) {
                b.setContainer(toContainerInfo(ce.getRefContainer(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setContainer(toContainerInfo(ce.getRefContainer(), DetailLevel.FULL));
            }
        } else if (e instanceof ParameterEntry pe) {
            if (detail == DetailLevel.SUMMARY) {
                b.setParameter(toParameterInfo(pe.getParameter(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setParameter(toParameterInfo(pe.getParameter(), DetailLevel.FULL));
            }
        } else if (e instanceof ArrayParameterEntry ae) {
            if (detail == DetailLevel.SUMMARY) {
                b.setParameter(toParameterInfo(ae.getParameter(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setParameter(toParameterInfo(ae.getParameter(), DetailLevel.FULL));
            }
            // TODO map dimensions info
        } else if (e instanceof ArgumentEntry ae) {
            b.setArgument(toArgumentInfo(ae.getArgument()));
        } else if (e instanceof FixedValueEntry fe) {
            FixedValueInfo.Builder feb = FixedValueInfo.newBuilder();
            if (fe.getName() != null) {
                feb.setName(fe.getName());
            }
            if (fe.getSizeInBits() != -1) {
                feb.setSizeInBits(fe.getSizeInBits());
            }
            feb.setHexValue(StringConverter.arrayToHexString(fe.getBinaryValue()));
            b.setFixedValue(feb.build());
        } else if (e instanceof IndirectParameterRefEntry ipe) {
            IndirectParameterRefInfo.Builder ipeb = IndirectParameterRefInfo.newBuilder();
            if (ipe.getAliasNameSpace() != null) {
                ipeb.setAliasNamespace(ipe.getAliasNameSpace());
            }
            ipeb.setParameter(toParameterInfo(ipe.getParameterRef()));
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
                        toParameterInfo(val.getParameterInstanceRef().getParameter(), DetailLevel.LINK));
            } else if (detail == DetailLevel.FULL) {
                b.setDynamicCount(
                        toParameterInfo(val.getParameterInstanceRef().getParameter(), DetailLevel.FULL));
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
            if (cmd.getAncillaryData() != null) {
                for (AncillaryData data : cmd.getAncillaryData()) {
                    cb.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
                }
            }

            if (cmd.getDefaultSignificance() != null) {
                var significanceInfo = toSignificanceInfo(cmd.getDefaultSignificance());
                cb.setSignificance(significanceInfo);
                cb.setEffectiveSignificance(significanceInfo);
            } else if (cmd.getEffectiveDefaultSignificance() != null) {
                var significanceInfo = toSignificanceInfo(cmd.getEffectiveDefaultSignificance());
                cb.setEffectiveSignificance(significanceInfo);
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

            for (CommandVerifier verifier : cmd.getCommandVerifiers()) {
                cb.addVerifier(toVerifierInfo(verifier));
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
            if (xtceArgument.getArgumentType() != null) {
                String strInitialValue = xtceArgument.getArgumentType().toString(xtceArgument.getInitialValue());
                b.setInitialValue(strInitialValue);
            } else {
                log.warn("Argument {} has no type so cannot convert initial value to string", xtceArgument.getName());
            }
        }

        if (xtceArgument.getArgumentType() != null) {
            ArgumentType xtceType = xtceArgument.getArgumentType();
            b.setType(toArgumentTypeInfo(xtceType));
            if (!b.hasInitialValue()) {
                String initialValue = null;
                initialValue = getDataTypeInitialValue(xtceArgument.getArgumentType());
                if (initialValue != null) {
                    b.setInitialValue(initialValue);
                }
            }
        }
        return b.build();
    }

    public static ArgumentInfo toArgumentInfo(ArgumentInstanceRef ref) {
        ArgumentInfo.Builder b = ArgumentInfo.newBuilder();
        Argument arg = ref.getArgument();
        PathElement[] path = ref.getMemberPath();
        if (path == null) {
            b.setName(arg.getName());
        } else {
            String memberPath = "";
            for (PathElement el : path) {
                memberPath += "." + el.toString();
            }
            b.setName(arg.getName() + memberPath);
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

        TransmissionConstraintInfo.Builder b = TransmissionConstraintInfo.newBuilder()
                .setTimeout(xtceConstraint.getTimeout())
                .setExpression(toExpressionString(xtceConstraint.getMatchCriteria()));
        return b.build();
    }

    public static VerifierInfo toVerifierInfo(CommandVerifier xtceVerifier) {
        VerifierInfo.Builder b = VerifierInfo.newBuilder();
        b.setStage(xtceVerifier.getStage());
        b.setCheckWindow(toCheckWindow(xtceVerifier.getCheckWindow()));
        if (xtceVerifier.getOnSuccess() != null) {
            b.setOnSuccess(toTerminationType(xtceVerifier.getOnSuccess()));
        }
        if (xtceVerifier.getOnFail() != null) {
            b.setOnFail(toTerminationType(xtceVerifier.getOnFail()));
        }
        if (xtceVerifier.getOnTimeout() != null) {
            b.setOnTimeout(toTerminationType(xtceVerifier.getOnTimeout()));
        }
        if (xtceVerifier.getAlgorithm() != null) {
            b.setAlgorithm(toAlgorithmInfo(xtceVerifier.getAlgorithm(), DetailLevel.SUMMARY));
        }
        if (xtceVerifier.getContainerRef() != null) {
            b.setContainer(toContainerInfo(xtceVerifier.getContainerRef(), DetailLevel.SUMMARY));
        }
        if (xtceVerifier.getMatchCriteria() != null) {
            b.setExpression(toExpressionString(xtceVerifier.getMatchCriteria()));
        }
        return b.build();
    }

    private static TerminationActionType toTerminationType(TerminationAction xtceTerminationAction) {
        switch (xtceTerminationAction) {
        case FAIL:
            return TerminationActionType.FAIL;
        case SUCCESS:
            return TerminationActionType.SUCCESS;
        default:
            throw new IllegalStateException("Unexpected termination action " + xtceTerminationAction);
        }
    }

    private static CheckWindowInfo toCheckWindow(CheckWindow checkWindow) {
        CheckWindowInfo.Builder b = CheckWindowInfo.newBuilder();
        b.setTimeToStopChecking(checkWindow.getTimeToStopChecking());
        b.setRelativeTo(checkWindow.getTimeWindowIsRelativeTo().toString());
        if (checkWindow.hasStart()) {
            b.setTimeToStartChecking(checkWindow.getTimeToStartChecking());
        }
        return b.build();
    }

    public static ComparisonInfo toComparisonInfo(Comparison xtceComparison) {
        ComparisonInfo.Builder b = ComparisonInfo.newBuilder();
        ParameterOrArgumentRef ref = xtceComparison.getRef();
        if (ref instanceof ParameterInstanceRef) {
            b.setParameter(toParameterInfo((ParameterInstanceRef) ref));
        } else {
            b.setArgument(toArgumentInfo((ArgumentInstanceRef) ref));
        }
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
        SignificanceLevelType level = toSignificanceLevelType(xtceSignificance.getConsequenceLevel());
        b.setConsequenceLevel(level);
        if (xtceSignificance.getReasonForWarning() != null) {
            b.setReasonForWarning(xtceSignificance.getReasonForWarning());
        }

        return b.build();
    }

    public static SignificanceLevelType toSignificanceLevelType(Levels level) {
        switch (level) {
        case NONE:
            return SignificanceLevelType.NONE;
        case WATCH:
            return SignificanceLevelType.WATCH;
        case WARNING:
            return SignificanceLevelType.WARNING;
        case DISTRESS:
            return SignificanceLevelType.DISTRESS;
        case CRITICAL:
            return SignificanceLevelType.CRITICAL;
        case SEVERE:
            return SignificanceLevelType.SEVERE;
        default:
            throw new IllegalStateException("Unexpected level " + level);
        }
    }

    public static ParameterInfo toParameterInfo(ParameterInstanceRef ref) {
        ParameterInfo.Builder b = ParameterInfo.newBuilder();
        Parameter p = ref.getParameter();
        PathElement[] path = ref.getMemberPath();
        if (path == null) {
            b.setName(ref.getParameter().getName());
            b.setQualifiedName(p.getQualifiedName());
        } else {
            String memberPath = "";
            for (PathElement el : path) {
                memberPath += "." + el.toString();
            }
            b.setName(p.getName() + memberPath);
            b.setQualifiedName(p.getQualifiedName() + memberPath);
        }
        return b.build();
    }

    public static ParameterInfo toParameterInfo(ParameterWithId parameterWithId, DetailLevel detail) {
        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(parameterWithId.getParameter(), detail);
        if (parameterWithId.getPath() != null && parameterWithId.getPath().length > 0) {
            ParameterInfo.Builder infob = ParameterInfo.newBuilder(pinfo);
            for (PathElement el : parameterWithId.getPath()) {
                infob.addPath(el.toString());
            }
            pinfo = infob.build();
        }
        return pinfo;
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
            if (p.getAncillaryData() != null) {
                for (AncillaryData data : p.getAncillaryData()) {
                    b.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
                }
            }
        }

        return b.build();
    }

    public static AncillaryDataInfo toAncillaryDataInfo(AncillaryData data) {
        AncillaryDataInfo.Builder infob = AncillaryDataInfo.newBuilder();
        if (data.getValue() != null) {
            infob.setValue(data.getValue());
        }
        if (data.getMimeType() != null) {
            infob.setMimeType(data.getMimeType());
        }
        if (data.getHref() != null) {
            infob.setHref(data.getHref().toString());
        }
        return infob.build();
    }

    public static ParameterTypeInfo toParameterTypeInfo(ParameterType parameterType, DetailLevel detail) {
        ParameterTypeInfo.Builder infob = ParameterTypeInfo.newBuilder();
        infob.setName(parameterType.getName());
        infob.setEngType(parameterType.getTypeAsString());

        if (parameterType instanceof NameDescription) {
            var nameDescription = (NameDescription) parameterType;

            infob.setQualifiedName(parameterType.getQualifiedName());

            if (detail == DetailLevel.SUMMARY || detail == DetailLevel.FULL) {
                if (parameterType.getShortDescription() != null) {
                    infob.setShortDescription(parameterType.getShortDescription());
                }
            }

            if (detail == DetailLevel.FULL) {
                if (parameterType.getLongDescription() != null) {
                    infob.setLongDescription(parameterType.getLongDescription());
                }
                if (nameDescription.getAliasSet() != null) {
                    Map<String, String> aliases = nameDescription.getAliasSet().getAliases();
                    for (Entry<String, String> me : aliases.entrySet()) {
                        infob.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
                    }
                }
                if (nameDescription.getAncillaryData() != null) {
                    for (AncillaryData data : nameDescription.getAncillaryData()) {
                        infob.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
                    }
                }
            }
        }

        if (parameterType instanceof BaseDataType) {
            BaseDataType bdt = (BaseDataType) parameterType;
            for (UnitType ut : bdt.getUnitSet()) {
                infob.addUnitSet(toUnitInfo(ut));
            }
        } else if (parameterType instanceof AggregateParameterType) {
            AggregateParameterType apt = (AggregateParameterType) parameterType;
            for (Member member : apt.getMemberList()) {
                MemberInfo.Builder memberb = MemberInfo.newBuilder();
                memberb.setName(member.getName());
                if (member.getType() instanceof ParameterType) {
                    ParameterType ptype = (ParameterType) member.getType();
                    memberb.setType(toParameterTypeInfo(ptype, detail));
                }
                if (member.getShortDescription() != null) {
                    memberb.setShortDescription(member.getShortDescription());
                }
                if (member.getLongDescription() != null) {
                    memberb.setLongDescription(member.getLongDescription());
                }
                if (member.getAliasSet() != null) {
                    Map<String, String> aliases = member.getAliasSet().getAliases();
                    for (Entry<String, String> me : aliases.entrySet()) {
                        memberb.addAlias(NamedObjectId.newBuilder()
                                .setName(me.getValue()).setNamespace(me.getKey()));
                    }
                }
                infob.addMember(memberb);
            }
        } else if (parameterType instanceof ArrayParameterType) {
            ArrayParameterType apt = (ArrayParameterType) parameterType;
            ArrayInfo.Builder arrayInfob = ArrayInfo.newBuilder();
            List<IntegerValue> dims = apt.getSize();
            for (int i = 0; i < apt.getNumberOfDimensions(); i++) {
                if (dims != null) { // XTCE 1.2+
                    IntegerValue dim = dims.get(i);
                    if (dim instanceof FixedIntegerValue) {
                        arrayInfob.addDimensions(ParameterDimensionInfo.newBuilder()
                                .setFixedValue(((FixedIntegerValue) dim).getValue()));
                    } else if (dim instanceof DynamicIntegerValue) {
                        ParameterDimensionInfo.Builder dimb = ParameterDimensionInfo.newBuilder();
                        DynamicIntegerValue dynamicValue = (DynamicIntegerValue) dim;
                        ParameterInstanceRef ref = dynamicValue.getParameterInstanceRef();
                        if (ref != null) {
                            dimb.setParameter(toParameterInfo(ref.getParameter(), DetailLevel.SUMMARY));
                            dimb.setSlope(dynamicValue.getSlope());
                            dimb.setIntercept(dynamicValue.getIntercept());
                        }
                        arrayInfob.addDimensions(dimb);
                    }
                } else { // XTCE 1.1
                    arrayInfob.addDimensions(ParameterDimensionInfo.getDefaultInstance());
                }
            }

            if (apt.getElementType() instanceof ParameterType) {
                ParameterType elementType = (ParameterType) apt.getElementType();
                arrayInfob.setType(toParameterTypeInfo(elementType, detail));
            }
            infob.setArrayInfo(arrayInfob);
        } else {
            throw new IllegalStateException("unknown parameter type " + parameterType);
        }

        if (detail == DetailLevel.FULL) {
            if (parameterType instanceof BaseDataType) {
                BaseDataType bdt = (BaseDataType) parameterType;
                if (bdt.getEncoding() != null) {
                    infob.setDataEncoding(toDataEncodingInfo(bdt.getEncoding()));
                }
            }
            if (parameterType instanceof NameDescription) {
                NameDescription namedItem = (NameDescription) parameterType;
                if (namedItem.getAncillaryData() != null) {
                    for (AncillaryData data : namedItem.getAncillaryData()) {
                        infob.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
                    }
                }
            }

            if (parameterType instanceof IntegerParameterType) {
                IntegerParameterType ipt = (IntegerParameterType) parameterType;
                infob.setSigned(ipt.isSigned());
                infob.setSizeInBits(ipt.getSizeInBits());
                if (ipt.getDefaultAlarm() != null) {
                    infob.setDefaultAlarm(toAlarmInfo(ipt.getDefaultAlarm()));
                }
                if (ipt.getContextAlarmList() != null) {
                    for (NumericContextAlarm contextAlarm : ipt.getContextAlarmList()) {
                        infob.addContextAlarm(toContextAlarmInfo(contextAlarm));
                    }
                }
                if (ipt.getNumberFormat() != null) {
                    infob.setNumberFormat(toNumberFormatTypeInfo(ipt.getNumberFormat()));
                }
            } else if (parameterType instanceof FloatParameterType) {
                FloatParameterType fpt = (FloatParameterType) parameterType;
                infob.setSizeInBits(fpt.getSizeInBits());
                if (fpt.getDefaultAlarm() != null) {
                    infob.setDefaultAlarm(toAlarmInfo(fpt.getDefaultAlarm()));
                }
                if (fpt.getContextAlarmList() != null) {
                    for (NumericContextAlarm contextAlarm : fpt.getContextAlarmList()) {
                        infob.addContextAlarm(toContextAlarmInfo(contextAlarm));
                    }
                }
                if (fpt.getNumberFormat() != null) {
                    infob.setNumberFormat(toNumberFormatTypeInfo(fpt.getNumberFormat()));
                }
            } else if (parameterType instanceof EnumeratedParameterType) {
                EnumeratedParameterType ept = (EnumeratedParameterType) parameterType;
                if (ept.getDefaultAlarm() != null) {
                    infob.setDefaultAlarm(toAlarmInfo(ept.getDefaultAlarm()));
                }
                if (ept.getContextAlarmList() != null) {
                    for (EnumerationContextAlarm contextAlarm : ept.getContextAlarmList()) {
                        infob.addContextAlarm(toContextAlarmInfo(contextAlarm));
                    }
                }
                List<ValueEnumeration> sortedEnumerations = new ArrayList<>(ept.getValueEnumerationList());
                Collections.sort(sortedEnumerations, (a, b) -> Long.compare(a.getValue(), b.getValue()));
                for (ValueEnumeration xtceValue : sortedEnumerations) {
                    infob.addEnumValue(toEnumValue(xtceValue));
                }
            } else if (parameterType instanceof AbsoluteTimeParameterType) {
                AbsoluteTimeParameterType apt = (AbsoluteTimeParameterType) parameterType;
                AbsoluteTimeInfo.Builder timeb = AbsoluteTimeInfo.newBuilder();
                if (apt.getInitialValue() != null) {
                    timeb.setInitialValue(apt.getInitialValue().toString());
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
            } else if (parameterType instanceof BooleanParameterType) {
                BooleanParameterType bpt = (BooleanParameterType) parameterType;
                infob.setOneStringValue(bpt.getOneStringValue());
                infob.setZeroStringValue(bpt.getZeroStringValue());
            }
        }
        return infob.build();
    }

    private static NumberFormatTypeInfo toNumberFormatTypeInfo(NumberFormatType numberFormatType) {
        NumberFormatTypeInfo.Builder infob = NumberFormatTypeInfo.newBuilder();
        infob.setNumberBase(numberFormatType.getNumberBase().name());
        infob.setMinimumFractionDigits(numberFormatType.getMinimumFractionDigits());
        if (numberFormatType.getMaximumFractionDigits() >= 0) {
            infob.setMaximumFractionDigits(numberFormatType.getMaximumFractionDigits());
        }
        infob.setMinimumIntegerDigits(numberFormatType.getMinimumIntegerDigits());
        if (numberFormatType.getMaximumIntegerDigits() >= 0) {
            infob.setMaximumIntegerDigits(numberFormatType.getMaximumIntegerDigits());
        }
        if (numberFormatType.getNegativeSuffix() != null) {
            infob.setNegativeSuffix(numberFormatType.getNegativeSuffix());
        }
        if (numberFormatType.getPositiveSuffix() != null) {
            infob.setPositiveSuffix(numberFormatType.getPositiveSuffix());
        }
        if (numberFormatType.getNegativePrefix() != null) {
            infob.setNegativePrefix(numberFormatType.getNegativePrefix());
        }
        if (numberFormatType.getPositivePrefix() != null) {
            infob.setPositivePrefix(numberFormatType.getPositivePrefix());
        }
        infob.setShowThousandsGrouping(numberFormatType.isShowThousandsGrouping());
        infob.setNotation(numberFormatType.getNotation().name());

        return infob.build();
    }

    private static String getDataTypeInitialValue(DataType dataType) {
        if (dataType == null || dataType.getInitialValue() == null) {
            return null;
        }
        return dataType.toString(dataType.getInitialValue());
    }

    public static ArgumentTypeInfo toArgumentTypeInfo(ArgumentType argumentType) {
        ArgumentTypeInfo.Builder infob = ArgumentTypeInfo.newBuilder()
                .setEngType(argumentType.getTypeAsString());
        if (argumentType.getName() != null) {
            infob.setName(argumentType.getName());
        }

        if (argumentType instanceof BaseDataType) {
            BaseDataType bdt = (BaseDataType) argumentType;
            if (bdt.getEncoding() != null) {
                infob.setDataEncoding(toDataEncodingInfo(bdt.getEncoding()));
            }
            for (UnitType ut : argumentType.getUnitSet()) {
                infob.addUnitSet(toUnitInfo(ut));
            }
        }

        if (argumentType instanceof AggregateArgumentType) {
            AggregateArgumentType aat = (AggregateArgumentType) argumentType;
            for (Member member : aat.getMemberList()) {
                ArgumentMemberInfo.Builder memberb = ArgumentMemberInfo.newBuilder();
                memberb.setName(member.getName());
                if (member.getType() instanceof ArgumentType) {
                    ArgumentType ptype = (ArgumentType) member.getType();
                    memberb.setType(toArgumentTypeInfo(ptype));
                    if (member.getInitialValue() != null) {
                        String initialValue = ptype.toString(member.getInitialValue());
                        memberb.setInitialValue(initialValue);
                    } else if (ptype.getInitialValue() != null) {
                        String initialValue = ptype.toString(ptype.getInitialValue());
                        memberb.setInitialValue(initialValue);
                    }
                }
                if (member.getShortDescription() != null) {
                    memberb.setShortDescription(member.getShortDescription());
                }
                if (member.getLongDescription() != null) {
                    memberb.setLongDescription(member.getLongDescription());
                }
                if (member.getAliasSet() != null) {
                    Map<String, String> aliases = member.getAliasSet().getAliases();
                    for (Entry<String, String> me : aliases.entrySet()) {
                        memberb.addAlias(NamedObjectId.newBuilder()
                                .setName(me.getValue()).setNamespace(me.getKey()));
                    }
                }
                infob.addMember(memberb);
            }
        } else if (argumentType instanceof ArrayArgumentType) {
            ArrayArgumentType aat = (ArrayArgumentType) argumentType;
            for (int i = 0; i < aat.getNumberOfDimensions(); i++) {
                ArgumentDimensionInfo.Builder dimensionb = ArgumentDimensionInfo.newBuilder();
                IntegerValue dimension = aat.getDimension(i);
                if (dimension instanceof FixedIntegerValue) {
                    var fixedIntegerValue = (FixedIntegerValue) dimension;
                    dimensionb.setFixedValue(fixedIntegerValue.getValue());
                } else if (dimension instanceof DynamicIntegerValue) {
                    var dynamicIntegerValue = (DynamicIntegerValue) dimension;
                    ParameterOrArgumentRef ref = dynamicIntegerValue.getDynamicInstanceRef();
                    if (ref instanceof ParameterInstanceRef) {
                        ParameterInstanceRef parameterRef = (ParameterInstanceRef) ref;
                        dimensionb.setParameter(toParameterInfo(parameterRef.getParameter(), DetailLevel.SUMMARY));
                    } else if (ref instanceof ArgumentInstanceRef) {
                        ArgumentInstanceRef argumentRef = (ArgumentInstanceRef) ref;
                        PathElement[] path = ref.getMemberPath();
                        if (path == null) {
                            dimensionb.setArgument(argumentRef.getName());
                        } else {
                            String memberPath = "";
                            for (PathElement el : path) {
                                memberPath += "." + el.toString();
                            }
                            dimensionb.setArgument(argumentRef.getName() + memberPath);
                        }
                    }
                    dimensionb.setSlope(dynamicIntegerValue.getSlope());
                    dimensionb.setIntercept(dynamicIntegerValue.getIntercept());
                }
                if (aat.getElementType() instanceof ArgumentType) {
                    ArgumentType elementType = (ArgumentType) aat.getElementType();
                    infob.setElementType(toArgumentTypeInfo(elementType));
                }
                infob.addDimensions(dimensionb);
            }
        } else if (argumentType instanceof IntegerArgumentType) {
            IntegerArgumentType iat = (IntegerArgumentType) argumentType;
            infob.setSigned(iat.isSigned());
            if (iat.getValidRange() != null) {
                if (iat.getValidRange().getMinInclusive() != Long.MIN_VALUE) {
                    infob.setRangeMin(iat.getValidRange().getMinInclusive());
                }
                if (iat.getValidRange().getMaxInclusive() != Long.MAX_VALUE) {
                    infob.setRangeMax(iat.getValidRange().getMaxInclusive());
                }
            }
        } else if (argumentType instanceof FloatArgumentType) {
            FloatArgumentType fat = (FloatArgumentType) argumentType;
            if (fat.getValidRange() != null) {
                if (!Double.isNaN(fat.getValidRange().getMin())) {
                    infob.setRangeMin(fat.getValidRange().getMin());
                }
                if (!Double.isNaN(fat.getValidRange().getMax())) {
                    infob.setRangeMax(fat.getValidRange().getMax());
                }
            }
        } else if (argumentType instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType eat = (EnumeratedArgumentType) argumentType;
            for (ValueEnumeration xtceValue : eat.getValueEnumerationList()) {
                infob.addEnumValue(toEnumValue(xtceValue));
            }
        } else if (argumentType instanceof BooleanArgumentType) {
            BooleanArgumentType bat = (BooleanArgumentType) argumentType;
            infob.setZeroStringValue(bat.getZeroStringValue());
            infob.setOneStringValue(bat.getOneStringValue());
        } else if (argumentType instanceof StringArgumentType) {
            StringArgumentType sat = (StringArgumentType) argumentType;
            IntegerRange sizeRange = sat.getSizeRangeInCharacters();
            if (sizeRange != null) {
                if (sizeRange.getMinInclusive() != Long.MIN_VALUE) {
                    infob.setMinChars((int) sizeRange.getMinInclusive());
                }
                if (sizeRange.getMaxInclusive() != Long.MAX_VALUE) {
                    infob.setMaxChars((int) sizeRange.getMaxInclusive());
                }
            }
        } else if (argumentType instanceof BinaryArgumentType) {
            BinaryArgumentType bat = (BinaryArgumentType) argumentType;
            IntegerRange sizeRange = bat.getSizeRangeInBytes();
            if (sizeRange != null) {
                if (sizeRange.getMinInclusive() != Long.MIN_VALUE) {
                    infob.setMinBytes((int) sizeRange.getMinInclusive());
                }
                if (sizeRange.getMaxInclusive() != Long.MAX_VALUE) {
                    infob.setMaxBytes((int) sizeRange.getMaxInclusive());
                }
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
        }

        // Other classes (ANDedConditions, ORedConditions) are ignored for now
        // These first require serializing support for arbitrary expressions.

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
            infob.setType(DataEncodingInfo.Type.BINARY);
            infob.setEncoding(toTextualEncoding((BinaryDataEncoding) xtceDataEncoding));
        } else if (xtceDataEncoding instanceof BooleanDataEncoding) {
            infob.setType(DataEncodingInfo.Type.BOOLEAN);
        } else if (xtceDataEncoding instanceof FloatDataEncoding) {
            FloatDataEncoding fde = (FloatDataEncoding) xtceDataEncoding;
            if (fde.getEncoding() == FloatDataEncoding.Encoding.STRING) {
                infob.setType(DataEncodingInfo.Type.STRING);
                infob.setEncoding(toTextualEncoding(fde.getStringDataEncoding()));
            } else {
                infob.setType(DataEncodingInfo.Type.FLOAT);
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
                    infob.addContextCalibrators(contextCalibratorb);
                    infob.addContextCalibrator(contextCalibratorb);
                }
            }
        } else if (xtceDataEncoding instanceof IntegerDataEncoding) {
            IntegerDataEncoding ide = (IntegerDataEncoding) xtceDataEncoding;
            if (ide.getEncoding() == IntegerDataEncoding.Encoding.STRING) {
                infob.setType(DataEncodingInfo.Type.STRING);
                infob.setEncoding(toTextualEncoding(ide.getStringEncoding()));
            } else {
                infob.setType(DataEncodingInfo.Type.INTEGER);
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
                    infob.addContextCalibrators(contextCalibratorb);
                    infob.addContextCalibrator(contextCalibratorb);
                }
            }
        } else if (xtceDataEncoding instanceof StringDataEncoding) {
            infob.setType(DataEncodingInfo.Type.STRING);
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

    public static String toTextualEncoding(BinaryDataEncoding bde) {
        String result = bde.getType() + "(";
        switch (bde.getType()) {
        case FIXED_SIZE:
            result += bde.getSizeInBits();
            break;
        case LEADING_SIZE:
            result += bde.getSizeInBitsOfSizeTag();
            break;
        case CUSTOM:
            break;
        case DYNAMIC:
            break;
        default:
            throw new IllegalStateException("Unexpected type " + bde.getType());
        }
        return result + ")";
    }

    public static Mdb.EnumValue toEnumValue(ValueEnumeration xtceValue) {
        Mdb.EnumValue.Builder b = Mdb.EnumValue.newBuilder();
        b.setValue(xtceValue.getValue());
        b.setLabel(xtceValue.getLabel());
        if (xtceValue.getDescription() != null) {
            b.setDescription(xtceValue.getDescription());
        }
        return b.build();
    }

    public static UnitInfo toUnitInfo(UnitType ut) {
        return UnitInfo.newBuilder().setUnit(ut.getUnit()).build();
    }

    public static CalibratorInfo toCalibratorInfo(Calibrator calibrator) {
        CalibratorInfo.Builder calibratorInfob = CalibratorInfo.newBuilder();
        if (calibrator instanceof PolynomialCalibrator) {
            calibratorInfob.setType(CalibratorInfo.Type.POLYNOMIAL);
            PolynomialCalibrator polynomialCalibrator = (PolynomialCalibrator) calibrator;
            PolynomialCalibratorInfo.Builder polyb = PolynomialCalibratorInfo.newBuilder();
            for (double coefficient : polynomialCalibrator.getCoefficients()) {
                polyb.addCoefficients(coefficient);
                polyb.addCoefficient(coefficient);
            }
            calibratorInfob.setPolynomialCalibrator(polyb);
        } else if (calibrator instanceof SplineCalibrator) {
            calibratorInfob.setType(CalibratorInfo.Type.SPLINE);
            SplineCalibrator splineCalibrator = (SplineCalibrator) calibrator;
            SplineCalibratorInfo.Builder splineb = SplineCalibratorInfo.newBuilder();
            for (SplinePoint point : splineCalibrator.getPoints()) {
                var pointInfo = SplinePointInfo.newBuilder()
                        .setRaw(point.getRaw())
                        .setCalibrated(point.getCalibrated());
                splineb.addPoints(pointInfo);
                splineb.addPoint(pointInfo);
            }
            calibratorInfob.setSplineCalibrator(splineb);
        } else if (calibrator instanceof JavaExpressionCalibrator) {
            calibratorInfob.setType(CalibratorInfo.Type.JAVA_EXPRESSION);
            JavaExpressionCalibrator javaCalibrator = (JavaExpressionCalibrator) calibrator;
            JavaExpressionCalibratorInfo.Builder javab = JavaExpressionCalibratorInfo.newBuilder();
            javab.setFormula(javaCalibrator.getFormula());
            calibratorInfob.setJavaExpressionCalibrator(javab);
        } else if (calibrator instanceof MathOperationCalibrator) {
            calibratorInfob.setType(CalibratorInfo.Type.MATH_OPERATION);
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
            AlarmRange watchRange = BasicParameterValue.toGpbAlarmRange(AlarmLevelType.WATCH,
                    staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRanges(watchRange);
            alarmInfob.addStaticAlarmRange(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = BasicParameterValue.toGpbAlarmRange(AlarmLevelType.WARNING,
                    staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRanges(warningRange);
            alarmInfob.addStaticAlarmRange(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = BasicParameterValue.toGpbAlarmRange(AlarmLevelType.DISTRESS,
                    staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRanges(distressRange);
            alarmInfob.addStaticAlarmRange(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = BasicParameterValue.toGpbAlarmRange(AlarmLevelType.CRITICAL,
                    staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRanges(criticalRange);
            alarmInfob.addStaticAlarmRange(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = BasicParameterValue.toGpbAlarmRange(AlarmLevelType.SEVERE,
                    staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRanges(severeRange);
            alarmInfob.addStaticAlarmRange(severeRange);
        }

        return alarmInfob.build();
    }

    private static ContextAlarmInfo toContextAlarmInfo(NumericContextAlarm contextAlarm) {
        ContextAlarmInfo.Builder resultb = ContextAlarmInfo.newBuilder()
                .setAlarm(toAlarmInfo(contextAlarm))
                .setContext(toExpressionString(contextAlarm.getContextMatch()))
                .addAllComparison(toComparisons(contextAlarm.getContextMatch()));
        return resultb.build();
    }

    public static AlarmInfo toAlarmInfo(EnumerationAlarm enumerationAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(enumerationAlarm.getMinViolations());
        for (EnumerationAlarmItem item : enumerationAlarm.getAlarmList()) {
            alarmInfob.addEnumerationAlarms(toEnumerationAlarm(item));
            alarmInfob.addEnumerationAlarm(toEnumerationAlarm(item));
        }
        return alarmInfob.build();
    }

    private static ContextAlarmInfo toContextAlarmInfo(EnumerationContextAlarm contextAlarm) {
        ContextAlarmInfo.Builder resultb = ContextAlarmInfo.newBuilder()
                .setAlarm(toAlarmInfo(contextAlarm))
                .setContext(toExpressionString(contextAlarm.getContextMatch()))
                .addAllComparison(toComparisons(contextAlarm.getContextMatch()));
        return resultb.build();
    }

    public static Mdb.EnumerationAlarm toEnumerationAlarm(EnumerationAlarmItem xtceAlarmItem) {
        Mdb.EnumerationAlarm.Builder resultb = Mdb.EnumerationAlarm.newBuilder();
        resultb.setLabel(xtceAlarmItem.getEnumerationLabel());
        switch (xtceAlarmItem.getAlarmLevel()) {
        case NORMAL:
            resultb.setLevel(AlarmLevelType.NORMAL);
            break;
        case WATCH:
            resultb.setLevel(AlarmLevelType.WATCH);
            break;
        case WARNING:
            resultb.setLevel(AlarmLevelType.WARNING);
            break;
        case DISTRESS:
            resultb.setLevel(AlarmLevelType.DISTRESS);
            break;
        case CRITICAL:
            resultb.setLevel(AlarmLevelType.CRITICAL);
            break;
        case SEVERE:
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
            case CONTAINER_PROCESSING:
                b.setScope(Scope.CONTAINER_PROCESSING);
                break;
            default:
                throw new IllegalStateException("Unexpected scope " + a.getScope());
            }

            if (a instanceof CustomAlgorithm) {
                b.setType(AlgorithmInfo.Type.CUSTOM);
                CustomAlgorithm ca = (CustomAlgorithm) a;
                if (ca.getLanguage() != null) {
                    b.setLanguage(ca.getLanguage());
                }
                if (ca.getAlgorithmText() != null) {
                    b.setText(ca.getAlgorithmText());
                }
            } else if (a instanceof MathAlgorithm) {
                b.setType(AlgorithmInfo.Type.MATH);
                MathAlgorithm ma = (MathAlgorithm) a;
                for (var el : ma.getOperation().getElementList()) {
                    switch (el.getType()) {
                    case OPERATOR:
                        b.addMathElements(MathElement.newBuilder()
                                .setType(MathElement.Type.OPERATOR)
                                .setOperator(el.getOperator().xtceName()));
                        break;
                    case THIS_PARAMETER_OPERAND:
                        b.addMathElements(MathElement.newBuilder()
                                .setType(MathElement.Type.THIS_PARAMETER_OPERAND));
                        break;
                    case VALUE_OPERAND:
                        b.addMathElements(MathElement.newBuilder()
                                .setType(MathElement.Type.VALUE_OPERAND)
                                .setValue(el.getValue()));
                        break;
                    case PARAMETER_INSTANCE_REF_OPERAND:
                        var pref = el.getParameterInstanceRef();
                        b.addMathElements(MathElement.newBuilder()
                                .setType(MathElement.Type.PARAMETER)
                                .setParameter(toParameterInfo(pref.getParameter(), DetailLevel.SUMMARY))
                                .setParameterInstance(pref.getInstance()));
                        break;
                    default:
                        throw new IllegalStateException("Unexpected math element " + el.getType());
                    }
                }
            } else {
                throw new IllegalStateException("Unexpected algorithm type " + a.getClass());
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
        ParameterInstanceRef pref = xtceInput.getParameterInstance();
        if (pref != null) {
            resultb.setParameter(toParameterInfo(pref.getParameter(), DetailLevel.SUMMARY));
            resultb.setParameterInstance(pref.getInstance());
        } else {
            resultb.setArgument(toArgumentInfo(xtceInput.getArgumentRef().getArgument()));
        }
        if (xtceInput.getInputName() != null) {
            resultb.setInputName(xtceInput.getInputName());
        }
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

    public static SpaceSystemInfo toSpaceSystemInfo(SpaceSystem ss, DetailLevel detail) {
        SpaceSystemInfo.Builder b = SpaceSystemInfo.newBuilder();
        b.setName(ss.getName());
        b.setQualifiedName(ss.getQualifiedName());
        if (ss.getShortDescription() != null) {
            b.setShortDescription(ss.getShortDescription());
        }
        if (ss.getLongDescription() != null) {
            b.setLongDescription(ss.getLongDescription());
        }
        if (ss.getAliasSet() != null) {
            Map<String, String> aliases = ss.getAliasSet().getAliases();
            for (Entry<String, String> me : aliases.entrySet()) {
                b.addAlias(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
            }
        }
        if (ss.getAncillaryData() != null) {
            for (AncillaryData data : ss.getAncillaryData()) {
                b.putAncillaryData(data.getName(), toAncillaryDataInfo(data));
            }
        }

        if (detail == DetailLevel.FULL) {
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

            for (SpaceSystem sub : ss.getSubSystems()) {
                b.addSub(toSpaceSystemInfo(sub, DetailLevel.FULL));
            }
        }
        return b.build();
    }

    static String toExpressionString(MatchCriteria matchCriteria) {
        MatchCriteriaEvaluator evaluator = MatchCriteriaEvaluatorFactory
                .getEvaluator(matchCriteria);
        return evaluator.toExpressionString();
    }
}
