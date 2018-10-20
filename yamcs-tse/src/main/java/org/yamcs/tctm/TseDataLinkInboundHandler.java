package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.time.TimeService;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TseDataLinkInboundHandler extends SimpleChannelInboundHandler<ParameterData> {

    private static final Logger log = LoggerFactory.getLogger(TseDataLinkInboundHandler.class);

    private final TimeService timeService;
    private final Stream stream;
    private final XtceDb xtcedb;

    public TseDataLinkInboundHandler(XtceDb xtcedb, TimeService timeService, Stream stream) {
        this.timeService = timeService;
        this.stream = stream;
        this.xtcedb = xtcedb;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ParameterData pdata) throws Exception {
        long now = timeService.getMissionTime();

        TupleDefinition tdef = null;
        String group = null;
        List<Object> cols = null;

        for (org.yamcs.protobuf.Pvalue.ParameterValue proto : pdata.getParameterList()) {
            String qualifiedName = proto.getId().getName();
            ParameterValue pv = ParameterValue.fromGpb(qualifiedName, proto);
            Parameter p = xtcedb.getParameter(qualifiedName);
            if (p == null) {
                log.warn("Ignorning unknown parameter {}", qualifiedName);
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
                log.warn("duplicate value for {} \nfirst: {}" + "\n second: {} ", pv.getParameter(), cols.get(idx),
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
