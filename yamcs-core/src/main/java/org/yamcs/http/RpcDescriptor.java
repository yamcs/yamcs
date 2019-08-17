package org.yamcs.http;

import org.yamcs.api.HttpRoute;

import com.google.protobuf.DescriptorProtos.DescriptorProto;

public class RpcDescriptor {

    private String service;
    private String method;
    private DescriptorProto inputType;
    private DescriptorProto outputType;

    private HttpRoute proto;

    public RpcDescriptor(String service, String method, DescriptorProto inputType, DescriptorProto outputType,
            HttpRoute proto) {
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

    public DescriptorProto getInputType() {
        return inputType;
    }

    public DescriptorProto getOutputType() {
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
