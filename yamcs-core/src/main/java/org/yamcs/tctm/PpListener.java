package org.yamcs.tctm;

import java.util.Collection;

import org.yamcs.ParameterValue;


public interface PpListener {
    /**
     *Update a collection of PPs
     */
    public abstract void updatePps(long gentime, String group, int seqNum, Collection<ParameterValue> params);
}

