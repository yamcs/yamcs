package org.yamcs.tse;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.time.TimeService;
import org.yamcs.tse.api.TseCommandResponse;
import org.yamcs.tse.api.TseCommanderMessage;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TseDataLinkInboundHandler extends SimpleChannelInboundHandler<TseCommanderMessage> {

    private static final Logger log = LoggerFactory.getLogger(TseDataLinkInboundHandler.class);

    private final TimeService timeService;
    private final Stream stream;
    private final Mdb mdb;
    private final CommandHistoryPublisher cmdhistPublisher;

    public TseDataLinkInboundHandler(CommandHistoryPublisher cmdhistPublisher, Mdb mdb, TimeService timeService,
            Stream stream) {
        this.cmdhistPublisher = cmdhistPublisher;
        this.timeService = timeService;
        this.stream = stream;
        this.mdb = mdb;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TseCommanderMessage message) throws Exception {
        if (message.hasCommandResponse()) {
            handleCommandResponse(message.getCommandResponse());
        }
        if (message.hasParameterData()) {
            handleParameterData(message.getParameterData());
        }
    }

    private void handleCommandResponse(TseCommandResponse cmdResponse) {
        if (cmdResponse.getSuccess()) {
            cmdhistPublisher.publishAck(cmdResponse.getId(), CommandHistoryPublisher.CommandComplete_KEY,
                    timeService.getMissionTime(), AckStatus.OK);
        } else {
            cmdhistPublisher.commandFailed(cmdResponse.getId(), timeService.getMissionTime(),
                    cmdResponse.getErrorMessage());
        }
    }

    private void handleParameterData(ParameterData pdata) {
        long now = timeService.getMissionTime();

        TupleDefinition tdef = null;
        String group = null;
        List<Object> cols = null;

        for (org.yamcs.protobuf.Pvalue.ParameterValue proto : pdata.getParameterList()) {
            String qualifiedName = proto.getId().getName();
            ParameterValue pv = BasicParameterValue.fromGpb(qualifiedName, proto);
            Parameter p = mdb.getParameter(qualifiedName);
            if (p == null) {
                log.warn("Ignoring unknown parameter {}", qualifiedName);
                continue;
            }
            String newGroup = p.getRecordingGroup();
            if ((group == null) || !group.equals(newGroup)) {
                if (cols != null) {
                    stream.emitTuple(new Tuple(tdef, cols));
                }
                group = newGroup;
                tdef = StandardTupleDefinitions.PARAMETER.copy();
                cols = new ArrayList<>(4 + pdata.getParameterCount());
                cols.add(now);
                cols.add(group);
                cols.add(pdata.getSeqNum());
                cols.add(now);
            }
            // Time of TSE Commander may not match mission time
            pv.setGenerationTime(now);
            int idx = tdef.getColumnIndex(qualifiedName);
            if (idx != -1) {
                log.warn("Duplicate value for {} \nfirst: {}" + "\n second: {} ", qualifiedName, cols.get(idx),
                        pv);
                continue;
            }
            tdef.addColumn(qualifiedName, DataType.PARAMETER_VALUE);
            cols.add(pv);
        }
        if (cols != null) {
            stream.emitTuple(new Tuple(tdef, cols));
        }
    }
}
