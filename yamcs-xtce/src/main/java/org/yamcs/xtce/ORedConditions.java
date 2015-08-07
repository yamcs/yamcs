package org.yamcs.xtce;

public class ORedConditions extends ExpressionList implements BooleanExpression {
	private static final long serialVersionUID = -971897455552714465L;

	@Override
	public boolean isMet(CriteriaEvaluator evaluator) {
		for (BooleanExpression exp: expressions) {
			if (exp.isMet(evaluator)) {				
				return true;
			}
		}
		return false;
	}
}
