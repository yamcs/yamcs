package org.yamcs.xtceproc;

import java.io.Serializable;

public abstract class CalibratorProc implements Serializable {
    private static final long serialVersionUID = 200706051148L;
    public abstract Double calibrate(double d) ;
}
