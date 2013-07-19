package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.ParameterValue;


/**
 * Keeps track of where we are when processing a packet.
 * @author nm
 *
 */
public class ProcessingContext {
	
	ByteBuffer bb;
	int bitPosition;
	int containerAbsoluteByteOffset; //keeps track of the absolute offset of the container where the processing takes place. Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container with respect to the primary container where the processing started 
	Subscription subscription;
	
	//this is the result of the processing
	public ArrayList<ParameterValue> paramResult;
	public ArrayList<ContainerExtractionResult> containerResult;
	
	public long acquisitionTime;
	public long generationTime;
	ProcessingStatistics stats;
	
	SequenceContainerProcessor sequenceContainerProcessor=new SequenceContainerProcessor(this);
	SequenceEntryProcessor sequenceEntryProcessor=new SequenceEntryProcessor(this);
	ParameterTypeProcessor parameterTypeProcessor=new ParameterTypeProcessor(this);
	DataEncodingProcessor dataEncodingProcessor=new DataEncodingProcessor(this);
	ValueProcessor valueProcessor=new ValueProcessor(this);
	ComparisonProcessor comparisonProcessor=new ComparisonProcessor(this);
	
	public ProcessingContext(ByteBuffer bb, int containerAbsoluteByteOffset, int bitPosition, Subscription subscription, 
	        ArrayList<ParameterValue> params, ArrayList<ContainerExtractionResult> containers, 
	        long acquisitionTime, long generationTime, ProcessingStatistics stats) {
		this.bb = bb;
		this.containerAbsoluteByteOffset=containerAbsoluteByteOffset;
		this.bitPosition = bitPosition;
		this.subscription = subscription;
		this.paramResult = params;
		this.containerResult=containers;
		this.acquisitionTime = acquisitionTime;
		this.generationTime = generationTime;
		this.stats = stats;
	}
}
