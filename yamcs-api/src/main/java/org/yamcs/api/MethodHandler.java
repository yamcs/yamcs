package org.yamcs.api;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

public interface MethodHandler {

    void call(MethodDescriptor method, Message request, Message responsePrototype,
            Observer<? extends Message> observer);

    Observer<? extends Message> streamingCall(MethodDescriptor method, Message request, Message responsePrototype,
            Observer<? extends Message> responseObserver);
}
