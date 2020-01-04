package org.yamcs.http;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.HttpRoute;
import org.yamcs.api.WebSocketTopic;

import com.google.protobuf.DescriptorProtos.DescriptorProto;

public class RpcDescriptor {

    private static final HttpRoute WEBSOCKET_ROUTE = HttpRoute.newBuilder()
            .setGet("/api/websocket")
            .build();

    private final String service;
    private final String method;
    private final DescriptorProto inputType;
    private final DescriptorProto outputType;
    private final String description;
    private final HttpRoute httpRoute;
    private final List<HttpRoute> additionalHttpRoutes = new ArrayList<>(1);

    private WebSocketTopic websocketTopic;
    private final List<WebSocketTopic> additionalWebSocketTopics = new ArrayList<>(1);

    public RpcDescriptor(String service, String method, DescriptorProto inputType, DescriptorProto outputType,
            HttpRoute httpOptions, String description) {
        this.service = service;
        this.method = method;
        this.inputType = inputType;
        this.outputType = outputType;
        this.description = description;

        httpRoute = httpOptions;
        additionalHttpRoutes.addAll(httpOptions.getAdditionalBindingsList());
    }

    public RpcDescriptor(String service, String method, DescriptorProto inputType, DescriptorProto outputType,
            WebSocketTopic websocketTopic, String description) {
        this(service, method, inputType, outputType, WEBSOCKET_ROUTE, description);
        this.websocketTopic = websocketTopic;
        additionalWebSocketTopics.addAll(websocketTopic.getAdditionalBindingsList());
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public DescriptorProto getInputType() {
        return inputType;
    }

    public DescriptorProto getOutputType() {
        return outputType;
    }

    public String getDescription() {
        return description;
    }

    public HttpRoute getHttpRoute() {
        return httpRoute;
    }

    public List<HttpRoute> getAdditionalHttpRoutes() {
        return additionalHttpRoutes;
    }

    public boolean isWebSocket() {
        return httpRoute == WEBSOCKET_ROUTE;
    }

    public WebSocketTopic getWebSocketTopic() {
        return websocketTopic;
    }

    public List<WebSocketTopic> getAdditionalWebSocketTopics() {
        return additionalWebSocketTopics;
    }
}
