package org.yamcs.parameter;

import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceEntry;

/**
 * A parameter value corresponding to a parameter that has been extracted from a container.
 *  
 * It contains the position in the container where it has been extracted from.
 * @author nm
 *
 */
public class ContainerParameterValue extends ParameterValue {
    SequenceEntry entry;
    int absoluteBitOffset, bitSize;

    public ContainerParameterValue(Parameter def) {
        super(def);
    }
    public ContainerParameterValue(ContainerParameterValue cpv) {
        super(cpv);
        this.entry = cpv.entry;
        this.absoluteBitOffset = cpv.absoluteBitOffset;
        this.bitSize = cpv.bitSize;
    }
    
    public int getAbsoluteBitOffset() {
        return absoluteBitOffset;
    }

    public void setAbsoluteBitOffset(int absoluteBitOffset) {
        this.absoluteBitOffset = absoluteBitOffset;
    }

    public int getBitSize() {
        return bitSize;
    }

    public void setBitSize(int bitSize) {
        this.bitSize = bitSize;
    }

    public void setSequenceEntry(SequenceEntry entry) {
        this.entry = entry;
    }

    public SequenceEntry getSequenceEntry() {
        return entry;
    }

}
