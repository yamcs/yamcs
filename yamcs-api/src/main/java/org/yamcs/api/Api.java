package org.yamcs.api;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Message;

public interface Api {

    ServiceDescriptor getDescriptorForType();

    void callMethod(MethodDescriptor method, Message request, CompletableFuture<Message> future);

    Message getRequestPrototype(MethodDescriptor method);

    Message getResponsePrototype(MethodDescriptor method);
}
