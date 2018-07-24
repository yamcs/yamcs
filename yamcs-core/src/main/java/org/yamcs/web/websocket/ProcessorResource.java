package org.yamcs.web.websocket;

import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ProcessorSubscriptionRequest;
import org.yamcs.protobuf.Web.ProcessorSubscriptionResponse;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * Provides lifecycle updates on one or all processors.
 */
public class ProcessorResource extends AbstractWebSocketResource implements ManagementListener {

    public static final String RESOURCE_NAME = "processor";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private volatile boolean subscribed;
    private boolean allProcessors;
    private boolean allInstances;

    private Processor processor;

    public ProcessorResource(ConnectedWebSocketClient client) {
        super(client);
        processor = client.getProcessor();
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            return processUnsubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     */
    private WebSocketReply processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        if (ctx.getData() != null) {
            ProcessorSubscriptionRequest req = decoder.decodeMessageData(ctx, ProcessorSubscriptionRequest.newBuilder())
                    .build();
            allProcessors = req.hasAllProcessors() && req.getAllProcessors();
            allInstances = req.hasAllInstances() && req.getAllInstances();
        }
        WebSocketReply reply = new WebSocketReply(ctx.getRequestId());
        ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
        ProcessorSubscriptionResponse response = ProcessorSubscriptionResponse.newBuilder()
                .setProcessor(pinfo)
                .build();
        reply.attachData("ProcessorSubscriptionResponse", response);
        wsHandler.sendReply(reply);

        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        return null;
    }

    @Override
    public void socketClosed() {
        ManagementService.getInstance().removeManagementListener(this);
        subscribed = false;
    }

    @Override
    public void processorAdded(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        this.processor = processor;
        if (subscribed) {
            if (!allInstances && !allProcessors) {
                ProcessorInfo processorInfo = ManagementGpbHelper.toProcessorInfo(processor);
                wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
            }
        }
    }

    @Override
    public void unselectProcessor() {
        processor = null;
    }

    @Override
    public void clientRegistered(ConnectedClient client) {
    }

    @Override
    public void clientInfoChanged(ConnectedClient client) {
    }

    @Override
    public void clientUnregistered(ConnectedClient client) {
    }

    @Override
    public void statisticsUpdated(Processor processor, Statistics stats) {
    }
}
