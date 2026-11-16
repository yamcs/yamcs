package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public class UnsignedComparisonTest {

    @Test
    public void testUint32RawComparisonAboveIntMax() {
        IntegerParameterType ptype = new IntegerParameterType.Builder()
                .setName("uint32")
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(32))
                .setSigned(false)
                .build();
        Parameter p = new Parameter("p");
        p.setParameterType(ptype);

        Comparison comparison = new Comparison(new ParameterInstanceRef(p, false), "2147483650",
                OperatorType.EQUALITY);
        MatchCriteriaEvaluator evaluator = MatchCriteriaEvaluatorFactory.getEvaluator(comparison);

        assertTrue(evaluator.toExpressionString().endsWith(" 2147483650"), evaluator.toExpressionString());

        ParameterValue pv = new ParameterValue(p);
        pv.setRawValue(ValueUtility.getUint32Value((int) 2147483650L));
        assertEquals(MatchResult.OK, evaluator.evaluate(ProcessingContext.createForTestTm(pv)));

        pv = new ParameterValue(p);
        pv.setRawValue(ValueUtility.getUint32Value(2));
        assertEquals(MatchResult.NOK, evaluator.evaluate(ProcessingContext.createForTestTm(pv)));
    }
}
