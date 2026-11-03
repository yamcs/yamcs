package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public class MatchCriteriaEvaluatorFactoryTest {

    @Test
    public void testIntComparedToFloat() {
        Parameter intPara = intParameter("int", false);
        Parameter floatPara = floatParameter("float");

        var ctx = context(intPara, ValueUtility.getSint64Value(3), floatPara, ValueUtility.getDoubleValue(3.0));
        assertEquals(MatchResult.OK, evaluate(OperatorType.EQUALITY, intPara, floatPara, ctx));
        assertEquals(MatchResult.OK, evaluate(OperatorType.EQUALITY, floatPara, intPara, ctx));

        ctx = context(intPara, ValueUtility.getSint64Value(-2), floatPara, ValueUtility.getDoubleValue(1.5));
        assertEquals(MatchResult.OK, evaluate(OperatorType.SMALLERTHAN, intPara, floatPara, ctx));
        assertEquals(MatchResult.OK, evaluate(OperatorType.LARGERTHAN, floatPara, intPara, ctx));
    }

    @Test
    public void testUnsignedIntComparedToFloat() {
        Parameter uintPara = intParameter("uint", true);
        Parameter floatPara = floatParameter("float");

        // 2^63, negative when interpreted as a signed long
        var ctx = context(uintPara, ValueUtility.getUint64Value(Long.MIN_VALUE), floatPara,
                ValueUtility.getDoubleValue(0));
        assertEquals(MatchResult.OK, evaluate(OperatorType.LARGERTHAN, uintPara, floatPara, ctx));
    }

    private static MatchResult evaluate(OperatorType op, Parameter left, Parameter right, ProcessingContext ctx) {
        var condition = new Condition(op, new ParameterInstanceRef(left), new ParameterInstanceRef(right));
        return MatchCriteriaEvaluatorFactory.getEvaluator(condition).evaluate(ctx);
    }

    private static ProcessingContext context(Parameter p1, Value v1, Parameter p2, Value v2) {
        var ctx = ProcessingContext.createForTmProcessing(new LastValueCache(), 0);
        ctx.tmParams.add(parameterValue(p1, v1));
        ctx.tmParams.add(parameterValue(p2, v2));
        return ctx;
    }

    private static ParameterValue parameterValue(Parameter p, Value v) {
        var pv = new ParameterValue(p);
        pv.setEngValue(v);
        return pv;
    }

    private static Parameter intParameter(String name, boolean unsigned) {
        var p = new Parameter(name);
        p.setQualifiedName("/test/" + name);
        p.setParameterType(new IntegerParameterType.Builder()
                .setName(name + "Type")
                .setSigned(!unsigned)
                .setSizeInBits(64)
                .build());
        return p;
    }

    private static Parameter floatParameter(String name) {
        var p = new Parameter(name);
        p.setQualifiedName("/test/" + name);
        p.setParameterType(new FloatParameterType.Builder()
                .setName(name + "Type")
                .setSizeInBits(64)
                .build());
        return p;
    }
}
