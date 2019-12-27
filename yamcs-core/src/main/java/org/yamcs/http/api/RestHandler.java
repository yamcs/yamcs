package org.yamcs.http.api;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpUtils;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.RouteHandler;
import org.yamcs.logging.Log;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.LinkInfo;
import org.yamcs.protobuf.RestExceptionMessage;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;

/**
 * Contains utility methods for REST handlers. May eventually refactor this out.
 */
public abstract class RestHandler extends RouteHandler {

    private static final Log log = new Log(RestHandler.class);

    protected final YamcsServer yamcsServer = YamcsServer.getServer();
    protected final SecurityStore securityStore = yamcsServer.getSecurityStore();

    protected static void completeOK(RestRequest restRequest) {
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(httpResponse, 0);
        completeRequest(restRequest, httpResponse);
    }

    public static <T extends Message> void completeOK(RestRequest restRequest, T responseMsg) {
        sendMessageResponse(restRequest, OK, responseMsg).addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
        });
    }

    protected static void completeOK(RestRequest restRequest, String contentType, ByteBuf body) {
        if (body == null) {
            throw new NullPointerException(
                    "body cannot be null; use the completeOK(request) to send an empty response.");
        }

        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        int txSize = body.readableBytes();
        HttpUtil.setContentLength(httpResponse, txSize);
        restRequest.addTransferredSize(txSize);
        completeRequest(restRequest, httpResponse);
    }

    private static void completeRequest(RestRequest restRequest, HttpResponse httpResponse) {
        ChannelFuture cf = HttpRequestHandler.sendResponse(restRequest.getChannelHandlerContext(),
                restRequest.getHttpRequest(), httpResponse, true);
        restRequest.reportStatusCode(httpResponse.status().code());
        cf.addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
            if (!l.isSuccess()) {
                log.error("Network error", l.cause());
            }
        });
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(RestRequest restRequest,
            HttpResponseStatus status, T responseMsg) {
        HttpRequest req = restRequest.getHttpRequest();
        ChannelHandlerContext ctx = restRequest.getChannelHandlerContext();
        ByteBuf body = restRequest.getChannelHandlerContext().alloc().buffer();
        MediaType contentType = restRequest.deriveTargetContentType();
        if (contentType != MediaType.JSON) {
            restRequest.reportStatusCode(status.code());
            return HttpRequestHandler.sendMessageResponse(ctx, req, status, responseMsg);
        } else {
            try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                contentType = MediaType.JSON;
                String str = JsonFormat.printer().print(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
            } catch (IOException e) {
                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                restRequest.reportStatusCode(status.code());
                return HttpRequestHandler.sendPlainTextError(ctx, req, status, e.toString());
            }
            HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
            HttpUtils.setContentTypeHeader(response, contentType);

            int txSize = body.readableBytes();
            HttpUtil.setContentLength(response, txSize);
            restRequest.reportStatusCode(status.code());
            return HttpRequestHandler.sendResponse(ctx, req, response, true);
        }

    }

    protected static ChannelFuture sendRestError(RestRequest req, HttpResponseStatus status, Throwable t) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        RestExceptionMessage msg = toException(t).build();
        req.reportStatusCode(status.code());
        return HttpRequestHandler.sendMessageResponse(ctx, req.getHttpRequest(), status, msg);
    }

    /**
     * write the error to the client and complete the request exceptionally
     * 
     * @param req
     * @param e
     */
    protected static void completeWithError(RestRequest req, HttpException e) {
        ChannelFuture cf = sendRestError(req, e.getStatus(), e);
        cf.addListener(l -> {
            req.getCompletableFuture().completeExceptionally(e);
            if (!l.isSuccess()) {
                log.error("Network error", l.cause());
            }
        });
    }

    private static RestExceptionMessage.Builder toException(Throwable t) {
        RestExceptionMessage.Builder exceptionb = RestExceptionMessage.newBuilder();
        exceptionb.setType(t.getClass().getSimpleName());

        // Try to get a specific message. i.e. turn "Type1: Type2: Type3: Message" into "Message"
        Throwable realCause = t;
        while (realCause.getCause() != null) {
            realCause = realCause.getCause();
        }
        if (realCause.getMessage() != null) {
            exceptionb.setMsg(realCause.getMessage());
        } else {
            exceptionb.setMsg(realCause.getClass().getSimpleName());
        }

        return exceptionb;
    }

    static String verifyInstance(String instance, boolean allowGlobal)
            throws NotFoundException {
        if (allowGlobal && YamcsServer.GLOBAL_INSTANCE.equals(instance)) {
            return instance;
        }
        return verifyInstance(instance);
    }

    public static String verifyInstance(String instance) throws NotFoundException {
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException("No instance named '" + instance + "'");
        }
        return instance;
    }

    protected static LinkInfo verifyLink(String instance, String linkName) throws NotFoundException {
        verifyInstance(instance);
        LinkInfo linkInfo = ManagementService.getInstance().getLinkInfo(instance, linkName);
        if (linkInfo == null) {
            throw new NotFoundException("No link named '" + linkName + "' within instance '" + instance + "'");
        }
        return linkInfo;
    }

    protected static Processor verifyProcessor(String instance, String processorName)
            throws NotFoundException {
        verifyInstance(instance);
        Processor processor = Processor.getInstance(instance, processorName);
        if (processor == null) {
            throw new NotFoundException("No processor '" + processorName + "' within instance '" + instance + "'");
        } else {
            return processor;
        }
    }

    protected static String verifyNamespace(XtceDb mdb, String pathName) throws NotFoundException {
        if (mdb.getNamespaces().contains(pathName)) {
            return pathName;
        }

        String rooted = "/" + pathName;
        if (mdb.getNamespaces().contains(rooted)) {
            return rooted;
        }

        throw new NotFoundException("No such namespace");
    }

    protected static SpaceSystem verifySpaceSystem(XtceDb mdb, String pathName) throws NotFoundException {
        String namespace;
        String name;
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            namespace = "";
            name = pathName;
        } else {
            namespace = pathName.substring(0, lastSlash);
            name = pathName.substring(lastSlash + 1);
        }

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        SpaceSystem spaceSystem = mdb.getSpaceSystem(id);
        if (spaceSystem != null) {
            return spaceSystem;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        spaceSystem = mdb.getSpaceSystem(id);
        if (spaceSystem != null) {
            return spaceSystem;
        }

        throw new NotFoundException("No such space system");
    }

    protected static NamedObjectId verifyParameterId(User user, XtceDb mdb, String pathName)
            throws HttpException {
        return verifyParameterWithId(user, mdb, pathName).getId();
    }

    public static Parameter verifyParameter(User user, XtceDb mdb, String pathName) throws HttpException {
        return verifyParameterWithId(user, mdb, pathName).getParameter();
    }

    protected static ParameterWithId verifyParameterWithId(User user, XtceDb mdb,
            String pathName) throws HttpException {
        int aggSep = AggregateUtil.findSeparator(pathName);

        PathElement[] aggPath = null;
        String nwa = pathName; // name without the aggregate part
        if (aggSep >= 0) {
            nwa = pathName.substring(0, aggSep);
            try {
                aggPath = AggregateUtil.parseReference(pathName.substring(aggSep));
            } catch (IllegalArgumentException e) {
                throw new NotFoundException("Invalid array/aggregate path in name " + pathName);
            }
        }

        //
        // }
        int lastSlash = nwa.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == nwa.length() - 1) {
            throw new NotFoundException("No such parameter (missing namespace?)");
        }

        String _namespace = nwa.substring(0, lastSlash);
        String name = nwa.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        String namespace = "/" + _namespace;
        Parameter p = mdb.getParameter(namespace, name);
        if (p == null) {
            namespace = _namespace;
            // Maybe some non-xtce namespace like MDB:OPS Name
            p = mdb.getParameter(namespace, name);
        }

        if (p != null && !hasObjectPrivilege(user, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
            throw new ForbiddenException("Unsufficient privileges to access parameter " + p.getQualifiedName());
        }
        if (p == null) {
            throw new NotFoundException("No parameter named " + pathName);
        }

        if (aggPath != null) {
            if (!AggregateUtil.verifyPath(p.getParameterType(), aggPath)) {
                throw new NotFoundException("Nonexistent array/aggregate path in name " + pathName);
            }
            name += AggregateUtil.toString(aggPath);
        }

        NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        return new ParameterWithId(p, id, aggPath);
    }

    protected static MetaCommand verifyCommand(XtceDb mdb, String pathName) throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such command (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        MetaCommand cmd = mdb.getMetaCommand(id);
        if (cmd == null) {
            // Maybe some non-xtce namespace like MDB:OPS Name
            id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
            cmd = mdb.getMetaCommand(id);
        }

        if (cmd == null) {
            throw new NotFoundException("No such command");
        } else {
            return cmd;
        }
    }

    protected static Algorithm verifyAlgorithm(XtceDb mdb, String pathName) throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such algorithm (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        Algorithm algorithm = mdb.getAlgorithm(id);
        if (algorithm != null) {
            return algorithm;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        algorithm = mdb.getAlgorithm(id);
        if (algorithm != null) {
            return algorithm;
        }

        throw new NotFoundException("No such algorithm");
    }

    protected static ParameterType verifyParameterType(XtceDb mdb, String pathName)
            throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such parameter type (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        ParameterType type = mdb.getParameterType(id);
        if (type != null) {
            return type;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        type = mdb.getParameterType(id);
        if (type != null) {
            return type;
        }

        throw new NotFoundException("No such parameter type");
    }

    protected static SequenceContainer verifyContainer(XtceDb mdb, String pathName)
            throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such container (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        SequenceContainer container = mdb.getSequenceContainer(id);
        if (container != null) {
            return container;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        container = mdb.getSequenceContainer(id);
        if (container != null) {
            return container;
        }

        throw new NotFoundException("No such container");
    }

    protected static class NameDescriptionWithId<T extends NameDescription> {
        final private T item;
        private final NamedObjectId requestedId;

        NameDescriptionWithId(T item, NamedObjectId requestedId) {
            this.item = item;
            this.requestedId = requestedId;
        }

        public T getItem() {
            return item;
        }

        public NamedObjectId getRequestedId() {
            return requestedId;
        }
    }

    public static void checkSystemPrivilege(User user, SystemPrivilege privilege) throws ForbiddenException {
        if (!user.hasSystemPrivilege(privilege)) {
            throw new ForbiddenException("No system privilege '" + privilege + "'");
        }
    }

    public static void checkObjectPrivileges(User user, ObjectPrivilegeType type, Collection<String> objects)
            throws ForbiddenException {
        checkObjectPrivileges(user, type, objects.toArray(new String[objects.size()]));
    }

    public static void checkObjectPrivileges(User user, ObjectPrivilegeType type, String... objects)
            throws ForbiddenException {
        for (String object : objects) {
            if (!user.hasObjectPrivilege(type, object)) {
                throw new ForbiddenException("No " + type + " authorization for '" + object + "'");
            }
        }
    }

    public static boolean hasSystemPrivilege(User user, SystemPrivilege privilege) {
        return user.hasSystemPrivilege(privilege);
    }

    public static boolean hasObjectPrivilege(User user, ObjectPrivilegeType type, String privilege) {
        return user.hasObjectPrivilege(type, privilege);
    }

    protected Object convertToFieldValue(RestRequest req, FieldDescriptor field, String parameter)
            throws HttpException {
        if (field.isRepeated()) {
            if (field.getJavaType() != JavaType.STRING) {
                throw new UnsupportedOperationException(
                        "No query parameter conversion for repeated type " + field.getJavaType());
            }
            List<Object> values = new ArrayList<>();
            for (String value : req.getQueryParameterList(field.getJsonName())) {
                for (String item : value.split(",")) { // Support both repeated query params and comma-separated
                    values.add(item);
                }
            }
            return values;
        } else {
            switch (field.getJavaType()) {
            case BOOLEAN:
                return req.getQueryParameterAsBoolean(field.getJsonName());
            case INT:
                return req.getQueryParameterAsInt(field.getJsonName());
            case LONG:
                return req.getQueryParameterAsLong(field.getJsonName());
            case STRING:
                return req.getQueryParameter(field.getJsonName());
            default:
                throw new UnsupportedOperationException(
                        "No query parameter conversion for type " + field.getJavaType());
            }
        }
    }
}
