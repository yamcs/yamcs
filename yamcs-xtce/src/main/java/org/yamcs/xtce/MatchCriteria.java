package org.yamcs.xtce;

import java.io.Serializable;
import java.util.Set;

public interface MatchCriteria extends Serializable {
	public Set<Parameter> getDependentParameters();

}
