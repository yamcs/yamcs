package org.yamcs.xtce;

import java.util.List;

/**
 * An abstract block of data; used as the base type for more specific container types
 * 
 * @author nm
 *
 */
public abstract class Container extends NameDescription {
    private static final long serialVersionUID = 200706051148L;

    /*
     * DIFFERS_FROM_XTCE XTCE specifies the size of the container in the BinaryDataEncoding
     * 
     * Yamcs uses it only for telemetry when dealing with containers that are part of other containers, to speed up the processing of the
     * parameter subsequent to this container.
     * If specified, means that this container will ALWAYS have this size
     */
    protected int sizeInBits = -1;

    protected Container baseContainer;

    protected MatchCriteria restrictionCriteria;
    // expected rate
    RateInStream rate = null;

    Container(String name) {
        super(name);
    }

    public void setSizeInBits(int sizeInBits) {
        this.sizeInBits = sizeInBits;
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    public void setRateInStream(RateInStream r) {
        this.rate = r;
    }

    public RateInStream getRateInStream() {
        return rate;
    }

    public abstract void addEntry(SequenceEntry se);

    public abstract List<SequenceEntry> getEntryList();

    public void setBaseContainer(Container baseContainer) {
        this.baseContainer = baseContainer;
    }

    public Container getBaseContainer() {
        return baseContainer;
    }

    public void setRestrictionCriteria(MatchCriteria restrictionCriteria) {
        this.restrictionCriteria = restrictionCriteria;
    }

    /**
     * restriction criteria related to inheritance from the base container
     * 
     * @return
     */
    public MatchCriteria getRestrictionCriteria() {
        return restrictionCriteria;
    }

}
