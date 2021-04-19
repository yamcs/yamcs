package org.yamcs.xtceproc;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
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
    final ProcessorData proccessingData;
    final BitBuffer buffer;

    // Keeps track of the absolute offset of the container where the processing takes place.
    // Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container
    // with respect to the primary container where the processing started
    int containerAbsoluteByteOffset;

    final Subscription subscription;
    final ContainerProcessingResult result;
    final ContainerProcessingOptions options;

    public final SequenceContainerProcessor sequenceContainerProcessor;
    public final SequenceEntryProcessor sequenceEntryProcessor;
    public final DataEncodingDecoder dataEncodingProcessor;
    public final ValueProcessor valueProcessor;
    public boolean provideContainerResult = true;

    public ContainerProcessingContext(ProcessorData pdata, BitBuffer buffer, ContainerProcessingResult result,
            Subscription subscription, ContainerProcessingOptions options) {
        this.proccessingData = pdata;
        this.buffer = buffer;
        this.subscription = subscription;
        this.result = result;
        this.options = pdata.getProcessorConfig().getContainerProcessingOptions();

        sequenceContainerProcessor = new SequenceContainerProcessor(this);
        sequenceEntryProcessor = new SequenceEntryProcessor(this);
        dataEncodingProcessor = new DataEncodingDecoder(this);
        valueProcessor = new ValueProcessor(this);
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
        // TBD maybe we should make this configurable
        // allowOld = true means that processing parameters in this packet can depend on parameters not part of the
        // packet - not a good idea but some people use that. Yamcs wasn't able to use old values but now it is
        // able.
        boolean allowOld = false;
        ParameterValue pv = result.getTmParameterInstance(p, pir.getInstance(), allowOld);
        if (pv == null) {
            return null;
        }
        if (pv.getAcquisitionStatus() != AcquisitionStatus.ACQUIRED) {
            return null;
        }

        return pir.useCalibratedValue() ? pv.getEngValue() : pv.getRawValue();
    }

    public XtceDb getXtceDb() {
        return proccessingData.getXtceDb();
    }

    public ProcessorData getProcessorData() {
        return proccessingData;
    }
}
