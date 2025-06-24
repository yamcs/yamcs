package org.yamcs.mdb;


/**
 * Used together with the XtceTmProcessor to find out which containers a specific data packet is representing
 * @author nm
 *
 */
public interface ContainerListener {
    public abstract void update(ContainerProcessingResult result);
}
