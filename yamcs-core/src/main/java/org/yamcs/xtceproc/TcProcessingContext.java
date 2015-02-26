package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.ParameterValue;


/**
 * Keeps track of where we are when filling in the bits and bytes of a command
 * @author nm
 *
 */
public class TcProcessingContext {
	
	ByteBuffer bb;
	public int bitPosition;
	
	//Keeps track of the absolute offset of the container where the processing takes place. 
	//Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container with respect to the primary container where the processing started 
	int containerAbsoluteByteOffset; 	
	
	Subscription subscription;
	
	//this is the result of the processing
	public ArrayList<ParameterValue> paramResult;
	public ArrayList<ContainerExtractionResult> containerResult;
	
	public long acquisitionTime;
	public long generationTime;
	ProcessingStatistics stats;
	
	public SequenceContainerProcessor sequenceContainerProcessor=new SequenceContainerProcessor(this);
	public SequenceEntryProcessor sequenceEntryProcessor=new SequenceEntryProcessor(this);
	public ParameterTypeProcessor parameterTypeProcessor=new ParameterTypeProcessor(this);
	public DataEncodingProcessor dataEncodingProcessor=new DataEncodingProcessor(this);
	public ValueProcessor valueProcessor=new ValueProcessor(this);
	public ComparisonProcessor comparisonProcessor;
	
	public TcProcessingContext(ByteBuffer bb, int containerAbsoluteByteOffset, int bitPosition, Subscription subscription, 
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
		this.comparisonProcessor=new ComparisonProcessor(paramResult);
	}
}
