package org.yamcs.ygw;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.ygw.protobuf.Ygw.MessageType;

/**
 * Corresponds to a Node in the Yamcs Gateway or to a sub-link (in the gateway each node can have zero or more
 * sub-links)
 */
public class YgwNodeLink extends AbstractTcTmDataLink implements ParameterDataLink {
    final int nodeId;
    final int linkId;

    final YgwLink ygwLink;

    // only for sub-links
    YgwNodeLink parentLink;

    protected CommandHistoryPublisher commandHistoryPublisher;
    protected CommandPostprocessor cmdPostProcessor;
    final boolean tmEnabled;
    final boolean tcEnabled;
    final Map<Integer, YgwNodeLink> subLinks = new HashMap<>();

    public YgwNodeLink(YgwLink ygwLink, int nodeId, int linkId, boolean tmEnabled, boolean tcEnabled) {
        this.ygwLink = ygwLink;
        this.nodeId = nodeId;
        this.linkId = linkId;
        this.tmEnabled = tmEnabled;
        this.tcEnabled = tcEnabled;
    }

    @Override
    protected Status connectionStatus() {
        return ygwLink.connectionStatus();
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public void setParameterSink(ParameterSink parameterSink) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        if (!ygwLink.isConnected()) {
            return false;
        }

        byte[] binary = pc.getBinary();
        if (!pc.disablePostprocessing()) {
            binary = cmdPostProcessor.process(pc);
            if (binary == null) {
                log.warn("command postprocessor did not process the command");
            }
        }

        pc.setBinary(binary);

        var protoPc = ProtoConverter.toProto(pc);

        long time = getCurrentTime();

        ygwLink.sendMessage((byte) MessageType.TC_VALUE, nodeId, protoPc.toByteArray())
                .whenComplete((c, t) -> {
                    if (t != null) {
                        log.warn("Error sending command ", t);
                        failedCommand(pc.getCommandId(), t.getMessage());
                    } else {
                        commandHistoryPublisher.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, time, AckStatus.OK);
                    }
                });

        return true;
    }

    @Override
    public AggregatedDataLink getParent() {
        return ygwLink;
    }

    public boolean isTmPacketDataLinkImplemented() {
        return tmEnabled;
    }

    public boolean isTcPacketDataLinkImplemented() {
        return tcEnabled;
    }

}
