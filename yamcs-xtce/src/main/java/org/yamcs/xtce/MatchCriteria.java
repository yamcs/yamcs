package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

public interface MatchCriteria extends Serializable {

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
         * matching cannot be determined because not all inputs are availalbe
         */
        UNDEF;
    }
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
    default boolean isMet(CriteriaEvaluator evaluator) {
        return matches(evaluator) == MatchResult.OK;
    }

    MatchResult matches(CriteriaEvaluator evaluator);

    public String toExpressionString();

    static String printExpressionReference(ParameterInstanceRef ref) {
        if (!ref.useCalibratedValue()) {
            return "'raw://" + ref.getParameter().getQualifiedName() + "'";
        } else {
            return "'" + ref.getParameter().getQualifiedName() + "'";
        }
    }

    static String printExpressionValue(Object value) {
        if (value != null && value instanceof String) {
            // Need to allow for quotes and slashes within the string itself
            // Turn '\' into '\\' and next, '"' into '\"'
            String escaped = ((String) value).replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        } else {
            return String.valueOf(value);
        }
    }

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
        public MatchResult matches(CriteriaEvaluator evaluator) {
            return MatchResult.OK;
        }

        @Override
        public Set<Parameter> getDependentParameters() {
            return Collections.emptySet();
        }

        @Override
        public String toExpressionString() {
            return "true";
        }
    };
}
