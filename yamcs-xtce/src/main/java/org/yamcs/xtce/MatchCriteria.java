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
    		log.info(indent + criteria.toString());
    	} else if (criteria instanceof ComparisonList) {
    		log.info(indent + "ComparisonList (");
    		for (Comparison c: ((ComparisonList)criteria).comparisons) {
    			log.info(indent + "  " + c.toString());
    		}
    		log.info(indent + ")");
    	} else if (criteria instanceof Condition) {
    		log.info(indent + criteria.toString());
    	} else if (criteria instanceof ANDedConditions) {
    		log.info(indent + "AND (");
    		for (MatchCriteria c: ((ExpressionList)criteria).expressions) {
    			printParsedMatchCriteria(log, c, indent + "  ");
    		}
    		log.info(indent + ")");    		
    	} else if (criteria instanceof ORedConditions) {
    		log.info(indent + "OR (");
    		for (MatchCriteria c: ((ExpressionList)criteria).expressions) {
    			printParsedMatchCriteria(log, c, indent + "  ");
    		}
    		log.info(indent + ")");    		
    	}  
    }
}
