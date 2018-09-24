package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.api.MediaType;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.StringConverter;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.HttpUtils;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.RouteHandler;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Contains utility methods for REST handlers. May eventually refactor this out.
 */
public abstract class RestHandler extends RouteHandler {
    public final static String GLOBAL_INSTANCE = "_global";

    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);

    protected static void completeOK(RestRequest restRequest) {
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(httpResponse, 0);
        completeRequest(restRequest, httpResponse);
    }

    protected static <T extends Message> void completeOK(RestRequest restRequest, T responseMsg) {
        sendMessageResponse(restRequest, OK, responseMsg).addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
        });
    }

    protected static void completeOK(RestRequest restRequest, MediaType contentType, ByteBuf body) {
        completeOK(restRequest, contentType.toString(), body);
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
        cf.addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
        });
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(RestRequest restRequest,
            HttpResponseStatus status, T responseMsg) {
        HttpRequest req = restRequest.getHttpRequest();
        ChannelHandlerContext ctx = restRequest.getChannelHandlerContext();
        ByteBuf body = restRequest.getChannelHandlerContext().alloc().buffer();
        MediaType contentType = restRequest.deriveTargetContentType();
        if (contentType != MediaType.JSON) {
            return HttpRequestHandler.sendMessageResponse(ctx, req, status, responseMsg);
        } else {
            try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                contentType = MediaType.JSON;
                String str = JsonFormat.printer().print(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
                // body.writeBytes(HttpRequestHandler.NEWLINE_BYTES); // For curl comfort
            } catch (IOException e) {
                return HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        e.toString());
            }
            HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
            HttpUtils.setContentTypeHeader(response, contentType);

            int txSize = body.readableBytes();
            HttpUtil.setContentLength(response, txSize);
            return HttpRequestHandler.sendResponse(ctx, req, response, true);
        }

    }

    protected static ChannelFuture sendRestError(RestRequest req, HttpResponseStatus status, Throwable t) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        RestExceptionMessage msg = toException(t).build();
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
        });
    }

    protected static void abortRequest(RestRequest req) {
        req.getCompletableFuture().complete(null);
    }

    /**
     * Just a little shortcut because builders are dead ugly
     */
    private static RestExceptionMessage.Builder toException(Throwable t) {
        RestExceptionMessage.Builder exceptionb = RestExceptionMessage.newBuilder();
        exceptionb.setType(t.getClass().getSimpleName());
        if (t.getMessage() != null) {
            exceptionb.setMsg(t.getMessage());
        }
        return exceptionb;
    }

    protected static String verifyInstance(RestRequest req, String instance, boolean allowGlobal)
            throws NotFoundException {
        if (allowGlobal && GLOBAL_INSTANCE.equals(instance)) {
            return instance;
        }
        return verifyInstance(req, instance);
    }

    protected static String verifyInstance(RestRequest req, String instance) throws NotFoundException {
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException(req, "No instance named '" + instance + "'");
        }
        return instance;
    }

    protected static LinkInfo verifyLink(RestRequest req, String instance, String linkName) throws NotFoundException {
        verifyInstance(req, instance);
        LinkInfo linkInfo = ManagementService.getInstance().getLinkInfo(instance, linkName);
        if (linkInfo == null) {
            throw new NotFoundException(req, "No link named '" + linkName + "' within instance '" + instance + "'");
        }
        return linkInfo;
    }

    protected static ConnectedClient verifyClient(RestRequest req, int clientId) throws NotFoundException {
        ConnectedClient client = ManagementService.getInstance().getClient(clientId);
        if (client == null) {
            throw new NotFoundException(req, "No such client");
        } else {
            return client;
        }
    }

    protected static Processor verifyProcessor(RestRequest req, String instance, String processorName)
            throws NotFoundException {
        verifyInstance(req, instance);
        Processor processor = Processor.getInstance(instance, processorName);
        if (processor == null) {
            throw new NotFoundException(req, "No processor '" + processorName + "' within instance '" + instance + "'");
        } else {
            return processor;
        }
    }

    protected static String verifyNamespace(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
        if (mdb.getNamespaces().contains(pathName)) {
            return pathName;
        }

        String rooted = "/" + pathName;
        if (mdb.getNamespaces().contains(rooted)) {
            return rooted;
        }

        throw new NotFoundException(req, "No such namespace");
    }

    protected static SpaceSystem verifySpaceSystem(RestRequest req, XtceDb mdb, String pathName)
            throws NotFoundException {
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

        throw new NotFoundException(req, "No such space system");
    }

    protected static NamedObjectId verifyParameterId(RestRequest req, XtceDb mdb, String pathName)
            throws HttpException {
        return verifyParameterWithId(req, mdb, pathName).getRequestedId();
    }

    protected static Parameter verifyParameter(RestRequest req, XtceDb mdb, String pathName) throws HttpException {
        return verifyParameterWithId(req, mdb, pathName).getItem();
    }

    protected static NameDescriptionWithId<Parameter> verifyParameterWithId(RestRequest req, XtceDb mdb,
            String pathName) throws HttpException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such parameter (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        Parameter p = mdb.getParameter(id);
        if (p == null) {
            // Maybe some non-xtce namespace like MDB:OPS Name
            id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
            p = mdb.getParameter(id);
        }

        if (p != null && !hasObjectPrivilege(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
            throw new ForbiddenException("Unsufficient privileges to access parameter " + p.getQualifiedName());
        }

        if (p == null) {
            throw new NotFoundException(req, "No parameter named " + StringConverter.idToString(id));
        } else {
            return new NameDescriptionWithId<>(p, id);
        }
    }

    protected static Stream verifyStream(RestRequest req, YarchDatabaseInstance ydb, String streamName)
            throws NotFoundException {
        Stream stream = ydb.getStream(streamName);

        if (stream != null && !hasObjectPrivilege(req, ObjectPrivilegeType.Stream, streamName)) {
            log.warn("Stream {} found, but withheld due to insufficient privileges. Returning 404 instead",
                    streamName);
            stream = null;
        }

        if (stream == null) {
            throw new NotFoundException(req,
                    "No stream named '" + streamName + "' (instance: '" + ydb.getName() + "')");
        } else {
            return stream;
        }
    }

    protected static TableDefinition verifyTable(RestRequest req, YarchDatabaseInstance ydb, String tableName)
            throws NotFoundException {
        TableDefinition table = ydb.getTable(tableName);
        if (table == null) {
            throw new NotFoundException(req, "No table named '" + tableName + "' (instance: '" + ydb.getName() + "')");
        } else {
            return table;
        }
    }

    protected static MetaCommand verifyCommand(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such command (missing namespace?)");
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
            throw new NotFoundException(req, "No such command");
        } else {
            return cmd;
        }
    }

    protected static Algorithm verifyAlgorithm(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such algorithm (missing namespace?)");
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

        throw new NotFoundException(req, "No such algorithm");
    }

    protected static ParameterType verifyParameterType(RestRequest req, XtceDb mdb, String pathName)
            throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such parameter type (missing namespace?)");
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

        throw new NotFoundException(req, "No such parameter type");
    }

    protected static SequenceContainer verifyContainer(RestRequest req, XtceDb mdb, String pathName)
            throws NotFoundException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such container (missing namespace?)");
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

        throw new NotFoundException(req, "No such container");
    }

    protected static AlarmServer verifyAlarmServer(Processor processor) throws BadRequestException {
        if (!processor.hasAlarmServer()) {
            String instance = processor.getInstance();
            String processorName = processor.getName();
            throw new BadRequestException(
                    "Alarms are not enabled for processor '" + instance + "/" + processorName + "'");
        } else {
            return processor.getParameterRequestManager().getAlarmServer();
        }
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

    public static void completeChunkedTransfer(RestRequest req) {
        req.getChannelHandlerContext().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(ChannelFutureListener.CLOSE)
                .addListener(l -> req.getCompletableFuture().complete(null));
    }

    protected static void checkSystemPrivilege(RestRequest req, SystemPrivilege privilege) throws ForbiddenException {
        if (!req.getUser().hasSystemPrivilege(privilege)) {
            throw new ForbiddenException("No system privilege '" + privilege + "'");
        }
    }

    protected static void checkObjectPrivileges(RestRequest req, ObjectPrivilegeType type, Collection<String> objects)
            throws ForbiddenException {
        checkObjectPrivileges(req, type, objects.toArray(new String[objects.size()]));
    }

    protected static void checkObjectPrivileges(RestRequest req, ObjectPrivilegeType type, String... objects)
            throws ForbiddenException {
        for (String object : objects) {
            if (!req.getUser().hasObjectPrivilege(type, object)) {
                throw new ForbiddenException("No " + type + " authorization for '" + object + "'");
            }
        }
    }

    protected static boolean hasSystemPrivilege(RestRequest req, SystemPrivilege privilege) {
        return req.getUser().hasSystemPrivilege(privilege);
    }

    protected static boolean hasObjectPrivilege(RestRequest req, ObjectPrivilegeType type, String privilege) {
        return req.getUser().hasObjectPrivilege(type, privilege);
    }
}
