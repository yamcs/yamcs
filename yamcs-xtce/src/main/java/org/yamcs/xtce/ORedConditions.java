package org.yamcs.xtce;

import java.util.stream.Collectors;

public class ORedConditions extends ExpressionList {
    private static final long serialVersionUID = -971897455552714465L;

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
