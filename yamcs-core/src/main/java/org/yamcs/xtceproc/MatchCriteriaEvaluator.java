package org.yamcs.xtceproc;


import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.MatchCriteria.MatchResult;

public interface MatchCriteriaEvaluator {
    abstract MatchResult evaluate(ParameterValueList pvlist, LastValueCache lastValueCache);
}