package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Set;
import org.slf4j.Logger;

public interface MatchCriteria extends Serializable {
    public Set<Parameter> getDependentParameters();
    boolean isMet(CriteriaEvaluator evaluator);
    
    /**
     * For debugging purpose
     * 
     * @param criteria
     */
    static public void printParsedMatchCriteria(Logger log, MatchCriteria criteria, String indent) {
    	if (criteria instanceof Comparison) {
    		log.debug(indent + criteria.toString());
    	} else if (criteria instanceof ComparisonList) {
    		log.debug(indent + "ComparisonList (");
    		for (Comparison c: ((ComparisonList)criteria).comparisons) {
    			log.debug(indent + "  " + c.toString());
    		}
    		log.debug(indent + ")");
    	} else if (criteria instanceof Condition) {
    		log.debug(indent + criteria.toString());
    	} else if (criteria instanceof ANDedConditions) {
    		log.debug(indent + "AND (");
    		for (MatchCriteria c: ((ExpressionList)criteria).expressions) {
    			printParsedMatchCriteria(log, c, indent + "  ");
    		}
    		log.debug(indent + ")");
    	} else if (criteria instanceof ORedConditions) {
    		log.debug(indent + "OR (");
    		for (MatchCriteria c: ((ExpressionList)criteria).expressions) {
    			printParsedMatchCriteria(log, c, indent + "  ");
    		}
    		log.debug(indent + ")");
    	}  
    }
}
