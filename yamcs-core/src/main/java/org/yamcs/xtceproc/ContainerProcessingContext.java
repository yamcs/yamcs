package org.yamcs.xtceproc;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.XtceDb;

/**
 * Keeps track of where we are when processing a packet.
 * 
 * @author nm
 *
 */
public class ContainerProcessingContext {
    final ProcessorData pdata;
    final BitBuffer buffer;

    // Keeps track of the absolute offset of the container where the processing takes place.
    // Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container
    // with respect to the primary container where the processing started
    int containerAbsoluteByteOffset;

    Subscription subscription;
    ContainerProcessingResult result;
    ContainerProcessingOptions options;

    // if set to true, out of packet parameters will be silently ignored, otherwise an exception will be thrown

    public final SequenceContainerProcessor sequenceContainerProcessor;
    public final SequenceEntryProcessor sequenceEntryProcessor;
    public final DataEncodingDecoder dataEncodingProcessor;
    public final ValueProcessor valueProcessor;
    public final CriteriaEvaluator criteriaEvaluator;

    public ContainerProcessingContext(ProcessorData pdata, BitBuffer buffer, ContainerProcessingResult result,
            Subscription subscription, ContainerProcessingOptions options) {
        this.pdata = pdata;
        this.buffer = buffer;
        this.subscription = subscription;
        this.criteriaEvaluator = new CriteriaEvaluatorImpl(result.params, pdata.getLastValueCache());
        this.result = result;
        this.options = options;

        sequenceContainerProcessor = new SequenceContainerProcessor(this);
        sequenceEntryProcessor = new SequenceEntryProcessor(this);
        dataEncodingProcessor = new DataEncodingDecoder(this);
        valueProcessor = new ValueProcessor(this);
    }

    static class ContainerProcessingResult {
        ParameterValueList params = new ParameterValueList();
        List<ContainerExtractionResult> containers = new ArrayList<>();
        long acquisitionTime;
        long generationTime;
        ProcessingStatistics stats;
        long expireMillis = -1; // -1 means not defined

        public ContainerProcessingResult(long aquisitionTime, long generationTime, ProcessingStatistics stats) {
            this.acquisitionTime = aquisitionTime;
            this.generationTime = generationTime;
            this.stats = stats;
        }

    }

    /**
     * Finds a parameter instance (i.e. a value) for a parameter in the current context
     * 
     * It only returns a parameter if the instance status was {@link AcquisitionStatus#ACQUIRED)
     * 
     * @param pir
     * @return the value found or null if not value has been found
     */
    public Value getValue(ParameterInstanceRef pir) {
        Parameter p = pir.getParameter();
        ParameterValue pv = result.params.getLastInserted(p);
        if (pv == null) {
            return null;
        }
        if (pv.getAcquisitionStatus() != AcquisitionStatus.ACQUIRED) {
            return null;
        }

        return pir.useCalibratedValue() ? pv.getEngValue() : pv.getRawValue();
    }

    public XtceDb getXtceDb() {
        return pdata.getXtceDb();
    }
}
