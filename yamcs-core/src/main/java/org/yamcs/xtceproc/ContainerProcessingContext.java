package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.CriteriaEvaluator;


/**
 * Keeps track of where we are when processing a packet.
 * @author nm
 *
 */
public class ContainerProcessingContext {	
    final ProcessorData pdata;
    final ContainerProcessingPosition position;
    
    //Keeps track of the absolute offset of the container where the processing takes place. 
    //Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container with respect to the primary container where the processing started 
    int containerAbsoluteByteOffset; 	

    Subscription subscription;
    ContainerProcessingResult result;

    //if set to true, out of packet parameters will be silently ignored, otherwise an exception will be thrown
    final boolean ignoreOutOfContainerEntries;

    public final SequenceContainerProcessor sequenceContainerProcessor=new SequenceContainerProcessor(this);
    public final SequenceEntryProcessor sequenceEntryProcessor=new SequenceEntryProcessor(this);
    public final DataEncodingDecoder dataEncodingProcessor=new DataEncodingDecoder(this);
    public final ValueProcessor valueProcessor=new ValueProcessor(this);
    public final CriteriaEvaluator criteriaEvaluator;

    public ContainerProcessingContext(ProcessorData pdata, ContainerProcessingPosition position, ContainerProcessingResult result, Subscription subscription, 
            boolean ignoreOutOfContainerEntries) {
        this.pdata = pdata;
        this.position = position;
        this.subscription = subscription;
        this.criteriaEvaluator = new CriteriaEvaluatorImpl(result.params);
        this.ignoreOutOfContainerEntries = ignoreOutOfContainerEntries;
        this.result = result;
    }
    
    static class ContainerProcessingPosition {
        ByteBuffer bb;
        int containerAbsoluteByteOffset;
        int bitPosition;
        public ContainerProcessingPosition(ByteBuffer bb, int containerAbsoluteByteOffset, int bitPosition) {
            this.bb = bb;
            this.containerAbsoluteByteOffset = containerAbsoluteByteOffset;
            this.bitPosition = bitPosition;
        }
    }
    
    static class ContainerProcessingResult {
        ParameterValueList params = new ParameterValueList();
        List<ContainerExtractionResult> containers = new ArrayList<>();
        long acquisitionTime;
        long generationTime;
        ProcessingStatistics stats;
        long expirationTime = TimeEncoding.INVALID_INSTANT      ;
        public ContainerProcessingResult(long aquisitionTime, long generationTime, ProcessingStatistics stats) {
            this.acquisitionTime = aquisitionTime;
            this.generationTime = generationTime;
            this.stats = stats;
        }
      
    }
}
