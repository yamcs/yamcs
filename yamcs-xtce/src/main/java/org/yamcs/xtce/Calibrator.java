package org.yamcs.xtce;

import java.io.Serializable;

public abstract class Calibrator implements Serializable {
	private static final long serialVersionUID = 200706051148L;
	public abstract Double calibrate(double d) ;

}
