package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

public interface MatchCriteria extends Serializable {

    /**
     * Return the set of parameters which are required in order to evaluate the match criteria. If no parameter is
     * required, return an empty set.
     * 
     * @return
     */
    public Set<Parameter> getDependentParameters();

    /**
     * Return true if the condition matches
     * 
     * @param evaluator
     * @return
     */
    boolean isMet(CriteriaEvaluator evaluator);

    /**
     * For debugging purpose
     * 
     * @param criteria
     */
    static public void printParsedMatchCriteria(Logger log, MatchCriteria criteria, String indent) {
        if (criteria instanceof Comparison) {
            log.fine(indent + criteria.toString());
        } else if (criteria instanceof ComparisonList) {
            log.fine(indent + "ComparisonList (");
            for (Comparison c : ((ComparisonList) criteria).comparisons) {
                log.fine(indent + "  " + c.toString());
            }
            log.fine(indent + ")");
        } else if (criteria instanceof Condition) {
            log.fine(indent + criteria.toString());
        } else if (criteria instanceof ANDedConditions) {
            log.fine(indent + "AND (");
            for (MatchCriteria c : ((ExpressionList) criteria).expressions) {
                printParsedMatchCriteria(log, c, indent + "  ");
            }
            log.fine(indent + ")");
        } else if (criteria instanceof ORedConditions) {
            log.fine(indent + "OR (");
            for (MatchCriteria c : ((ExpressionList) criteria).expressions) {
                printParsedMatchCriteria(log, c, indent + "  ");
            }
            log.fine(indent + ")");
        }
    }

    public static final MatchCriteria ALWAYS_MATCH = new MatchCriteria() {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean isMet(CriteriaEvaluator evaluator) {
            return true;
        }

        @Override
        public Set<Parameter> getDependentParameters() {
            return Collections.emptySet();
        }
    };
}
