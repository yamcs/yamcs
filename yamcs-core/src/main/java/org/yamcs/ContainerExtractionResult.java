package org.yamcs;

import org.yamcs.xtce.SequenceContainer;

/**
 * Holds the definition of a container, the content of its slice and some positioning information inside that slice
 */
public class ContainerExtractionResult {
    private final SequenceContainer container;
    private final byte[] containerContent;
    private final int offset;
    private final int bitPosition;

    private final long acquisitionTime;
    private final long generationTime;
    private final int seqCount;

    final boolean derivedFromRoot;

    public ContainerExtractionResult(SequenceContainer container,
            byte[] containerContent,
            int offset,
            int bitPosition,
            long acquisitionTime,
            long generationTime,
            int seqCount,
            boolean derivedFromRoot) {
        this.container = container;
        this.containerContent = containerContent;
        this.offset = offset;
        this.bitPosition = bitPosition;
        this.acquisitionTime = acquisitionTime;
        this.generationTime = generationTime;
        this.seqCount = seqCount;
        this.derivedFromRoot = derivedFromRoot;
    }

    public SequenceContainer getContainer() {
        return container;
    }

    public byte[] getContainerContent() {
        return containerContent;
    }

    /**
     * @return the position in bits where the entries defined in this container start
     */
    public int getLocationInContainerInBits() {
        return offset * 8 + bitPosition;
    }

    /**
     * 
     * @return the position in bytes where this container including parent hierarchy starts
     */
    public int getOffset() {
        return offset;
    }

    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    public long getGenerationTime() {
        return generationTime;
    }

    /**
     * 
     * Return true if this inherits (directly or indirectly) from the root container.
     * <p>
     * Return false if this does not inherit from the root container - that means is obtained through container
     * composition rather than inheritance
     */
    public boolean isDerivedFromRoot() {
        return derivedFromRoot;
    }

    public int getSeqCount() {
        return seqCount;
    }

    @Override
    public String toString() {
        return container.toString();
    }


}
