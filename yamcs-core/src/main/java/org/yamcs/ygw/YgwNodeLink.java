package org.yamcs.ygw;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.AbstractTcTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.tctm.GenericCommandPostprocessor;
import org.yamcs.tctm.ParameterDataLink;
import org.yamcs.tctm.ParameterSink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.ygw.protobuf.Ygw.MessageType;

public class YgwNodeLink  extends AbstractTcTmDataLink implements ParameterDataLink {
    final int nodeId;
    final int linkId;
    final YgwLink ygwLink;
    protected CommandHistoryPublisher commandHistoryPublisher;
    protected CommandPostprocessor cmdPostProcessor;
    
    public YgwNodeLink(YgwLink ygwLink, int nodeId, int linkId) {
        this.ygwLink = ygwLink;
        this.nodeId = nodeId;
        this.linkId = linkId;
    }
    
    @Override
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        // TODO Auto-generated method stub
    }

    @Override
    protected Status connectionStatus() {
        // TODO Auto-generated method stub
        return null;
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
}
