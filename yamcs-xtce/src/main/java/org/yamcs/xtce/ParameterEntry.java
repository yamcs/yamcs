package org.yamcs.xtce;

/**
 * An entry that is a single Parameter
 * 
 * @author nm
 *
 */
public class ParameterEntry extends SequenceEntry {
    private static final long serialVersionUID = 200805131551L;
    private Parameter parameter;

    /**
     * Constructor for parameter entry. The parameter to which it reffers will be set later with {@link #setParameter(Parameter)}
     * 
     * @param locationInContainerInBits
     * @param location
     * @param parameter
     */
    public ParameterEntry(int locationInContainerInBits, ReferenceLocationType location, Parameter parameter) {
        this(locationInContainerInBits, location);
        this.setParameter(parameter);
    }

    /**
     * Constructor for an unresolved ParameterEntry. The Parameter will come later via {@link #setParameter(Parameter)}
     * 
     * @param locationInContainerInBits
     * @param location
     */
    public ParameterEntry(int locationInContainerInBits, ReferenceLocationType location) {
        super(locationInContainerInBits, location);
    }

    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public String toString() {
        return "ParameterEntry position:" + getIndex() + ", container:" + container.getName() +
                " locationInContainer:" + getLocationInContainerInBits() + " from:" + getReferenceLocation() +
                ", " + parameter +
                ((getRepeatEntry() != null) ? ", repeatEntry: (" + getRepeatEntry() + ")" : "");
    }

}
