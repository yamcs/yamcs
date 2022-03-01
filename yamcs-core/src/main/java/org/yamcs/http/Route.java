package org.yamcs.http;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.api.Api;
import org.yamcs.api.HttpRoute;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import io.netty.handler.codec.http.HttpMethod;

public class Route implements Comparable<Route> {

    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?\\{(\\w+)(\\?|\\*|\\*\\*)?\\}");

    private final Pattern pattern;

    private final Api<Context> api;
    private final String uriTemplate;
    private final HttpMethod httpMethod;
    private final boolean offloaded;
    private final boolean deprecated;
    private final String body;
    private final String fieldMaskRoot;
    private final String logFormat;
    private final RpcDescriptor descriptor;

    // May be unspecified
    private int maxBodySize;

    private Counter requestCounter;
    private Counter errorCounter;

    Route(Api<Context> api, HttpRoute httpOptions, RpcDescriptor descriptor, MetricRegistry metricRegistry) {
        this.api = api;
        this.descriptor = descriptor;

        requestCounter = metricRegistry.counter(String.format(
                "yamcs.api.requests.total.%s.%s", descriptor.getService(), descriptor.getMethod()));
        errorCounter = metricRegistry.counter(String.format(
                "yamcs.api.errors.total.%s.%s", descriptor.getService(), descriptor.getMethod()));

        offloaded = httpOptions.getOffloaded();
        deprecated = httpOptions.getDeprecated();
        logFormat = httpOptions.hasLog() ? httpOptions.getLog() : null;

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

        pattern = toPattern(uriTemplate);

        body = httpOptions.hasBody() ? httpOptions.getBody() : null;
        fieldMaskRoot = httpOptions.hasFieldMaskRoot() ? httpOptions.getFieldMaskRoot() : null;
        if (httpOptions.hasMaxBodySize()) {
            maxBodySize = httpOptions.getMaxBodySize();
        }
    }

    private Pattern toPattern(String route) {
        Matcher matcher = ROUTE_PATTERN.matcher(route);
        StringBuffer buf = new StringBuffer("^");
        while (matcher.find()) {
            boolean star = ("*".equals(matcher.group(3)));
            boolean optional = ("?".equals(matcher.group(3)));
            if ("**".equals(matcher.group(3))) {
                star = true;
                optional = true;
            }
            String slash = (matcher.group(1) != null) ? matcher.group(1) : "";
            StringBuilder replacement = new StringBuilder();
            if (optional) {
                replacement.append("(?:");
                replacement.append(slash);
                replacement.append("(?<").append(matcher.group(2)).append(">");
                replacement.append(star ? ".+?" : "[^/]+");
                replacement.append(")?)?");
            } else {
                replacement.append(slash);
                replacement.append("(?<").append(matcher.group(2)).append(">");
                replacement.append(star ? ".+?" : "[^/]+");
                replacement.append(")");
            }

            matcher.appendReplacement(buf, replacement.toString());
        }
        matcher.appendTail(buf);
        return Pattern.compile(buf.append("/?$").toString());
    }

    public RpcDescriptor getDescriptor() {
        return descriptor;
    }

    public Api<Context> getApi() {
        return api;
    }

    public String getBody() {
        return body;
    }

    public String getFieldMaskRoot() {
        return fieldMaskRoot;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public String getLogFormat() {
        return logFormat;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public boolean isOffloaded() {
        return offloaded;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public Matcher matchURI(String uri) {
        return pattern.matcher(uri);
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public long getRequestCount() {
        return requestCounter.getCount();
    }

    public void incrementRequestCount() {
        requestCounter.inc();
    }

    public Counter getRequestCounter() {
        return requestCounter;
    }

    public long getErrorCount() {
        return errorCounter.getCount();
    }

    public void incrementErrorCount() {
        errorCounter.inc();
    }

    public Counter getErrorCounter() {
        return errorCounter;
    }

    @Override
    public int compareTo(Route o) {
        int pathLengthCompare = Integer.compare(uriTemplate.length(), o.uriTemplate.length());
        if (pathLengthCompare != 0) {
            return -pathLengthCompare;
        } else {
            return uriTemplate.compareTo(o.uriTemplate);
        }
    }
}
