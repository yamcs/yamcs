package org.yamcs.api.rest;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.UsernamePasswordToken;

import com.google.protobuf.MessageLite;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

class HttpClient {
    MediaType sendMediaType = MediaType.PROTOBUF;
    MediaType acceptMediaType = MediaType.PROTOBUF;
    URI uri;
    EventLoopGroup group;


    private long maxResponseLength=1024*1024;//max length of the expected response 


    public CompletableFuture<byte[]> doAsyncRequest(String url, HttpMethod httpMethod, byte[] body, AuthenticationToken authToken) throws URISyntaxException {
        AccumulatingChannelHandler channelHandler = new AccumulatingChannelHandler();

        ChannelFuture chf = setupChannel(url ,channelHandler);
        HttpRequest request = setupRequest(url, httpMethod, body, authToken);
        CompletableFuture<byte[]> cf = new CompletableFuture<byte[]>();
        chf.addListener(f->{
            if(!f.isSuccess()) {
                cf.completeExceptionally(f.cause());
                return;
            }

            Channel ch = chf.channel();
            ch.writeAndFlush(request).await(1, TimeUnit.SECONDS);

            ChannelFuture closeFuture = ch.closeFuture();

            closeFuture.addListener(f1-> {
                if(channelHandler.exception!=null) {
                    cf.completeExceptionally(channelHandler.exception);
                } else if(channelHandler.httpResponse==null) {
                    cf.completeExceptionally(new IOException("connection closed without providing an http response"));
                } else {
                    HttpResponse resp = channelHandler.httpResponse;
                    if(resp.getStatus().code() != HttpResponseStatus.OK.code()) {
                        Exception exception = decodeException(resp, getByteArray(channelHandler.result));
                        cf.completeExceptionally(exception);
                    } else {
                        cf.complete(getByteArray(channelHandler.result));
                    }
                }
            });
        });

        return cf;
    }


