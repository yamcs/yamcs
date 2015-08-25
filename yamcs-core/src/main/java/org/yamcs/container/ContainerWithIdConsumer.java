package org.yamcs.container;

import java.util.List;

import org.yamcs.ParameterValue;

public interface ContainerWithIdConsumer {
	void processContainer(ContainerWithId cwi, List<ParameterValue> parameters);
}
