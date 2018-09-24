package org.yamcs.xtce;

/**
 * An entry that is a single Argument
 * 
 * @author nm
 *
 */
public class ArgumentEntry extends SequenceEntry {
    private static final long serialVersionUID = 1L;
    private Argument argument;

    public ArgumentEntry(int locationInContainerInBits, ReferenceLocationType location, Argument argument) {
        super(locationInContainerInBits, location);
        this.argument = argument;
    }

    /**
     * Constructor for an unresolved ArgumentEntry. The Argument will come later via {@link #setArgument(Argument)}
     * 
     * @param locationInContainerInBits
     * @param location
     */
    public ArgumentEntry(int locationInContainerInBits, ReferenceLocationType location) {
        super(locationInContainerInBits, location);
    }

    public ArgumentEntry(Argument arg) {
        this.argument = arg;
    }

    public void setArgument(Argument argument) {
        this.argument = argument;
    }

    public Argument getArgument() {
        return argument;
    }

    @Override
    public String toString() {
        return "ArgumentEntry position:" + getIndex() + ", container:" + container.getName() +
                " locationInContainer:" + getLocationInContainerInBits() + " from:" + getReferenceLocation() +
                ", argument: " + argument;

    }
}
