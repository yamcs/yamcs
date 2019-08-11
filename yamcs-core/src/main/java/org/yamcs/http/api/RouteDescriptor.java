package org.yamcs.http.api;

import org.yamcs.api.HttpRoute;

public class RouteDescriptor {

    private String service;
    private String method;
    private String inputType;
    private String outputType;

    private HttpRoute proto;

    public RouteDescriptor(String service, String method, String inputType, String outputType, HttpRoute proto) {
        this.service = service;
        this.method = method;
        this.inputType = inputType;
        this.outputType = outputType;
        this.proto = proto;
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public String getInputType() {
        return inputType;
    }

    public String getOutputType() {
        return outputType;
    }

    public String getDescription() {
        if (proto.hasDescription()) {
            return proto.getDescription();
        }
        return null;
    }

    public String getHttpMethod() {
        if (proto.hasMethod()) {
            return proto.getMethod();
        }
        return null;
    }

    public String getPath() {
        if (proto.hasPath()) {
            return proto.getPath();
        }
        return null;
    }

    public boolean isDeprecated() {
        return proto.hasDeprecated() && proto.getDeprecated();
    }
}
