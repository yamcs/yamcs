package org.yamcs.xtce;

import static org.yamcs.xtce.MatchCriteria.printExpressionReference;
import static org.yamcs.xtce.MatchCriteria.printExpressionValue;

import java.util.HashSet;
import java.util.Set;

/**
 * The Condition is XTCE overlaps with the Comparison. Condition allows two operands to be Parameters
 * 
 * @author dho
 *
 */
public class Condition implements BooleanExpression {

    private static final long serialVersionUID = 1L;

    ParameterInstanceRef lValueRef;
    Object rValueRef;

    OperatorType comparisonOperator;

    // the string is used to create the object and then is changed to the other type, depending on the valueType
    String stringValue;

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
     * If the type of the parameter used for comparison is known, can parse the stringValue to see if it can be compared
     * with the type
     */
    public void validateValueType() {
        if (((rValueRef == null) || (!(rValueRef instanceof ParameterInstanceRef))) && (stringValue != null)) {
            ParameterType ptype = lValueRef.getParameter().getParameterType();
            if (ptype != null) {
                if (lValueRef.useCalibratedValue()) {
                    rValueRef = ptype.parseString(stringValue);
                } else {
                    rValueRef = ptype.parseStringForRawValue(stringValue);
                }
            }
        }
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset = new HashSet<>();

        pset.add(lValueRef.getParameter());
        if (rValueRef instanceof ParameterInstanceRef) {
            pset.add(((ParameterInstanceRef) rValueRef).getParameter());
        }
        return pset;
    }

    @Override
    public String toExpressionString() {
        StringBuilder buf = new StringBuilder();
        buf.append(printExpressionReference(lValueRef));
        buf.append(" ");
        buf.append(comparisonOperator);
        buf.append(" ");

        if (rValueRef instanceof ParameterInstanceRef) {
            buf.append(printExpressionReference((ParameterInstanceRef) rValueRef));
        } else {
            buf.append(printExpressionValue(rValueRef));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        String rValue = stringValue;
        if (stringValue == null) {
            if (((ParameterInstanceRef) rValueRef).getParameter() == null) {
                rValue = "paraName(unresolved)";
            } else {
                rValue = "paramName(" + ((ParameterInstanceRef) rValueRef).getParameter().getName() + ")";
            }
        }

        String lValue = "paraName(unresolved)";
        if (lValueRef.getParameter() != null) {
            lValue = "paraName(" + lValueRef.getParameter().getName() + ")";
        }

        return "Condition: " + lValue + comparisonOperator + rValue;

    }

    public ParameterInstanceRef getlValueRef() {
        return lValueRef;
    }

    /**
     * 
     * @return right value reference - could be itself an ParameterInstanceRef
     */
    public Object getrValueRef() {
        return rValueRef;
    }

    public OperatorType getComparisonOperator() {
        return comparisonOperator;
    }

    public String getStringValue() {
        return stringValue;
    }
}
