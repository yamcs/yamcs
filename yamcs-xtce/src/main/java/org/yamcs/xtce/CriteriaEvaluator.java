package org.yamcs.xtce;

public interface CriteriaEvaluator {
	boolean evaluate(OperatorType op, Object lValueRef, Object rValueRef);
}
