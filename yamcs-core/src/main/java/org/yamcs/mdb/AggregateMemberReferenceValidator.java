package org.yamcs.mdb;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.AggregateMemberInstanceRef;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SpaceSystem;

/**
 * Validates lexical aggregate-member references used as dynamic array dimensions.
 * <p>
 * Aggregate member references cannot be resolved to one fixed {@link Member}: a reusable array type may be used under
 * different aggregate types. Validation therefore starts at each parameter and follows every concrete type use site,
 * keeping the same innermost-to-outermost aggregate scope used during telemetry extraction.
 */
final class AggregateMemberReferenceValidator {

    private AggregateMemberReferenceValidator() {
    }

    static void validate(SpaceSystem rootSpaceSystem) {
        for (Parameter parameter : rootSpaceSystem.getParameters(true)) {
            ParameterType parameterType = parameter.getParameterType();
            if (parameterType == null) {
                continue;
            }

            Deque<AggregateScope> aggregateScopes = new ArrayDeque<>();
            Set<DataType> activeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
            validateType(parameterType, aggregateScopes, activeTypes, parameter.getName(), parameter.getName());
        }
    }

    private static void validateType(DataType type, Deque<AggregateScope> aggregateScopes,
            Set<DataType> activeTypes, String parameterName, String usePath) {
        if (!activeTypes.add(type)) {
            throw new DatabaseLoadException("Circular parameter type reference encountered while validating '"
                    + usePath + "' in parameter '" + parameterName + "'");
        }

        try {
            if (type instanceof ArrayDataType arrayType) {
                validateDimensions(arrayType, aggregateScopes, parameterName, usePath);
                validateType(arrayType.getElementType(), aggregateScopes, activeTypes, parameterName, usePath + "[]");
            } else if (type instanceof AggregateDataType aggregateType) {
                List<Member> members = aggregateType.getMemberList();
                for (int i = 0; i < members.size(); i++) {
                    Member member = members.get(i);
                    DataType memberType = member.getType();
                    if (memberType == null) {
                        throw new DatabaseLoadException("Aggregate member '" + member.getName() + "' at '" + usePath
                                + "' in parameter '" + parameterName + "' has no resolved type");
                    }

                    // Only members before this one have values when telemetry extraction enters its type.
                    aggregateScopes.push(new AggregateScope(aggregateType, i));
                    try {
                        validateType(memberType, aggregateScopes, activeTypes, parameterName,
                                usePath + "." + member.getName());
                    } finally {
                        aggregateScopes.pop();
                    }
                }
            }
        } finally {
            activeTypes.remove(type);
        }
    }

    private static void validateDimensions(ArrayDataType arrayType, Deque<AggregateScope> aggregateScopes,
            String parameterName, String usePath) {
        List<IntegerValue> dimensions = arrayType.getSize();
        if (dimensions == null) {
            return;
        }

        for (IntegerValue dimension : dimensions) {
            if (dimension instanceof DynamicIntegerValue dynamicValue
                    && dynamicValue.getDynamicInstanceRef() instanceof AggregateMemberInstanceRef memberRef) {
                validateReference(memberRef, aggregateScopes, parameterName, usePath);
            }
        }
    }

    private static void validateReference(AggregateMemberInstanceRef memberRef,
            Deque<AggregateScope> aggregateScopes, String parameterName, String usePath) {
        String memberName = memberRef.getName();
        for (AggregateScope scope : aggregateScopes) {
            List<Member> members = scope.type().getMemberList();
            for (int i = 0; i < members.size(); i++) {
                Member member = members.get(i);
                if (!memberName.equals(member.getName())) {
                    continue;
                }

                // Finding the name in the nearest scope shadows matching members in outer aggregates, even when the
                // nearest member has not yet been decoded.
                if (i >= scope.availableMemberCount()) {
                    throw new DatabaseLoadException("Aggregate member '" + memberName + "' used as an array size at '"
                            + usePath + "' in parameter '" + parameterName + "' must be declared before the array");
                }
                if (!isIntegerCompatible(member, memberRef.useCalibratedValue())) {
                    String valueKind = memberRef.useCalibratedValue() ? "calibrated" : "raw";
                    throw new DatabaseLoadException("Aggregate member '" + memberName + "' used as an array size at '"
                            + usePath + "' in parameter '" + parameterName + "' has a non-integer " + valueKind
                            + " value");
                }
                return;
            }
        }

        throw new DatabaseLoadException("Cannot find aggregate member '" + memberName + "' used as an array size at '"
                + usePath + "' in parameter '" + parameterName + "'");
    }

    private static boolean isIntegerCompatible(Member member, boolean useCalibratedValue) {
        if (!(member.getType() instanceof ParameterType parameterType)) {
            return false;
        }

        if (!useCalibratedValue) {
            try {
                return parameterType.getEncoding() instanceof IntegerDataEncoding;
            } catch (UnsupportedOperationException e) {
                return false;
            }
        }

        Type valueType = parameterType.getValueType();
        return valueType == Type.SINT32 || valueType == Type.SINT64
                || valueType == Type.UINT32 || valueType == Type.UINT64;
    }

    private record AggregateScope(AggregateDataType type, int availableMemberCount) {
    }
}
