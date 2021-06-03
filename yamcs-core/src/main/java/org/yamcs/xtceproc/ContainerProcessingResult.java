package org.yamcs.xtceproc;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValueList;

/**
 * the container result is built during a container processing
 * <p>
 * It is an super class of the {@link ProcessingData} and passed to the ParameteRequestManager to compute algorithms,
 * trigger alarms, etc.
 *
 */
public class ContainerProcessingResult extends ProcessingData {
    List<ContainerExtractionResult> containers = new ArrayList<>();
    long acquisitionTime;
    long generationTime;
    long expireMillis = -1; // -1 means not defined
    XtceProcessingException exception;

    public ContainerProcessingResult(long aquisitionTime, long generationTime, LastValueCache procLastValueCache) {
        super(procLastValueCache, new ParameterValueList(), null, null, null);
        this.acquisitionTime = aquisitionTime;
        this.generationTime = generationTime;
    }

    public ParameterValueList getParameterResult() {
        return getTmParams();
    }

    public List<ContainerExtractionResult> getContainerResult() {
        return containers;
    }

}
