package org.yamcs.xtce;

import org.yamcs.xtce.MatchCriteria.MatchResult;

public interface CriteriaEvaluator {
    MatchResult evaluate(OperatorType op, Object lValueRef, Object rValueRef);
}
