package org.yamcs.xtce;

/**
 * Entry from a container that makes reference to another container.
 * This is different than container inheritance: here the small container is included in the big one and does not extend
 * it.
 *
 */
public class ContainerEntry extends SequenceEntry {
    private static final long serialVersionUID = 200706050737L;
    private SequenceContainer refContainer;

    public ContainerEntry(int locationInContainerInBits, ReferenceLocationType location,
            SequenceContainer refContainer) {
        this(locationInContainerInBits, location);
        this.setRefContainer(refContainer);
    }


    public ContainerEntry(int locationInContainerInBits, ReferenceLocationType location) {
        super(locationInContainerInBits, location);
    }

    @Override
    public String toString() {
        return "ContainerEntry position:" + getIndex() + ", locationInContainer: " + getLocationInContainerInBits()
                + " from " + getReferenceLocation() +
                ", refContainer: " + getRefContainer().getName()
                + ((getRepeatEntry() != null) ? ", repeatEntry: (" + getRepeatEntry() + ")" : "");
    }

    public void setRefContainer(SequenceContainer refContainer) {
        this.refContainer = refContainer;
    }

    public SequenceContainer getRefContainer() {
        return refContainer;
    }

}
