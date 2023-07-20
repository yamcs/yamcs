package org.yamcs.yfe;

import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.yfe.protobuf.Yfe.MessageType;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

public class TcLink extends AbstractTcDataLink {
    final YfeLink parentLink;
    final int targetId;

    public TcLink(YfeLink yfeLink, int targetId) {
        this.parentLink = yfeLink;
        this.targetId = targetId;
    }

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
    }

    @Override
    public boolean sendCommand(PreparedCommand pc) {
        if (!parentLink.isConnected()) {
            return false;
        }

        byte[] binary = postprocess(pc);

        pc.setBinary(binary);

        var protoPc = ProtoConverter.toProto(pc);

        long time = getCurrentTime();

        parentLink.sendMessage((byte) MessageType.TC_VALUE, targetId, protoPc.toByteArray())
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
