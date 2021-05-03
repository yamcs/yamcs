package org.yamcs.xtceproc;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.SequenceContainer;

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
    ProcessingStatistics stats;
    long expireMillis = -1; // -1 means not defined
    XtceProcessingException exception;

    public ContainerProcessingResult(long aquisitionTime, long generationTime, ProcessingStatistics stats,
            LastValueCache procLastValueCache) {
        super(procLastValueCache, new ParameterValueList(), null, null, null);
        this.acquisitionTime = aquisitionTime;
        this.generationTime = generationTime;
        this.stats = stats;

    }

    public String getPacketName() {
        // Derives the archive partition based on a list of matched containers. The first container is the root
        // container.
        for (int i = containers.size() - 1; i >= 0; i--) {
            SequenceContainer sc = containers.get(i).getContainer();
            if (sc.useAsArchivePartition()) {
                return sc.getQualifiedName();
            }
        }
        return containers.get(0).getContainer().getQualifiedName();
    }

    public ParameterValueList getParameterResult() {
        return getTmParams();
    }

    public List<ContainerExtractionResult> getContainerResult() {
        return containers;
    }

}