package org.yamcs.api;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.Message;

public interface Api<T> {

    ServiceDescriptor getDescriptorForType();

    void callMethod(MethodDescriptor method, T ctx, Message request, Observer<Message> observer);

    Observer<Message> callMethod(MethodDescriptor method, T ctx, Observer<Message> observer);

    Message getRequestPrototype(MethodDescriptor method);

    Message getResponsePrototype(MethodDescriptor method);
}
