package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.time.TimeService;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TseDataLinkInboundHandler extends SimpleChannelInboundHandler<ParameterData> {

    private static final Logger log = LoggerFactory.getLogger(TseDataLinkInboundHandler.class);

    private TimeService timeService;
    private Stream stream;

    public TseDataLinkInboundHandler(TimeService timeService, Stream stream) {
        this.timeService = timeService;
        this.stream = stream;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ParameterData pdata) throws Exception {
        long now = timeService.getMissionTime();

        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER.copy();
        List<Object> cols = new ArrayList<>(4 + pdata.getParameterCount());
        cols.add(now);
        cols.add(pdata.getGroup());
        cols.add(pdata.getSeqNum());
        cols.add(now);

        for (org.yamcs.protobuf.Pvalue.ParameterValue proto : pdata.getParameterList()) {
            String qualifiedName = proto.getId().getName();

            // Time of TSE Commander may not match mission time
            Pvalue.ParameterValue protoCopy = Pvalue.ParameterValue.newBuilder(proto)
                    .setGenerationTime(now)
                    .build();
            ParameterValue pv = ParameterValue.fromGpb(qualifiedName, protoCopy);
            int idx = tdef.getColumnIndex(qualifiedName);
            if (idx != -1) {
                log.warn("duplicate value for {} \nfirst: {}" + "\n second: {} ", pv.getParameter(), cols.get(idx),
                        pv);
                continue;
            }
            tdef.addColumn(qualifiedName, DataType.PARAMETER_VALUE);
            cols.add(pv);
        }

        Tuple t = new Tuple(tdef, cols);
        stream.emitTuple(t);
    }
}
