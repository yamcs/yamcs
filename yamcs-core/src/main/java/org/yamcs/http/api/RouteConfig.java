package org.yamcs.http.api;

import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.api.Api;
import org.yamcs.api.HttpRoute;
import org.yamcs.http.RpcDescriptor;

import com.google.protobuf.Descriptors.MethodDescriptor;

import io.netty.handler.codec.http.HttpMethod;

/**
 * Struct containing all non-path route configuration
 */
public class RouteConfig implements Comparable<RouteConfig> {

    Api<Context> api;

    final String uriTemplate;
    final HttpMethod httpMethod;
    final int maxBodySize;
    final boolean offThread;

    private RpcDescriptor descriptor;
    AtomicLong requestCount = new AtomicLong();
    AtomicLong errorCount = new AtomicLong();

    private boolean deprecated;
    private String body;

    RouteConfig(Api<Context> api, HttpRoute httpOptions, RpcDescriptor descriptor) {
        this.api = api;
        this.descriptor = descriptor;

        offThread = httpOptions.getOffThread();
        deprecated = httpOptions.getDeprecated();

        switch (httpOptions.getPatternCase()) {
        case GET:
            httpMethod = HttpMethod.GET;
            uriTemplate = httpOptions.getGet();
            break;
        case POST:
            httpMethod = HttpMethod.POST;
            uriTemplate = httpOptions.getPost();
            break;
        case PATCH:
            httpMethod = HttpMethod.PATCH;
            uriTemplate = httpOptions.getPatch();
            break;
        case PUT:
            httpMethod = HttpMethod.PUT;
            uriTemplate = httpOptions.getPut();
            break;
        case DELETE:
            httpMethod = HttpMethod.DELETE;
            uriTemplate = httpOptions.getDelete();
            break;
        default:
            throw new IllegalStateException("Unexpected pattern '" + httpOptions.getPatternCase() + "'");
        }

        if (httpOptions.hasBody()) {
            body = httpOptions.getBody();
        }

        maxBodySize = httpOptions.hasMaxBodySize() ? httpOptions.getMaxBodySize() : Router.MAX_BODY_SIZE;
    }

    public RpcDescriptor getDescriptor() {
        return descriptor;
    }

    public MethodDescriptor getMethod() {
        return api.getDescriptorForType().findMethodByName(descriptor.getMethod());
    }

    @Override
    public int compareTo(RouteConfig o) {
        int pathLengthCompare = Integer.compare(uriTemplate.length(), o.uriTemplate.length());
        if (pathLengthCompare != 0) {
            return -pathLengthCompare;
        } else {
            return uriTemplate.compareTo(o.uriTemplate);
        }
    }

    public int maxBodySize() {
        return maxBodySize;
    }

    public String getBody() {
        return body;
    }

    public boolean isDeprecated() {
        return deprecated;
    }
}
