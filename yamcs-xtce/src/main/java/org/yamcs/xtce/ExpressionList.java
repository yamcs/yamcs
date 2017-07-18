package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class does not have an equivalence in the XTCE standard
 * Created as a base-class for ORedConditions and ANDedConditions 
 * 
 * @author dho
 *
 */
public abstract class ExpressionList implements BooleanExpression {
    private static final long serialVersionUID = 2333657095062539096L;
    protected List<BooleanExpression> expressions = new ArrayList<>();	

    public void addConditionExpression(BooleanExpression cond) {
        expressions.add(cond);		
    }

    public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset=new HashSet<>();
        for(BooleanExpression c: expressions) {
            pset.addAll(c.getDependentParameters());
        }
        return pset;
    }

    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("ExpressionList: ");
        for(BooleanExpression exp: expressions) {
            sb.append(exp.toString()).append(" ");
        }
        return sb.toString();
    }
    
    public List<BooleanExpression> getExpressionList() {
        return expressions;
    }
}
