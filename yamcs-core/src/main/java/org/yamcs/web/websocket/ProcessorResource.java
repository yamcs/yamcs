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
public class ProcessorResource implements WebSocketResource, ManagementListener {

    public static final String RESOURCE_NAME = "processor";

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed;
    private boolean allProcessors;
    private boolean allInstances;

    private Processor processor;

    public ProcessorResource(ConnectedWebSocketClient client) {
        this.client = client;
        processor = client.getProcessor();
    }

    /**
     * Registers for updates on any processor or client. Sends the current set of processor, and clients (in that order)
     * to the requester.
     */
    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
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
        client.sendReply(reply);

        ManagementService.getInstance().addManagementListener(this);
        subscribed = true;
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ManagementService.getInstance().removeManagementListener(this);
        client.sendReply(new WebSocketReply(ctx.getRequestId()));
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
        client.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void processorStateChanged(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        client.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void processorClosed(ProcessorInfo processorInfo) {
        if (!allInstances && !processorInfo.getInstance().equals(processor.getInstance())) {
            return;
        }
        if (!allProcessors && !processorInfo.getName().equals(processor.getName())) {
            return;
        }
        client.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        this.processor = processor;
        if (subscribed) {
            if (!allInstances && !allProcessors) {
                ProcessorInfo processorInfo = ManagementGpbHelper.toProcessorInfo(processor);
                client.sendData(ProtoDataType.PROCESSOR_INFO, processorInfo);
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
