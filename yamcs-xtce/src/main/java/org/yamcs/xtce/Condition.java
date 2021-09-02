package org.yamcs.xtce;

import java.util.HashSet;
import java.util.Set;

/**
 * The Condition is XTCE overlaps with the Comparison. Condition allows two operands to be Parameters
 * 
 * @author dho
 *
 */
public class Condition implements BooleanExpression {

    private static final long serialVersionUID = 2L;

    // ParameterInstanceRef or ArgumentInstanceRef
    ParameterOrArgumentRef leftRef;

    // Only one of these two can be set
    ParameterOrArgumentRef rightRef;
    String rightValue;

    OperatorType comparisonOperator;

    public Condition(OperatorType comparisonOperator, ParameterOrArgumentRef leftRef, ParameterOrArgumentRef rightRef) {
        super();

        this.leftRef = leftRef;
        this.comparisonOperator = comparisonOperator;
        this.rightRef = rightRef;
    }

    public Condition(OperatorType comparisonOperator, ParameterOrArgumentRef leftRef, String rightValue) {
        super();

        this.leftRef = leftRef;
        this.comparisonOperator = comparisonOperator;
        this.rightValue = rightValue;
    }

    /**
     * If the type of the parameter used for comparison is known, can parse the stringValue to see if it can be compared
     * with the type
     */
    public void validateValueType() {
        if (rightValue != null) {
            DataType ptype = leftRef.getDataType();
            if (ptype != null) {
                if (leftRef.useCalibratedValue()) {
                    ptype.convertType(rightValue);
                } else {
                    ptype.parseStringForRawValue(rightValue);
                }
            }
        }
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset = new HashSet<>();
        if (leftRef instanceof ParameterInstanceRef) {
            ParameterInstanceRef pref = ((ParameterInstanceRef) leftRef);
            pset.add(pref.getParameter());
        }
        if (rightRef instanceof ParameterInstanceRef) {
            ParameterInstanceRef pref = ((ParameterInstanceRef) rightRef);
            pset.add(pref.getParameter());
        }

        return pset;
    }

    @Override
    public String toString() {
        return "Condition: " + leftRef + comparisonOperator + (rightValue == null ? rightRef : rightValue);

    }

    public ParameterOrArgumentRef getLeftRef() {
        return leftRef;
    }

    public ParameterOrArgumentRef getRightRef() {
        return rightRef;
    }

    public OperatorType getComparisonOperator() {
        return comparisonOperator;
    }

    public String getRightValue() {
        return rightValue;
    }

}
