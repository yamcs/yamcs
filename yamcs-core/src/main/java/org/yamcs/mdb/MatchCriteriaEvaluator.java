package org.yamcs.mdb;

public interface MatchCriteriaEvaluator {

    public enum MatchResult {
        /**
         * condition matches
         */
        OK,
        /**
         * condition does not match
         */
        NOK,
        /**
         * matching cannot be determined because not all inputs are available
         */
        UNDEF;
    }

    MatchResult evaluate(ProcessingData input);

    public String toExpressionString();
}
