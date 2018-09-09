package org.yamcs.tse;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Tse.CommandDeviceRequest;
import org.yamcs.protobuf.Tse.CommandDeviceResponse;

import com.google.common.util.concurrent.ListenableFuture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class RpcServerHandler extends SimpleChannelInboundHandler<CommandDeviceRequest> {

    private static final Logger log = LoggerFactory.getLogger(RpcServerHandler.class);

    private DeviceManager deviceManager;

    public RpcServerHandler(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("RPC client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CommandDeviceRequest request) throws Exception {
        CommandDeviceResponse.Builder responseb = CommandDeviceResponse.newBuilder();
        String command = request.getMessage();

        Device device = deviceManager.getDevice("rigol" /* TODO */);
        if (device == null) {
            ctx.write(responseb.build());
        } else {
            ListenableFuture<String> f = deviceManager.queueCommand(device, command);
            f.addListener(() -> {
                try {
                    String result = f.get();
                    if (result != null) {
                        responseb.setResult(result);
                    }
                } catch (ExecutionException e) {
                    responseb.setException(e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    responseb.setException("interrupted");
                }
                ctx.write(responseb.build());
            }, directExecutor());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("RPC client disconnected: " + ctx.channel().remoteAddress());
    }
}
