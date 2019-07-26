package org.yamcs.http;

import java.util.List;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpContent;

public class HttpContentToByteBufDecoder extends MessageToMessageDecoder<HttpContent>{
    @Override
    protected void decode(ChannelHandlerContext ctx, HttpContent msg, List<Object> out) throws Exception {
        ByteBuf buf = msg.content();
        out.add(buf.retain());
    }
}
