package org.yamcs.client;


import org.yamcs.api.MethodHandler;
import org.yamcs.client.base.AbstractSubscription;
import org.yamcs.protobuf.ContainerData;
import org.yamcs.protobuf.SubscribeContainersRequest;

public class ContainerSubscription extends AbstractSubscription<SubscribeContainersRequest, ContainerData> {
    protected ContainerSubscription(MethodHandler methodHandler) {
        super(methodHandler, "containers", ContainerData.class);
    }
}
