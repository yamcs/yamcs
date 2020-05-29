package org.yamcs.api;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

public interface MethodHandler {

    void callMethod(MethodDescriptor method, Message request, Message responsePrototype,
            Observer<? extends Message> observer);
}
