package org.yamcs;

import java.util.ArrayList;

public interface ParameterConsumer {

	void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items);

}
