package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Web.ProcessorSubscriptionRequest;
import org.yamcs.protobuf.Web.ProcessorSubscriptionResponse;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * Provides lifecycle updates on one or all processors.
 * 
 * TODO this is currently undocumented while we migrate the api to official clients. It is however intended to
 * eventually deprecate and remove management/subscribe of processors. Clients should not use both in parallel because
 * they both emit PROCESSOR_INFO data.
 */
public class ProcessorResource extends AbstractWebSocketResource implements ManagementListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessorResource.class);
    public static final String RESOURCE_NAME = "processor";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private volatile boolean subscribed;
    private boolean allProcessors;
    private boolean allInstances;

    public ProcessorResource(WebSocketClient client) {
        super(client);
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
        try {
            WebSocketReply reply = new WebSocketReply(ctx.getRequestId());
            ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
            ProcessorSubscriptionResponse response = ProcessorSubscriptionResponse.newBuilder()
                    .setProcessor(pinfo)
                    .build();
            reply.attachData("ProcessorSubscriptionResponse", response);
            wsHandler.sendReply(reply);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    private WebSocketReply processUnsubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) {
        ManagementService.getInstance().removeManagementListener(this);
        try {
            wsHandler.sendReply(new WebSocketReply(ctx.getRequestId()));
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
        return null;
    }

    @Override
    public void quit() {
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
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        try {
            wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
        }
    }

    @Override
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        super.switchProcessor(oldProcessor, newProcessor);
        if (subscribed) {
            if (!allInstances && !allProcessors) {
                try {
                    ProcessorInfo processorInfo = ManagementGpbHelper.toProcessorInfo(newProcessor);
                    wsHandler.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
                } catch (IOException e) {
                    log.error("Exception when sending data", e);
                }
            }
        }
    }

    @Override
    public void clientRegistered(ClientInfo ci) {
    }

    @Override
    public void clientInfoChanged(ClientInfo ci) {
    }

    @Override
    public void clientUnregistered(ClientInfo ci) {
    }

    @Override
    public void statisticsUpdated(Processor processor, Statistics stats) {
    }
}
