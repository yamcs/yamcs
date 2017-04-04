package org.yamcs.web;

import java.util.List;

import org.yamcs.api.MediaType;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Better version of {@link HttpContentCompressor}. Netty's HttpContentCompressor fails for chunked writes
 * <p>
 * The main reason for compressing is that it significantly reduces size of textual api responses (json), which are
 * being used more often than protobuf these days.
 * <p>
 * Should be revisited with Netty upgrades, to see if this workaround is still needed.
 * 
 * @see <a href="http://stackoverflow.com/questions/20136334/netty-httpstaticfileserver-example-not-working-with-httpcontentcompressor"> this post</a> 
 */
public class SmartHttpContentCompressor extends HttpContentCompressor {

    private static final int MIN_COMPRESSABLE_CONTENT_LENGTH = 1024;

    /*
    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, List<Object> out)
            throws Exception {
        System.out.println("================ dencoding http message "+msg);
        super.decode(ctx, msg, out);
    }
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        System.out.println("=============== encoding http message "+msg);
        super.encode(ctx, msg, out);
    }
    
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        System.out.println("=============== writing msg: "+msg);
        if(msg instanceof HttpChunkedInput) {
            System.out.println("length of chunk is "+((HttpChunkedInput)msg).length());
        }
        super.write(ctx, msg, promise);
    }
    
    */
    @Override
    protected Result beginEncode(HttpResponse res, String acceptEncoding) throws Exception {
        
        Result r = super.beginEncode(res, acceptEncoding);
        System.out.println("in smart compressor returning: "+r.contentEncoder()+" with handlers ");
        
        return r;
        /*
        if (!(res instanceof FullHttpResponse)) {
            if (!HttpHeaderValues.CHUNKED.equals(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING))) {
                return null;
            }
        }

        String contentType = res.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (MediaType.PROTOBUF.is(contentType)) { // Already compressed
            return null;
        }
        // If the content length is less than 1 kB but known, skip compression
        if (res.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            int contentLength = Integer.parseInt(res.headers().get(HttpHeaderNames.CONTENT_LENGTH));
            if (contentLength > 0 && contentLength < MIN_COMPRESSABLE_CONTENT_LENGTH) {
                return null;
            }
        }
        System.out.println("starting encdoing :"+acceptEncoding);
        return super.beginEncode(res, acceptEncoding);*/
    }
}
