package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.AggregateMemberInstanceRef;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringParameterType;

public class AggregateMemberReferenceValidatorTest {

    @Test
    public void validatesEveryUseOfAReusableArrayType() {
        ParameterType integerType = integerType();
        ArrayParameterType sharedArrayType = arrayType("shared_array", "count", integerType);
        AggregateParameterType firstType = aggregateType("first_type",
                new Member("count", integerType),
                new Member("values", sharedArrayType));
        AggregateParameterType secondType = aggregateType("second_type",
                new Member("count", integerType),
                new Member("values", sharedArrayType));

        SpaceSystem spaceSystem = new SpaceSystem("test");
        spaceSystem.addParameter(parameter("first", firstType));
        spaceSystem.addParameter(parameter("second", secondType));

        assertDoesNotThrow(() -> AggregateMemberReferenceValidator.validate(spaceSystem));
    }

    @Test
    public void resolvesAReferenceFromAnEnclosingAggregate() {
        ParameterType integerType = integerType();
        ArrayParameterType arrayType = arrayType("values_type", "outer_count", integerType);
        AggregateParameterType innerType = aggregateType("inner_type", new Member("values", arrayType));
        AggregateParameterType outerType = aggregateType("outer_type",
                new Member("outer_count", integerType),
                new Member("inner", innerType));

        assertDoesNotThrow(() -> AggregateMemberReferenceValidator.validate(spaceSystem("report", outerType)));
    }

    @Test
    public void rejectsAMissingAggregateMember() {
        ParameterType integerType = integerType();
        ArrayParameterType arrayType = arrayType("values_type", "missing_count", integerType);
        AggregateParameterType reportType = aggregateType("report_type", new Member("values", arrayType));

        DatabaseLoadException exception = assertThrows(DatabaseLoadException.class,
                () -> AggregateMemberReferenceValidator.validate(spaceSystem("report", reportType)));
        assertEquals("Cannot find aggregate member 'missing_count' used as an array size at 'report.values' "
                + "in parameter 'report'", exception.getMessage());
    }

    @Test
    public void rejectsAnAggregateMemberDeclaredAfterTheArray() {
        ParameterType integerType = integerType();
        ArrayParameterType arrayType = arrayType("values_type", "count", integerType);
        AggregateParameterType reportType = aggregateType("report_type",
                new Member("values", arrayType),
                new Member("count", integerType));

        DatabaseLoadException exception = assertThrows(DatabaseLoadException.class,
                () -> AggregateMemberReferenceValidator.validate(spaceSystem("report", reportType)));
        assertEquals("Aggregate member 'count' used as an array size at 'report.values' in parameter 'report' "
                + "must be declared before the array", exception.getMessage());
    }

    @Test
    public void nearestAggregateMemberShadowsAnOuterMemberEvenWhenDeclaredTooLate() {
        ParameterType integerType = integerType();
        ArrayParameterType arrayType = arrayType("values_type", "count", integerType);
        AggregateParameterType innerType = aggregateType("inner_type",
                new Member("values", arrayType),
                new Member("count", integerType));
        AggregateParameterType outerType = aggregateType("outer_type",
                new Member("count", integerType),
                new Member("inner", innerType));

        DatabaseLoadException exception = assertThrows(DatabaseLoadException.class,
                () -> AggregateMemberReferenceValidator.validate(spaceSystem("report", outerType)));
        assertEquals("Aggregate member 'count' used as an array size at 'report.inner.values' in parameter 'report' "
                + "must be declared before the array", exception.getMessage());
    }

    @Test
    public void rejectsANonintegerAggregateMember() {
        ParameterType integerType = integerType();
        ParameterType stringType = new StringParameterType.Builder().setName("string").build();
        ArrayParameterType arrayType = arrayType("values_type", "count", integerType);
        AggregateParameterType reportType = aggregateType("report_type",
                new Member("count", stringType),
                new Member("values", arrayType));

        DatabaseLoadException exception = assertThrows(DatabaseLoadException.class,
                () -> AggregateMemberReferenceValidator.validate(spaceSystem("report", reportType)));
        assertEquals("Aggregate member 'count' used as an array size at 'report.values' in parameter 'report' "
                + "has a non-integer calibrated value", exception.getMessage());
    }

    private static SpaceSystem spaceSystem(String parameterName, ParameterType parameterType) {
        SpaceSystem spaceSystem = new SpaceSystem("test");
        spaceSystem.addParameter(parameter(parameterName, parameterType));
        return spaceSystem;
    }

    private static Parameter parameter(String name, ParameterType type) {
        Parameter parameter = new Parameter(name);
        parameter.setParameterType(type);
        return parameter;
    }

    private static IntegerParameterType integerType() {
        return new IntegerParameterType.Builder()
                .setName("uint8")
                .setSizeInBits(8)
                .setSigned(false)
                .build();
    }

    private static ArrayParameterType arrayType(String name, String countMember, ParameterType elementType) {
        DynamicIntegerValue size = new DynamicIntegerValue(new AggregateMemberInstanceRef(countMember, true));
        return new ArrayParameterType.Builder()
                .setName(name)
                .setElementType(elementType)
                .setSize(List.of(size))
                .build();
    }

    private static AggregateParameterType aggregateType(String name, Member... members) {
        AggregateParameterType.Builder builder = new AggregateParameterType.Builder().setName(name);
        for (Member member : members) {
            builder.addMember(member);
        }
        return builder.build();
    }
}
