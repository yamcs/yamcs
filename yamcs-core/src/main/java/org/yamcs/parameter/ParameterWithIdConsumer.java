package org.yamcs.parameter;

import java.util.List;

public interface ParameterWithIdConsumer {
    public abstract void update(int subscriptionId, List<ParameterValueWithId> params);

}