    private static YamcsApiException decodeException( HttpObject httpObj, byte[] data) throws IOException {
        YamcsApiException exception;
        if(httpObj instanceof DefaultHttpResponse) {
            if(httpObj instanceof DefaultFullHttpResponse) {
                DefaultFullHttpResponse fullResp = (DefaultFullHttpResponse)httpObj;
                data = getByteArray(fullResp.content());
            }
            DefaultHttpResponse resp = (DefaultHttpResponse) httpObj;
            if(data!=null) {
                String contentType = resp.headers().get(HttpHeaders.Names.CONTENT_TYPE);
                if(MediaType.JSON.is(contentType)) {
                    RestExceptionMessage msg =  fromJson(new String(data), SchemaWeb.RestExceptionMessage.MERGE).build();
                    exception = new YamcsApiException(msg.getType()+" : "+msg.getMsg());
                } else if (MediaType.PROTOBUF.is(contentType)) {
                    RestExceptionMessage msg = RestExceptionMessage.parseFrom(data);
                    exception = new YamcsApiException(msg.getType()+" : "+msg.getMsg());
                } else {
                    exception = new YamcsApiException(new String(data));    
                }
            } else {
                exception = new YamcsApiException("Received http response: "+resp.getStatus());
            }
        } else {
            exception = new YamcsApiException("Received http response: "+httpObj);
        }
        return exception;

    }
    /**
     * Sets the maximum size of the responses - this is not applicable to bulk requests whose response is practically unlimited and delivered piece by piece
     * @param length
     */
    public void setMaxResponseLength(long length) {
        this.maxResponseLength = length;
    }
    /**
     * Perform a request that potentially retrieves large amount of data. The data is forwarded to the client. 
     * 
     * @param url
     * @param httpMethod
     * @param body
     * @param authToken
     * @param receiver - send all the data to this receiver. To find out when the request has been finished, the Future has to be used
     * @return a future indicating when the operation is completed.
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    public CompletableFuture<Void> doBulkRequest(String url, HttpMethod httpMethod, byte[] body, AuthenticationToken authToken, BulkRestDataReceiver receiver) throws URISyntaxException {
        BulkChannelHandler channelHandler = new BulkChannelHandler(receiver);

        ChannelFuture chf = setupChannel(url ,channelHandler);
        HttpRequest request = setupRequest(url, httpMethod, body, authToken);
        CompletableFuture<Void> cf = new CompletableFuture<Void>();

        chf.addListener(f->{
            Channel ch = chf.channel();
            ch.writeAndFlush(request);
            ChannelFuture closeFuture = ch.closeFuture();
            closeFuture.addListener(f1-> {
                if(channelHandler.exception!=null) {
                    cf.completeExceptionally(channelHandler.exception);
                } else {
                    cf.complete(null);
                }
            });

        });
        cf.whenComplete((v, t) -> {
            if(t instanceof CancellationException) {
                chf.channel().close();
            }
        });
        return cf;
    }

    public CompletableFuture<Void> doBulkRequest(String url, HttpMethod httpMethod, String body, AuthenticationToken authToken, BulkRestDataReceiver receiver) throws URISyntaxException {
        return doBulkRequest(url, httpMethod, body.getBytes(), authToken, receiver);
    }

    private ChannelFuture setupChannel(String url, SimpleChannelInboundHandler<HttpObject> channelHandler) throws URISyntaxException {
        uri = new URI(url);
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = 80;
        }

        if (!"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only HTTP is supported.");
        }


        if(group==null) {
            group = new NioEventLoopGroup();
        }
        
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpContentDecompressor());
                p.addLast(channelHandler);
            }
        });
        return b.connect(host, port);
    }


    private HttpRequest setupRequest(String url, HttpMethod httpMethod, byte[] body, AuthenticationToken authToken) throws URISyntaxException {
        uri = new URI(url);
        String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
        String fullUri = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            fullUri += "?" + uri.getRawQuery();
        }
        ByteBuf content = null;
        if(body!=null) {
            content = Unpooled.copiedBuffer(body);
        } else {
            content = Unpooled.EMPTY_BUFFER;
        }

        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, fullUri, content);
        request.headers().set(HttpHeaders.Names.HOST, host);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        request.headers().set(HttpHeaders.Names.CONTENT_TYPE, sendMediaType);
        request.headers().set(HttpHeaders.Names.ACCEPT, acceptMediaType);

        if(authToken != null) {
            if(authToken instanceof UsernamePasswordToken) {
                UsernamePasswordToken up = (UsernamePasswordToken)authToken;
                String credentialsClear = up.getUsername();
                if(up.getPasswordS() != null)
                    credentialsClear += ":" + up.getPasswordS();
                String credentialsB64 = new String(Base64.getEncoder().encode(credentialsClear.getBytes()));
                String authorization = "Basic " + credentialsB64;
                request.headers().set(HttpHeaders.Names.AUTHORIZATION, authorization);
            } else {
                throw new RuntimeException(authToken.getClass()+" not supported");
            }
        }
        return request;
    }


    public MediaType getSendMediaType() {
        return sendMediaType;
    }

    public void setSendMediaType(MediaType sendMediaType) {
        this.sendMediaType = sendMediaType;
    }

    public MediaType getAcceptMediaType() {
        return acceptMediaType;
    }

    public void setAcceptMediaType(MediaType acceptMediaType) {
        this.acceptMediaType = acceptMediaType;
    }
    
    public void close() {
            group.shutdownGracefully();
    }



    static <T extends MessageLite.Builder> T fromJson(String jsonstr, Schema<T> schema) throws IOException {
        StringReader reader = new StringReader(jsonstr);
        T msg = schema.newMessage();
        JsonIOUtil.mergeFrom(reader, msg, schema, false);
        return msg;
    }

    static byte[] getByteArray(ByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        return b;
    }

    class AccumulatingChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
        ByteBuf result = Unpooled.buffer();
        Throwable exception;
        HttpResponse httpResponse;

        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof HttpResponse) {
                httpResponse = (HttpResponse) msg;
            } else if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                result.writeBytes(content.content());
                if (content instanceof LastHttpContent) {
                    ctx.close();
                } else if(result.readableBytes()>maxResponseLength) {
                    exception = new IOException("Response too large, max accepted size: "+maxResponseLength);
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            exception = cause;
            ctx.close();
        }
    }



    static class BulkChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
        final BulkRestDataReceiver receiver;
        Throwable exception;

        BulkChannelHandler(BulkRestDataReceiver receiver) {
            this.receiver = receiver;
        }
        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws IOException {
            if (msg instanceof HttpResponse) {
                HttpResponse resp = (HttpResponse) msg;
                if(resp.getStatus().code()!=HttpResponseStatus.OK.code()) {
                    exception = decodeException(msg, null);
                    receiver.receiveException(exception);
                    ctx.close();
                }
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                try {
                    receiver.receiveData(getByteArray(content.content()));
                } catch (YamcsApiException e) {
                    exceptionCaught(ctx, e);
                }
                if (content instanceof LastHttpContent) {
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            receiver.receiveException(cause);
            exception = cause;
            ctx.close();
        }
    }
}
