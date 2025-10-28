package org.yamcs.http;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.HttpRoute;
import org.yamcs.api.WebSocketTopic;

import com.google.protobuf.DescriptorProtos.DescriptorProto;

public class RpcDescriptor {

    private final String packageName;
    private final String service;
    private final String method;
    private final String inputSymbol;
    private final DescriptorProto inputType;
    private final String outputSymbol;
    private final DescriptorProto outputType;
    private final String description;
    private final HttpRoute httpRoute;
    private final List<HttpRoute> additionalHttpRoutes = new ArrayList<>(1);

    private WebSocketTopic websocketTopic;
    private final List<WebSocketTopic> additionalWebSocketTopics = new ArrayList<>(1);

    public RpcDescriptor(String packageName, String service, String method,
            String inputSymbol, DescriptorProto inputType,
            String outputSymbol, DescriptorProto outputType,
            HttpRoute httpOptions) {
        this.packageName = packageName;
        this.service = service;
        this.method = method;
        this.inputSymbol = inputSymbol;
        this.inputType = inputType;
        this.outputSymbol = outputSymbol;
        this.outputType = outputType;
        this.description = service + "." + method;

        httpRoute = httpOptions;
        additionalHttpRoutes.addAll(httpOptions.getAdditionalBindingsList());
    }

    public RpcDescriptor(String packageName, String service, String method,
            String inputSymbol, DescriptorProto inputType,
            String outputSymbol, DescriptorProto outputType,
            WebSocketTopic websocketTopic) {
        this(packageName, service, method, inputSymbol, inputType, outputSymbol, outputType,
                HttpServer.WEBSOCKET_ROUTE);
        this.websocketTopic = websocketTopic;
        additionalWebSocketTopics.addAll(websocketTopic.getAdditionalBindingsList());
    }

    public String getPackage() {
        return packageName;
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public String getInputSymbol() {
        return inputSymbol;
    }

    public DescriptorProto getInputType() {
        return inputType;
    }

    public String getOutputSymbol() {
        return outputSymbol;
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

    public WebSocketTopic getWebSocketTopic() {
        return websocketTopic;
    }

    public List<WebSocketTopic> getAdditionalWebSocketTopics() {
        return additionalWebSocketTopics;
    }
}
