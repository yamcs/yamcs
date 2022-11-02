package org.yamcs.xtce;

import java.io.Serializable;
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

    static String printExpressionReference(ParameterOrArgumentRef ref) {
        String fullName = ref.getName();
        PathElement[] path = ref.getMemberPath();
        if (path != null) {
            for (PathElement el : path) {
                if (el.getName() != null) {
                    fullName += ".";
                }
                fullName += el;
            }
        }

        if (!ref.useCalibratedValue()) {
            return "'raw://" + fullName + "'";
        } else {
            return "'" + fullName + "'";
        }
    }

    static String printExpressionValue(Object value) {
        if (value instanceof String) {
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
}
