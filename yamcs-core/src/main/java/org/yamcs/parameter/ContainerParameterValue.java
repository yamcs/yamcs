package org.yamcs.parameter;

import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceEntry;

/**
 * A parameter value corresponding to a parameter that has been extracted from a container.
 *  
 * It contains the position in the container where it has been extracted from.
 *
 */
public class ContainerParameterValue extends ParameterValue {
    SequenceEntry entry;
    // the start of the container in the packet in bytes
    // this is the start of the top container in the hierarchy
    // this means it is normally 0 unless we have container composition (not inheritance!) and then it is the
    // byte offset where the sub-container appears in the containing container
    final int startOffset;
    // bit offset relative to the startOffset
    final int bitOffset;

    int bitSize;

    public ContainerParameterValue(Parameter def, int startOffset, int bitOffset) {
        super(def);
        this.startOffset = startOffset;
        this.bitOffset = bitOffset;
    }

    public ContainerParameterValue(ContainerParameterValue cpv) {
        super(cpv);
        this.entry = cpv.entry;
        this.bitOffset = cpv.bitOffset;
        this.startOffset = cpv.startOffset;
        this.bitSize = cpv.bitSize;
    }
    
    public int getAbsoluteBitOffset() {
        return startOffset * 8 + bitOffset;
    }

    /**
     * Returns the start of the byte offset of the container start in the packet. This is the start of the top
     * container in the hierarchy where entry.getContainer() belongs.
     * <p>
     * It is 0 unless we have container composition (not inheritance!) and then it is the
     * byte offset where the sub-container appears in the containing container
     */
    public int getContainerStartOffset() {
        return startOffset;
    }

    public int getBitSize() {
        return bitSize;
    }

    public void setBitSize(int bitSize) {
        this.bitSize = bitSize;
    }

    public SequenceEntry getSequenceEntry() {
        return entry;
    }

    public void setSequenceEntry(SequenceEntry entry) {
        this.entry = entry;
    }

}
