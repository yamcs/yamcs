package org.yamcs.activities.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.mdb.MatchCriteriaEvaluatorFactory;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ProcessingContext;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;

public class ConditionExpressionParserTest {

    private static final String INT = "/REFMDB/SUBSYS1/IntegerPara1_1";
    private static final String FLOAT = "/REFMDB/SUBSYS1/FloatPara1_10_3";

    private static Mdb mdb;
    private ConditionExpressionParser parser;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        TimeEncoding.setUp();
        mdb = MdbFactory.createInstanceByConfig("refmdb");
    }

    @BeforeEach
    public void setUp() {
        parser = new ConditionExpressionParser(mdb);
    }

    @Test
    public void testSimpleComparison() throws Exception {
        assertEquals(MatchResult.OK, evaluate(INT + " < 10", intValue(5)));
        assertEquals(MatchResult.NOK, evaluate(INT + " < 10", intValue(10)));
        assertEquals(MatchResult.OK, evaluate(INT + " LE 10", intValue(10)));
        assertEquals(MatchResult.OK, evaluate(INT + " = 10", intValue(10)));
        assertEquals(MatchResult.OK, evaluate(INT + " EQ 10", intValue(10)));
        assertEquals(MatchResult.NOK, evaluate(INT + " != 10", intValue(10)));
    }

    @Test
    public void testLogicalOperators() throws Exception {
        var expr = INT + " > 1 && " + FLOAT + " < 2.5";
        assertEquals(MatchResult.OK, evaluate(expr, intValue(5), floatValue(1)));
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(5), floatValue(3)));

        expr = INT + " > 1 AND " + FLOAT + " < 2.5";
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(0), floatValue(1)));

        expr = INT + " > 1 || " + FLOAT + " < 2.5";
        assertEquals(MatchResult.OK, evaluate(expr, intValue(0), floatValue(1)));
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(0), floatValue(3)));
    }

    @Test
    public void testPrecedence() throws Exception {
        // AND binds stronger than OR: a OR (b AND c)
        var expr = INT + " == 1 OR " + INT + " == 2 AND " + FLOAT + " > 10";
        assertEquals(MatchResult.OK, evaluate(expr, intValue(1), floatValue(0)));
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(2), floatValue(0)));

        expr = "(" + INT + " == 1 OR " + INT + " == 2) AND " + FLOAT + " > 10";
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(1), floatValue(0)));
        assertEquals(MatchResult.OK, evaluate(expr, intValue(2), floatValue(11)));
    }

    @Test
    public void testNot() throws Exception {
        assertEquals(MatchResult.OK, evaluate("NOT " + INT + " < 10", intValue(10)));
        assertEquals(MatchResult.NOK, evaluate("!" + INT + " < 10", intValue(9)));

        // De Morgan: !(a && b) == !a || !b
        var expr = "!(" + INT + " > 1 && " + FLOAT + " > 1)";
        assertEquals(MatchResult.OK, evaluate(expr, intValue(5), floatValue(0)));
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(5), floatValue(5)));
    }

    @Test
    public void testParameterReference() throws Exception {
        var expr = FLOAT + " > $" + FLOAT;
        assertEquals(MatchResult.NOK, evaluate(expr, floatValue(1)));
    }

    @Test
    public void testSpreadsheetSyntax() throws Exception {
        var expr = "&(" + INT + " > 1;" + FLOAT + " < 2.5)";
        assertEquals(MatchResult.OK, evaluate(expr, intValue(5), floatValue(1)));
        assertEquals(MatchResult.NOK, evaluate(expr, intValue(5), floatValue(3)));
    }

    @Test
    public void testAggregateMember() throws Exception {
        parser.parse("/REFMDB/SUBSYS1/aggregate_para1.member1 > 3");
        assertEquals(List.of("/REFMDB/SUBSYS1/aggregate_para1"),
                parser.getParameters().stream().map(p -> p.getQualifiedName()).toList());

        var e = assertThrows(ParseException.class,
                () -> parser.parse("/REFMDB/SUBSYS1/aggregate_para1.nosuchmember > 3"));
        assertTrue(e.getMessage().contains("nosuchmember"));
    }

    @Test
    public void testParameterOrder() throws Exception {
        parser.parse(FLOAT + " > 1 && " + INT + " < 3 && " + FLOAT + " < 5");
        assertEquals(List.of(FLOAT, INT), parser.getParameters().stream().map(p -> p.getQualifiedName()).toList());
    }

    @Test
    public void testErrors() {
        assertThrows(ParseException.class, () -> parser.parse(""));
        assertThrows(ParseException.class, () -> parser.parse("/REFMDB/SUBSYS1/Unknown < 3"));
        assertThrows(ParseException.class, () -> parser.parse(INT + " < "));
        assertThrows(ParseException.class, () -> parser.parse(INT + " < 3 &&"));
        assertThrows(ParseException.class, () -> parser.parse("(" + INT + " < 3"));
        assertThrows(ParseException.class, () -> parser.parse(INT + " < \"abc"));
        assertThrows(ParseException.class, () -> parser.parse(INT + " < abc"));
        assertThrows(ParseException.class, () -> parser.parse(INT + " 3"));
    }

    private MatchResult evaluate(String expression, ParameterValue... values) throws ParseException {
        var expr = parser.parse(expression);
        var evaluator = MatchCriteriaEvaluatorFactory.getEvaluator(expr);
        var ctx = ProcessingContext.createForTestTm(values);
        return evaluator.evaluate(ctx);
    }

    private ParameterValue intValue(long v) {
        var pv = new ParameterValue(mdb.getParameter(INT));
        pv.setEngValue(ValueUtility.getUint32Value((int) v));
        return pv;
    }

    private ParameterValue floatValue(double v) {
        var pv = new ParameterValue(mdb.getParameter(FLOAT));
        pv.setEngValue(ValueUtility.getFloatValue((float) v));
        return pv;
    }
}
