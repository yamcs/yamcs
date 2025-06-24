package org.yamcs.mdb;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValueList;

/**
 * the container result is built during a container processing
 * <p>
 * It is an super class of the {@link ProcessingContext} and passed to the ParameteRequestManager to compute algorithms,
 * trigger alarms, etc.
 *
 */
public class ContainerProcessingResult extends ProcessingContext {
    List<ContainerExtractionResult> containers = new ArrayList<>();
    final long acquisitionTime;
    final int seqCount;
    long expireMillis = -1; // -1 means not defined
    XtceProcessingException exception;
    // link on which the container has been received
    String link;


    public ContainerProcessingResult(long acquisitionTime, long generationTime, int seqCount,
            LastValueCache procLastValueCache) {
        super(procLastValueCache, new ParameterValueList(), null, null, null, generationTime);
        this.seqCount = seqCount;
        this.acquisitionTime = acquisitionTime;
    }

    public ParameterValueList getParameterResult() {
        return getTmParams();
    }

    public List<ContainerExtractionResult> getContainerResult() {
        return containers;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

}
