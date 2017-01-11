package org.yamcs.utils;

import java.net.URI;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.security.UsernamePasswordToken;

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
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

public class HttpClient {
    URI uri;
    Exception exception;
    StringBuilder result = new StringBuilder();

    //	public Future<String> doAsyncRequest(String url, HttpMethod httpMethod, String body) throws Exception {
    //		return doAsyncRequest(url, httpMethod, body, null);
    //	}

    public Future<String> doAsyncRequest(String url, HttpMethod httpMethod, String body,
            UsernamePasswordToken authToken) throws Exception {
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

        exception = null;
        result.setLength(0);

        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpContentDecompressor());
                p.addLast(new MyChannelHandler());
            }
        });

        Channel ch = b.connect(host, port).sync().channel();
        ByteBuf content = null;
        if(body!=null) {
            content = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        } else {
            content = Unpooled.EMPTY_BUFFER;
        }

        String fullUri = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            fullUri += "?" + uri.getRawQuery();
        }
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, fullUri, content);
        request.headers().set(HttpHeaders.Names.HOST, host);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        if(authToken != null) {
            String credentialsClear = authToken.getUsername();
            if(authToken.getPasswordS() != null)
                credentialsClear += ":" + authToken.getPasswordS();
            String credentialsB64 = new String(Base64.getEncoder().encode(credentialsClear.getBytes()));
            String authorization = "Basic " + credentialsB64;
            request.headers().set(HttpHeaders.Names.AUTHORIZATION, authorization);
        }

        ch.writeAndFlush(request).await(1, TimeUnit.SECONDS);

        ResultFuture rf = new ResultFuture(ch.closeFuture());

        return rf;
    }


    /*	public String doRequest(String url, HttpMethod httpMethod, String body) throws Exception {
		return doRequest(url, httpMethod, body, null);
	}*/
    public String doRequest(String url, HttpMethod httpMethod, String body, UsernamePasswordToken authToken) throws Exception {
        Future<String> f = doAsyncRequest(url, httpMethod, body, authToken);
        return f.get(5, TimeUnit.SECONDS);
    }
    /*	public String doGetRequest(String url, String body) throws Exception {
		return doGetRequest(url, body, null);
	}*/
    public String doGetRequest(String url, String body, UsernamePasswordToken authToken) throws Exception {
        return doRequest(url, HttpMethod.GET, body, authToken);
    }
    /*	public String doPostRequest(String url, String body) throws Exception {
		return doPostRequest(url, body, null);
	}*/
    public String doPostRequest(String url, String body, UsernamePasswordToken authToken) throws Exception {
        return doRequest(url, HttpMethod.POST, body, authToken);
    }

    class MyChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof HttpResponse) {
//                 HttpResponse response = (HttpResponse) msg;
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                result.append(content.content().toString(CharsetUtil.UTF_8));

                if (content instanceof LastHttpContent) {
                    ctx.close();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if(cause instanceof Exception) {
                exception = (Exception)cause;
            } else {
                exception = new RuntimeException(cause);
            }
            cause.printStackTrace();
            ctx.close();
        }
    }

    class ResultFuture implements Future<String> {
        ChannelFuture closeFuture;

        public ResultFuture(ChannelFuture closeFuture) {
            this.closeFuture = closeFuture;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return closeFuture.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return closeFuture.isCancelled();
        }

        @Override
        public boolean isDone() {
            return closeFuture.isDone();
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            closeFuture.await();
            if(HttpClient.this.exception!=null) {
                throw new ExecutionException(HttpClient.this.exception);
            }
            return HttpClient.this.result.toString();
        }

        @Override
        public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,	TimeoutException {
            if(closeFuture.await(timeout, unit)) {
                if(HttpClient.this.exception!=null) {
                    throw new ExecutionException(HttpClient.this.exception);
                }
            } else {
                throw new TimeoutException();
            }
            return HttpClient.this.result.toString();
        }
    }
}
