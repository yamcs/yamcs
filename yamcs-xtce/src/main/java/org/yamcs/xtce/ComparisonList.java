package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All comparisons must be true.
 * 
 * @author nm
 *
 */
public class ComparisonList implements MatchCriteria {
    
    private static final long serialVersionUID = 200805131551L;
    ArrayList<Comparison> comparisons = new ArrayList<>();

    public void addComparison(Comparison comparison) {
        comparisons.add(comparison);
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset = new HashSet<Parameter>();
        for (Comparison c : comparisons) {
            pset.addAll(c.getDependentParameters());
        }
        return pset;
    }
    
    @Override
    public boolean isMet(CriteriaEvaluator evaluator) {
        for(Comparison c:comparisons) {
            if (!c.isMet(evaluator)) {
            	return false;
            }
        }
        
        return true;
    }

    public List<Comparison> getComparisonList() {
        return comparisons;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ComparisonList: ");
        for (Comparison c : comparisons) {
            sb.append(c.toString()).append(" ");
        }
        return sb.toString();
    }
}
