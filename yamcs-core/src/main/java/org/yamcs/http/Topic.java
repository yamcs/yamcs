package org.yamcs.http;

import org.yamcs.api.Api;
import org.yamcs.api.WebSocketTopic;

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
}
