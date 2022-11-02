package org.yamcs.xtce;

import java.util.List;

/**
 * Describe an entry that is an array parameter.
 * 
 * The size of the extracted array has to be specified in the {@code size} property which is a list of
 * {@link IntegerValue} that can be static or values of other parameters.
 * 
 * @author nm
 *
 */
public class ArrayParameterEntry extends ParameterEntry {
    private static final long serialVersionUID = 2L;
    
    public ArrayParameterEntry(int locationInContainerInBits, ReferenceLocationType location) {
        super(locationInContainerInBits, location);
    }
    
    public ArrayParameterEntry(int locationInContainerInBits, ReferenceLocationType location, Parameter parameter) {
        super(locationInContainerInBits, location, parameter);
    }
  

    private Parameter parameter;
    List<IntegerValue> dim;


    public void setParameter(Parameter parameter) {
        if (!(parameter.getParameterType() instanceof ArrayParameterType)) {
            throw new IllegalArgumentException("Array parameter type required for parameter " + parameter.getName());
        }
        this.parameter = parameter;
        if(dim==null) {
            ArrayParameterType pt = (ArrayParameterType)parameter.getParameterType();
            dim = pt.getSize();
        }
        validateSize();
    }

    /**
     * sets the sizes of the extracted array. The length of the list has to match the
     * {@link ArrayParameterType#getNumberOfDimensions()}
     * 
     * @throws IllegalArgumentException
     *             if the length of the list is not correct.
     */
    public void setSize(List<IntegerValue> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Dimension sizes cannot be empty");
        }
        this.dim = list;
        validateSize();
    }

    public List<IntegerValue> getSize() {
        return dim;
    }

    private void validateSize() {
        if (dim != null && parameter != null) {
            ArrayParameterType ptype = (ArrayParameterType) parameter.getParameterType();
            if (dim.size() != ptype.getNumberOfDimensions()) {
                throw new IllegalArgumentException(
                        "The numberOfDimensions of the parameter does not match the size length: "
                                + ptype.getNumberOfDimensions() + " vs " + dim.size());
            }
        }
    }

    /**
     * 
     * @return the parameter referenced by this array entry
     */
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public String toString() {
        return "ArrayParameterEntry position:" + getIndex() + ", container:" + container.getName() +
                " locationInContainer:" + getLocationInContainerInBits() + " from:" + getReferenceLocation() +
                ", " + parameter +
                ((getRepeatEntry() != null) ? ", repeatEntry: (" + getRepeatEntry() + ")" : "");
    }
}
