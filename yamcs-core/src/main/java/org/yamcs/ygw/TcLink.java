package org.yamcs.ygw;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.ygw.protobuf.Ygw.MessageType;


public class TcLink extends AbstractTcDataLink {
    final YgwLink parentLink;
    final int nodeId;
    

    public TcLink(YgwLink yfeLink, int targetId) {
        this.parentLink = yfeLink;
        this.nodeId = targetId;
    }

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        if (!parentLink.isConnected()) {
            return false;
        }

        pc.setBinary(postprocess(pc));

        var protoPc = ProtoConverter.toProto(pc);

        long time = getCurrentTime();

        parentLink.sendMessage((byte) MessageType.TC_VALUE, nodeId, protoPc.toByteArray())
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
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
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
    public AggregatedDataLink getParent() {
        return parentLink;
    }

}
