package org.yamcs.xtce;

import java.util.stream.Collectors;

public class ANDedConditions extends ExpressionList {
    private static final long serialVersionUID = 6301730763127090210L;


    @Override
    public MatchResult matches(CriteriaEvaluator evaluator) {
        MatchResult result = MatchResult.OK;

        for (BooleanExpression exp : expressions) {
            MatchResult r = exp.matches(evaluator);
            if (r == MatchResult.NOK) {
                result = r;
                break;
            } else if (r == MatchResult.UNDEF) {
                result = r;
                // continue checking maybe a comparison will return NOK
            }
        }
        return result;
    }

    @Override
    public String toExpressionString() {
        if (expressions.size() == 1) {
            return expressions.get(0).toExpressionString();
        } else {
            return expressions.stream()
                    .map(expr -> "(" + expr.toExpressionString() + ")")
                    .collect(Collectors.joining(" and "));
        }
    }
}
