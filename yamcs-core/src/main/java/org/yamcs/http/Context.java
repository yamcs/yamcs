package org.yamcs.http;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yamcs.api.Api;
import org.yamcs.api.Observer;
import org.yamcs.logging.Log;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;

import io.netty.channel.ChannelHandlerContext;

/**
 * Request context used in RPC-style endpoints.
 */
public abstract class Context {

    private static AtomicInteger counter = new AtomicInteger();

    protected final Log log;

    /**
     * Unique id for this call.
     */
    private final int id;

    /**
     * The Netty request context for an RPC call. In general RPC implementation should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. http chunking)
     */
    public final ChannelHandlerContext nettyContext;

    protected final Api<Context> api;

    private JsonFormat.Parser jsonParser;
    private JsonFormat.Printer jsonPrinter;

    protected FieldMask fieldMask;

    /**
     * The request user.
     */
    public final User user;

    protected long txSize = 0;
    protected int statusCode;
    protected boolean reverseLookup;

    /**
     * A future that covers the full API call.
     * <p>
     * API implementations should use the passed {@link Observer} instead of this future.
     */
    final CompletableFuture<Void> requestFuture = new CompletableFuture<>();

    Context(HttpServer httpServer, ChannelHandlerContext nettyContext, User user, Api<Context> api) {
        this.id = counter.incrementAndGet();
        this.nettyContext = nettyContext;
        this.user = user;
        this.api = api;
        this.reverseLookup = httpServer.getReverseLookup();

        log = new Log(Context.class);
        log.setContext("c" + id);

        jsonParser = httpServer.getJsonParser();
        jsonPrinter = httpServer.getJsonPrinter();
    }

    boolean isDone() {
        return requestFuture.isDone();
    }

    public Api<Context> getApi() {
        return api;
    }

    public abstract MethodDescriptor getMethod();

    public boolean isServerStreaming() {
        return getMethod().toProto().getServerStreaming();
    }

    public boolean isClientStreaming() {
        return getMethod().toProto().getClientStreaming();
    }

    public Message getRequestPrototype() {
        MethodDescriptor method = getMethod();
        return api.getRequestPrototype(method);
    }

    public Message getResponsePrototype() {
        MethodDescriptor method = getMethod();
        return api.getResponsePrototype(method);
    }

    public void parseJson(String json, Builder builder) throws InvalidProtocolBufferException {
        jsonParser.merge(json, builder);
    }

    public String printJson(Message message) throws InvalidProtocolBufferException {
        return jsonPrinter.print(message);
    }

    public FieldMask getFieldMask() {
        return fieldMask;
    }

    /**
     * Get the number of bytes transferred as the result of this call. It should not include the http headers. Note that
     * the number might be increased before the data is sent so it will be wrong if there was an error sending data.
     * 
     * 
     * @return number of bytes transferred as part of the request
     */
    public long getTransferredSize() {
        return txSize;
    }

    public void addTransferredSize(long byteCount) {
        txSize += byteCount;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void reportStatusCode(int statusCode) {
        // TODO It should be possible to refactor this such that
        // the HTTP status code becomes the result of the requestFuture.
        if (this.statusCode != 0) {
            throw new IllegalArgumentException("Status code already set to " + this.statusCode);
        }
        this.statusCode = statusCode;
    }

    public String getClientAddress() {
        InetSocketAddress address = (InetSocketAddress) nettyContext.channel().remoteAddress();
        return reverseLookup ? address.getHostName() : address.getAddress().getHostAddress();
    }

    public void checkSystemPrivilege(SystemPrivilege privilege) throws ForbiddenException {
        if (!user.hasSystemPrivilege(privilege)) {
            throw new ForbiddenException("Missing system privilege '" + privilege + "'");
        }
    }

    public void checkAnyOfSystemPrivileges(SystemPrivilege... privileges) {
        var match = false;
        for (var privilege : privileges) {
            if (user.hasSystemPrivilege(privilege)) {
                match = true;
            }
        }
        if (!match) {
            var candidates = Stream.of(privileges).map(Object::toString).collect(Collectors.joining(", "));
            throw new ForbiddenException("Missing system privilege (one of " + candidates + ")");
        }
    }

    public void checkObjectPrivileges(ObjectPrivilegeType type, Collection<String> objects) throws ForbiddenException {
        checkObjectPrivileges(type, objects.toArray(new String[objects.size()]));
    }

    public void checkObjectPrivileges(ObjectPrivilegeType type, String... objects) throws ForbiddenException {
        for (String object : objects) {
            if (!user.hasObjectPrivilege(type, object)) {
                throw new ForbiddenException("No " + type + " authorization for '" + object + "'");
            }
        }
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return Integer.toString(id);
    }
}
