package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.xtce.MatchCriteria.MatchResult;

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
        Set<Parameter> pset = new HashSet<>();
        for (Comparison c : comparisons) {
            pset.addAll(c.getDependentParameters());
        }
        return pset;
    }

    @Override
    public MatchResult matches(CriteriaEvaluator evaluator) {
        MatchResult result = MatchResult.OK;

        for (Comparison c : comparisons) {
            MatchResult r = c.matches(evaluator);
            if (r == MatchResult.NOK) {
                result = r;
                break;
            } else if (r == MatchResult.UNDEF) {
                result = r;
                // continue checking maybe a comparison will return NOK
            }
        }

        return result;
    }

    public List<Comparison> getComparisonList() {
        return comparisons;
    }

    @Override
    public String toExpressionString() {
        return comparisons.stream()
                .map(Comparison::toExpressionString)
                .collect(Collectors.joining(" and "));
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
