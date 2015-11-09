package org.yamcs.xtce;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The Condition is XTCE overlaps with the Comparison. Condition allows two operands to be Parameters
 * 
 * @author dho
 *
 */
public class Condition implements BooleanExpression {	
	private static final long serialVersionUID = 1L;
	
	ParameterInstanceRef lValueRef = null;
    Object rValueRef = null;
    
    OperatorType comparisonOperator;

    //the string is used to create the object and then is changed to the other type, depending on the valueType
    String stringValue = null;    
    
    transient static Logger LOG=LoggerFactory.getLogger(Condition.class.getName());    

    public Condition(OperatorType comparisonOperator, ParameterInstanceRef lValueRef, ParameterInstanceRef rValueRef) {
		super();
		this.lValueRef = lValueRef;
		this.comparisonOperator = comparisonOperator;
		this.rValueRef = rValueRef;
		this.stringValue = null;
	}

	public Condition(OperatorType comparisonOperator, ParameterInstanceRef lValueRef, String stringValue) {
		super();
		this.lValueRef = lValueRef;
		this.comparisonOperator = comparisonOperator;
		this.stringValue = stringValue;
		this.rValueRef = null;
	}
		
	/**
     * Called when the type of the parameter used for comparison is known, 
     * so we have to find the value from stringValue that we can compare to it 
     */
    public void resolveValueType() {
    	if (((rValueRef == null) || (!(rValueRef instanceof ParameterInstanceRef))) && (stringValue != null)) {
            boolean useCalibratedValue = lValueRef.useCalibratedValue();
            ParameterType ptype = lValueRef.getParameter().getParameterType();
            if(useCalibratedValue) {
                rValueRef = ptype.parseString(stringValue);
            } else {
                rValueRef = ptype.parseStringForRawValue(stringValue);
            }    		
    	} else {
    		LOG.error("Cannot resolveValueType, inconsistent state");
    	}
    }

	@Override
	public boolean isMet(CriteriaEvaluator evaluator) {
		return evaluator.evaluate(comparisonOperator, lValueRef, rValueRef);
	}
	
    @Override
	public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset=new HashSet<Parameter>();
        
        pset.add(lValueRef.getParameter());
        if (rValueRef instanceof ParameterInstanceRef) {
        	pset.add(((ParameterInstanceRef)rValueRef).getParameter());
        }
        return pset;
    }	

    @Override
    public String toString() {
    	String rValue = stringValue; 
    	if (stringValue == null) {
    		if (((ParameterInstanceRef)rValueRef).getParameter() == null) {
    			rValue = "paraName(unresolved)";
    		} else {
    			rValue = "paramName(" + ((ParameterInstanceRef)rValueRef).getParameter().getName() + ")";
    		}
    	} 
    	
    	String lValue = "paraName(unresolved)";
    	if (lValueRef.getParameter() != null) {
    		lValue = "paraName(" + lValueRef.getParameter().getName() + ")";
    	}
    	
		return "Condition: " + lValue + OperatorType.operatorToString(comparisonOperator) + rValue;
    	
    }        
}
