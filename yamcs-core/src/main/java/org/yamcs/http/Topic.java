package org.yamcs.http;

import org.yamcs.api.Api;
import org.yamcs.api.Observer;
import org.yamcs.api.WebSocketTopic;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

public class Topic {

    private final Api<Context> api;
    private final String name;
    private final RpcDescriptor descriptor;

    private final boolean deprecated;

    Topic(Api<Context> api, WebSocketTopic topic, RpcDescriptor descriptor) {
        this.api = api;
        this.name = topic.getTopic();
        this.descriptor = descriptor;

        deprecated = topic.getDeprecated();
    }

    public RpcDescriptor getDescriptor() {
        return descriptor;
    }

    public Api<Context> getApi() {
        return api;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public String getName() {
        return name;
    }

    public MethodDescriptor getMethodDescriptor() {
        String methodName = descriptor.getMethod();
        return api.getDescriptorForType().findMethodByName(methodName);
    }

    public Message getRequestPrototype() {
        return api.getRequestPrototype(getMethodDescriptor());
    }

    public Message getResponsePrototype() {
        return api.getResponsePrototype(getMethodDescriptor());
    }

    public void callMethod(Context ctx, Message request, Observer<Message> observer) {
        MethodDescriptor method = getMethodDescriptor();
        api.callMethod(method, ctx, request, observer);
    }

    public Observer<Message> callMethod(Context ctx, Observer<Message> observer) {
        MethodDescriptor method = getMethodDescriptor();
        return api.callMethod(method, ctx, observer);
    }
}
