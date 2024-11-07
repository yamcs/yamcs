package org.yamcs.mdb;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.SequenceEntry;

/**
 * Keeps track of where we are when processing a packet.
 * <p>
 * One object is used for all containers deriving in a hierarchy
 */
public class ContainerProcessingContext {
    final ProcessorData proccessorData;
    final BitBuffer buffer;

    final Subscription subscription;
    final ContainerProcessingResult result;
    final ContainerProcessingOptions options;

    public final SequenceContainerProcessor sequenceContainerProcessor;
    public final SequenceEntryProcessor sequenceEntryProcessor;
    public final DataEncodingDecoder dataEncodingProcessor;
    public boolean provideContainerResult = true;
    public final boolean derivedFromRoot;

    SequenceEntry currentEntry;

    public ContainerProcessingContext(ProcessorData pdata, BitBuffer buffer, ContainerProcessingResult result,
            Subscription subscription, ContainerProcessingOptions options, boolean derivedFromRoot) {
        this.proccessorData = pdata;
        this.buffer = buffer;
        this.subscription = subscription;
        this.result = result;
        this.options = options;
        this.derivedFromRoot = derivedFromRoot;

        sequenceContainerProcessor = new SequenceContainerProcessor(this);
        sequenceEntryProcessor = new SequenceEntryProcessor(this);
        dataEncodingProcessor = new DataEncodingDecoder(this);
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
        ParameterValue pv = result.getParameterInstance(pir);
        if (pv == null) {
            return null;
        }
        if (pv.getAcquisitionStatus() != AcquisitionStatus.ACQUIRED) {
            return null;
        }

        return pir.useCalibratedValue() ? pv.getEngValue() : pv.getRawValue();
    }

    public long getIntegerValue(IntegerValue iv) {
        if (iv instanceof FixedIntegerValue) {
            return ((FixedIntegerValue) iv).getValue();
        } else if (iv instanceof DynamicIntegerValue) {
            return result.resolveDynamicIntegerValue((DynamicIntegerValue) iv);
        }

        throw new UnsupportedOperationException("values of type " + iv + " not implemented");
    }

    public Mdb getMdb() {
        return proccessorData.getMdb();
    }

    public ProcessorData getProcessorData() {
        return proccessorData;
    }

    public long getAcquisitionTime() {
        return result.acquisitionTime;
    }
}
