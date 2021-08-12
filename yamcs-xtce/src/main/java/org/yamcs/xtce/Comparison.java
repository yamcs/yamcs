package org.yamcs.xtce;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.yamcs.xtce.util.DataTypeUtil;

/**
 * This corresponds to XTCE Comparison or ArgumentComparison
 */
public class Comparison implements MatchCriteria {

    private static final long serialVersionUID = 10L;

    // only one of paraRef and argRef is set, the other is null
    private ParameterOrArgumentRef ref;

    private OperatorType comparisonOperator;

    String stringValue;

    /**
     * Makes a new comparison with a generic stringValue at this step the paraRef could be pointing to an unknown
     * parameter. resolveValueType can(should) be called later to create the correct value if it's not string
     * 
     * @param paraRef
     * @param stringValue
     * @param op
     */
    public Comparison(ParameterInstanceRef paraRef, String stringValue, OperatorType op) {
        if (stringValue == null) {
            throw new NullPointerException("stringValue");
        }
        this.ref = paraRef;
        this.stringValue = stringValue;
        this.comparisonOperator = op;

        checkParaRef(paraRef);
    }

    public Comparison(ArgumentInstanceRef argRef, String stringValue, OperatorType op) {
        if (stringValue == null) {
            throw new NullPointerException("stringValue");
        }
        this.ref = argRef;
        this.stringValue = stringValue;
        this.comparisonOperator = op;
    }

    private void checkParaRef(ParameterInstanceRef paraRef) {
        if (paraRef.getInstance() != 0) {
            throw new UnsupportedOperationException("Condition on parameter values from history are not supported");
        }
    }

    /**
     * Called when the type of the parameter used for comparison is known, so we have to find the value from stringValue
     * that we can compare to it
     */
    public void validateValueType() {

        boolean useCalibratedValue = ref.useCalibratedValue;
        DataType dtype = ref.getDataType();

        if (dtype instanceof AggregateDataType) {
            if (ref.getMemberPath() == null) {
                throw new IllegalArgumentException(
                        "Reference to an aggregate parameter type " + dtype.getName() + " without speciyfing the path");
            }
            DataType dtype1 = DataTypeUtil.getMemberType(dtype, ref.getMemberPath());
            if (dtype1 == null) {
                throw new IllegalArgumentException("reference " + PathElement.pathToString(ref.getMemberPath())
                        + " points to a nonexistent member inside the parameter type " + dtype.getName());
            }
            dtype = dtype1;
        }
        try {
            if (useCalibratedValue) {
                dtype.convertType(stringValue);
            } else {
                dtype.parseStringForRawValue(stringValue);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot parse value required for comparing with "
                    + ref.getName() + ": " + e.getMessage(), e);
        }
    }

    public ParameterOrArgumentRef getRef() {
        return ref;
    }

    public OperatorType getComparisonOperator() {
        return comparisonOperator;
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        if (ref instanceof ParameterInstanceRef) {
            Set<Parameter> pset = new HashSet<>();
            pset.add(((ParameterInstanceRef) ref).getParameter());
            return pset;
        } else {
            return Collections.emptySet();
        }

    }

    public String getStringValue() {
        return stringValue;
    }

    @Override
    public String toString() {
        return "Comparison: " + ref +
                comparisonOperator + stringValue;
    }
}
