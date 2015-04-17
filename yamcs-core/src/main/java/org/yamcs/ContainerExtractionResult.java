package org.yamcs;

import java.nio.ByteBuffer;

import org.yamcs.xtce.SequenceContainer;

/**
 * Holds the definition of a container, the content of its slice and some positioning
 * information inside that slice
 */
public class ContainerExtractionResult {

    private SequenceContainer container;
    private ByteBuffer containerContent;
    private int locationInContainerInBits;
    
    private long acquisitionTime;
    private long generationTime;
	
    public ContainerExtractionResult(SequenceContainer container,
                    ByteBuffer containerContent,
                    int locationInContainerInBits,
                    long acquisitionTime,
                    long generationTime) {
		this.container = container;
		this.containerContent = containerContent;
		this.locationInContainerInBits = locationInContainerInBits;
		this.acquisitionTime = acquisitionTime;
		this.generationTime = generationTime;
		
		containerContent.position(0);
	}

	public SequenceContainer getContainer() {
		return container;
	}
	
	public ByteBuffer getContainerContent() {
	    return containerContent;
	}
	
	public int getLocationInContainerInBits() {
	    return locationInContainerInBits;
	}
	
	public long getAcquisitionTime() {
	    return acquisitionTime;
	}
	
	public long getGenerationTime() {
	    return generationTime;
	}
	
	@Override
	public String toString() {
	    return container.toString();
	}
}
