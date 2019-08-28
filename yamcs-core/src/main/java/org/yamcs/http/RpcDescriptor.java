package org.yamcs.http;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.HttpRoute;

import com.google.protobuf.DescriptorProtos.DescriptorProto;

public class RpcDescriptor {

    private String service;
    private String method;
    private DescriptorProto inputType;
    private DescriptorProto outputType;
    private String description;

    private HttpRoute httpRoute;
    private List<HttpRoute> additionalHttpRoutes = new ArrayList<>(1);

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
}
