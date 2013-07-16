package org.yamcs.xtceproc;

import java.util.List;

import org.yamcs.ContainerExtractionResult;

/**
 * Used together with the XtceTmProcessor to find out which containers a specific data packet is representing
 * @author nm
 *
 */
public interface ContainerListener {
    public abstract void update(List<ContainerExtractionResult> c);
}
