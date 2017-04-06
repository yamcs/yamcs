package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.api.MediaType;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.security.Privilege;
import org.yamcs.utils.StringConverter;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.HttpUtils;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.RouteHandler;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.protostuff.Schema;

/**
 * Contains utility methods for REST handlers. May eventually refactor this out.
 */
public abstract class RestHandler extends RouteHandler {

    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);

    protected static void completeOK(RestRequest restRequest) {
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(httpResponse, 0);
        completeRequest(restRequest, httpResponse);
    }

    protected static <T extends MessageLite> void completeOK(RestRequest restRequest, T responseMsg, Schema<T> responseSchema) {
        HttpRequestHandler.sendMessageResponse(restRequest.getChannelHandlerContext(), restRequest.getHttpRequest(), OK, responseMsg, responseSchema).addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
        });
    }

    protected static void completeOK(RestRequest restRequest, MediaType contentType, ByteBuf body) {
        if (body == null) {
            throw new NullPointerException("body cannot be null; use the completeOK(request) to send an empty response.");
        } 
        
        HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        HttpUtils.setContentTypeHeader(httpResponse, contentType);
        int txSize =  body.readableBytes();
        HttpUtil.setContentLength(httpResponse, txSize);
        restRequest.addTransferredSize(txSize);
        completeRequest(restRequest, httpResponse);
    }

    private static void completeRequest(RestRequest restRequest, HttpResponse httpResponse) {
        ChannelFuture cf = HttpRequestHandler.sendResponse(restRequest.getChannelHandlerContext(), restRequest.getHttpRequest(), httpResponse, true);

        cf.addListener(l -> {
            restRequest.getCompletableFuture().complete(null);
        });
    }

    protected static ChannelFuture sendRestError(RestRequest req, HttpResponseStatus status, Throwable t) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        RestExceptionMessage msg = toException(t).build();
        return HttpRequestHandler.sendMessageResponse(ctx, req.getHttpRequest(), status, msg, SchemaWeb.RestExceptionMessage.WRITE);
    }
    /**
     * write the error to the client and complete the request exceptionally
     * @param req
     * @param e
     */
    protected static void completeWithError(RestRequest req, HttpException e) {
        ChannelFuture cf = sendRestError(req, e.getStatus(), e);
        cf.addListener(l-> {
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

    protected static ClientInfo verifyClient(RestRequest req, int clientId) throws NotFoundException {
        ClientInfo ci = ManagementService.getInstance().getClientInfo(clientId);
        if (ci == null) {
            throw new NotFoundException(req, "No such client");
        } else {
            return ci;
        }
    }

    protected static Processor verifyProcessor(RestRequest req, String instance, String processorName) throws NotFoundException {
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

    protected static NamedObjectId verifyParameterId(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
        return verifyParameterWithId(req, mdb, pathName).getRequestedId();
    }

    protected static Parameter verifyParameter(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
        return verifyParameterWithId(req, mdb, pathName).getItem();
    }

    protected static NameDescriptionWithId<Parameter> verifyParameterWithId(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
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


        if (p != null && !authorised(req, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter {} found, but withheld due to insufficient privileges. Returning 404 instead", StringConverter.idToString(id));
            p = null;
        }

        if (p == null) {
            throw new NotFoundException(req, "No parameter named " + StringConverter.idToString(id));
        } else {
            return new NameDescriptionWithId<Parameter>(p, id);
        }
    }

    protected static Stream verifyStream(RestRequest req, YarchDatabase ydb, String streamName) throws NotFoundException {
        Stream stream = ydb.getStream(streamName);
        if (stream == null) {
            throw new NotFoundException(req, "No stream named '" + streamName + "' (instance: '" + ydb.getName() + "')");
        } else {
            return stream;
        }
    }

    protected static TableDefinition verifyTable(RestRequest req, YarchDatabase ydb, String tableName) throws NotFoundException {
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

        if (cmd != null && !authorised(req, Privilege.Type.TC, cmd.getQualifiedName())) {
            log.warn("Command {} found, but withheld due to insufficient privileges. Returning 404 instead", StringConverter.idToString(id));
            cmd = null;
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

    protected static SequenceContainer verifyContainer(RestRequest req, XtceDb mdb, String pathName) throws NotFoundException {
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
            throw new BadRequestException("Alarms are not enabled for processor '" + instance + "/" + processorName + "'");
        } else {
            return processor.getParameterRequestManager().getAlarmServer();
        }
    }

    protected static boolean authorised(RestRequest req, Privilege.Type type, String privilege) {
        return Privilege.getInstance().hasPrivilege1(req.getAuthToken(), type, privilege);
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
        .addListener(l-> req.getCompletableFuture().complete(null));
    }
}
