package org.yamcs.web;

import org.yamcs.api.MediaType;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Better version of {@link HttpContentCompressor}. Netty's HttpContentCompressor fails for chunked writes
 * <p>
 * The main reason for compressing is that it significantly reduces size of textual api responses (json), which are
 * being used more often than protobuf these days.
 * <p>
 * Should be revisited with Netty upgrades, to see if this workaround is still needed.
 * 
 * @see http://stackoverflow.com/questions/20136334/netty-httpstaticfileserver-example-not-working-with-httpcontentcompressor 
 */
public class SmartHttpContentCompressor extends HttpContentCompressor {

    private static final int MIN_COMPRESSABLE_CONTENT_LENGTH = 1024;

    @Override
    protected Result beginEncode(HttpResponse res, String acceptEncoding) throws Exception {
        if (!(res instanceof FullHttpResponse)) {
            if (!Values.CHUNKED.equals(res.headers().get(Names.TRANSFER_ENCODING))) {
                return null;
            }
        }

        String contentType = res.headers().get(Names.CONTENT_TYPE);
        if (MediaType.PROTOBUF.is(contentType)) { // Already compressed
            return null;
        }
        // If the content length is less than 1 kB but known, skip compression
        if (res.headers().contains(Names.CONTENT_LENGTH)) {
            int contentLength = Integer.parseInt(res.headers().get(Names.CONTENT_LENGTH));
            if (contentLength > 0 && contentLength < MIN_COMPRESSABLE_CONTENT_LENGTH) {
                return null;
            }
        }

        return super.beginEncode(res, acceptEncoding);
    }
}
