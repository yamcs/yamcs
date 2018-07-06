package org.yamcs.xtce;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.util.DataTypeUtil;

/**
 * A simple ParameterInstanceRef to value comparison.
 * DIFFERS_FROM_XTCE:
 * 1) in xtce the value is stored as a string and it's not very clear how it's compared with an integer
 * 2) in xtce Comparison extends ParameterInstanceRef, and MatchCriteria is a choice of Comparison, ComparisonList, ...
 */
public class Comparison implements MatchCriteria {
    private static final long serialVersionUID = 9L;
    ParameterInstanceRef instanceRef;

    OperatorType comparisonOperator;

    // the string is used to create the object and then is changed to the other type, depending on the valueType
    String stringValue;

    private Object value;

    transient static Logger log = LoggerFactory.getLogger(Comparison.class.getName());

    /**
     * Makes a new comparison with a generic stringValue
     * at this step the paraRef could be pointing to an unknown parameter.
     * resolveValueType can(should) be called later to create the correct value if it's not string
     * 
     * @param paraRef
     * @param stringValue
     * @param op
     */
    public Comparison(ParameterInstanceRef paraRef, String stringValue, OperatorType op) {
        if(stringValue==null) {
            throw new NullPointerException("stringValue");
        }
        this.instanceRef = paraRef;
        this.stringValue = stringValue;
        this.value = stringValue;
        this.comparisonOperator = op;
        
        checkParaRef(paraRef);
    }

    public Comparison(ParameterInstanceRef paraRef, int intValue, OperatorType op) {
        this.instanceRef = paraRef;
        this.value = intValue;
        this.stringValue = Integer.toString(intValue);
        this.comparisonOperator = op;
        checkParaRef(paraRef);
    }

    public Comparison(ParameterInstanceRef paraRef, long longValue, OperatorType op) {
        this.instanceRef = paraRef;
        this.value = longValue;
        this.stringValue = Long.toString(longValue);
        this.comparisonOperator = op;
        checkParaRef(paraRef);
    }
    public Comparison(ParameterInstanceRef paraRef, double doubleValue, OperatorType op) {
        this.instanceRef = paraRef;
        this.value = doubleValue;
        this.stringValue = Double.toString(doubleValue);
        this.comparisonOperator = op;
        checkParaRef(paraRef);
    }

    private void checkParaRef(ParameterInstanceRef paraRef) {
        if(paraRef.getInstance()!=0) {
            throw new UnsupportedOperationException("Condition on parameter values from history are not supported");
        }
    }
    @Override
    public boolean isMet(CriteriaEvaluator evaluator) {
        return evaluator.evaluate(comparisonOperator, instanceRef, value);
    }

    /**
     * Called when the type of the parameter used for comparison is known,
     * so we have to find the value from stringValue that we can compare to it
     */
    public void resolveValueType() {
        boolean useCalibratedValue = instanceRef.useCalibratedValue();
        ParameterType ptype = instanceRef.getParameter().getParameterType();
        
        if(ptype instanceof AggregateParameterType) {
            ParameterType ptype1  = (ParameterType) DataTypeUtil.getMemberType(ptype, instanceRef.getMemberPath());
            if(ptype1 == null) {
                throw new IllegalArgumentException("reference "+PathElement.pathToString(instanceRef.getMemberPath())
                        +" points to un unexisting member inside the parameter type "+ptype.getName());
            }
            ptype = ptype1;
        } 
        try {
            if (useCalibratedValue) {
                value = ptype.parseString(stringValue);
            } else {
                value = ptype.parseStringForRawValue(stringValue);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot parse value required for comparing with "
                    + instanceRef.getParameter().getName() + ": " + e.getMessage(), e);
        }
    }

    public ParameterInstanceRef getParameterRef() {
        return instanceRef;
    }

    public OperatorType getComparisonOperator() {
        return comparisonOperator;
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        Set<Parameter> pset = new HashSet<>();
        pset.add(instanceRef.getParameter());
        return pset;
    }

    public static String operatorToString(OperatorType op) {
        switch (op) {
        case EQUALITY: {
            return "==";
        }
        case INEQUALITY: {
            return "!=";
        }
        case LARGERTHAN: {
            return ">";
        }
        case LARGEROREQUALTHAN: {
            return ">=";
        }
        case SMALLERTHAN: {
            return "<";
        }
        case SMALLEROREQUALTHAN: {
            return "<=";
        }
        }
        return "unknown";
    }

    public static OperatorType stringToOperator(String s) {
        if ("==".equals(s)) {
            return OperatorType.EQUALITY;
        } else if ("!=".equals(s)) {
            return OperatorType.INEQUALITY;
        } else if (">".equals(s)) {
            return OperatorType.LARGERTHAN;
        } else if (">=".equals(s)) {
            return OperatorType.LARGEROREQUALTHAN;
        } else if ("<".equals(s)) {
            return OperatorType.SMALLERTHAN;
        } else if ("<=".equals(s)) {
            return OperatorType.SMALLEROREQUALTHAN;
        } else {
            log.warn("unknown operator type " + s);
        }
        return null;
    }

    public Parameter getParameter() {
        return instanceRef.getParameter();
    }

    public Object getValue() {
        return value;
    }

    public String getStringValue() {
        return stringValue;
    }

    @Override
    public String toString() {
        if (instanceRef.getParameter() != null) {
            return "Comparison: paraName(" + instanceRef.getParameter().getName()
                    + (instanceRef.useCalibratedValue() ? ".eng" : ".raw") + ")" +
                    operatorToString(comparisonOperator) + stringValue;
        } else {
            return "Comparison: paraName(unresolved)" +
                    operatorToString(comparisonOperator) + stringValue;
        }
    }
}
