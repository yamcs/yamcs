package org.yamcs.xtce;

import java.util.stream.Collectors;

public class ORedConditions extends ExpressionList implements BooleanExpression {
    private static final long serialVersionUID = -971897455552714465L;

    @Override
    public boolean isMet(CriteriaEvaluator evaluator) {
        for (BooleanExpression exp : expressions) {
            if (exp.isMet(evaluator)) {
                return true;
            }
        }
        return false;
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
