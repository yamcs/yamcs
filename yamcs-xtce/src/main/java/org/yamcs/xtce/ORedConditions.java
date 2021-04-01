package org.yamcs.xtce;

import java.util.stream.Collectors;

public class ORedConditions extends ExpressionList implements BooleanExpression {
    private static final long serialVersionUID = -971897455552714465L;

    @Override
    public MatchResult matches(CriteriaEvaluator evaluator) {
        MatchResult result = MatchResult.NOK;

        for (BooleanExpression exp : expressions) {
            MatchResult r = exp.matches(evaluator);
            if (r == MatchResult.OK) {
                result = r;
                break;
            } else if (r == MatchResult.UNDEF) {
                result = r;
                // continue checking maybe a expression will return OK
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
                    .collect(Collectors.joining(" or "));
        }
    }
}
