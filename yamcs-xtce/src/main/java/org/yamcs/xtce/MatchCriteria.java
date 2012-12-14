package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Set;

public abstract class MatchCriteria implements Serializable {
	private static final long serialVersionUID = 200706050819L;
	public abstract Set<Parameter> getDependentParameters();

}
