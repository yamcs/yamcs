package org.yamcs.ygw;


import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractTmDataLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.TimeEncoding;

import io.netty.buffer.ByteBuf;

public class TmLink extends AbstractTmDataLink {
    YgwLink parentLink;

    public TmLink(YgwLink yfeLink) {
        this.parentLink = yfeLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
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

    public void processMessage(ByteBuf buf) {
        if (isEffectivelyDisabled()) {
            log.trace("Ignoring message because the link is disabled");
            return;
        }
        long rectime = timeService.getMissionTime();

        long millis = buf.readLong();
        int picos = buf.readInt();

        org.yamcs.time.Instant ert = TimeEncoding.fromUnixPicos(millis, picos);

        byte[] pktData = new byte[buf.readableBytes()];
        buf.readBytes(pktData);

        TmPacket pkt = new TmPacket(rectime, pktData);
        pkt.setEarthReceptionTime(ert);


        packetCount.incrementAndGet();
        pkt = packetPreprocessor.process(pkt);
        if (pkt != null) {
            processPacket(pkt);
        }

    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }
}
