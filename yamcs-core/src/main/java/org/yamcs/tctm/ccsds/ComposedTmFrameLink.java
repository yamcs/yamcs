package org.yamcs.tctm.ccsds;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.DataLinkComponentLoader;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmChannelDecoder;
import org.yamcs.tctm.TmFrameTransport;

/** A CCSDS telemetry link assembled from transport, optional coding/transform, and protocol components. */
public class ComposedTmFrameLink extends AbstractTmFrameLink {
    private TmChannelDecoder channelDecoder;
    private TmFrameTransport transport;

    @Override
    public Spec getSpec() {
        Spec spec = getDefaultSpec();
        spec.addOption("composition", OptionType.STRING);
        spec.addOption("components", OptionType.MAP).withSpec(Spec.ANY).withRequired(true);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
        YConfiguration components = ComposedFrameLinkSupport.components(config);
        YConfiguration protocolConfig = ComposedFrameLinkSupport.protocolConfiguration(
                yamcsInstance, name, components, false);
        super.init(yamcsInstance, name, protocolConfig);

        if (components.containsKey("channelCoding")) {
            channelDecoder = DataLinkComponentLoader.load(components.getConfig("channelCoding"),
                    TmChannelDecoder.class, yamcsInstance, name, "channelCoding");
            validateChannelDecoderLength();
        }
        transport = DataLinkComponentLoader.load(components.getConfig("transport"), TmFrameTransport.class,
                yamcsInstance, name, "transport");
    }

    private void validateChannelDecoderLength() {
        ComposedFrameLinkSupport.validateDecodedFrameLength(channelDecoder.decodedFrameLength(),
                frameHandler.getMinFrameSize(), frameHandler.getMaxFrameSize(), getFrameDecapsulationOverhead());
    }

    private int maximumTransportFrameLength() {
        if (channelDecoder != null && channelDecoder.encodedFrameLength() != -1) {
            return channelDecoder.encodedFrameLength();
        }
        return frameHandler.getMaxFrameSize() + getFrameDecapsulationOverhead();
    }

    private void receiveFrame(byte[] data, int offset, int length) {
        dataIn(1, length);
        if (channelDecoder != null) {
            try {
                ByteBuffer decoded = channelDecoder.decode(ByteBuffer.wrap(data, offset, length).slice());
                length = decoded.remaining();
                if (decoded.hasArray() && !decoded.isReadOnly()) {
                    data = decoded.array();
                    offset = decoded.arrayOffset() + decoded.position();
                } else {
                    data = new byte[length];
                    decoded.duplicate().get(data);
                    offset = 0;
                }
            } catch (TcTmException e) {
                eventProducer.sendWarning("Error decoding physical-channel frame: " + e.getMessage());
                invalidFrameCount.incrementAndGet();
                return;
            }
        }
        handleFrame(timeService.getHresMissionTime(), data, offset, length);
    }

    @Override
    protected synchronized void doEnable() throws Exception {
        transport.start(maximumTransportFrameLength(), new TmFrameTransport.Receiver() {
            @Override
            public void onFrame(byte[] data, int offset, int length) {
                receiveFrame(data, offset, length);
            }

            @Override
            public void onInvalidFrame(String reason) {
                eventProducer.sendWarning(reason);
                invalidFrameCount.incrementAndGet();
            }

            @Override
            public void onFailure(Throwable cause) {
                if (isRunningAndEnabled()) {
                    notifyFailed(cause);
                }
            }
        });
    }

    @Override
    protected synchronized void doDisable() {
        transport.stop();
    }

    @Override
    protected void doStart() {
        try {
            if (!isDisabled()) {
                doEnable();
            }
            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        doDisable();
        notifyStopped();
    }

    @Override
    public String getDetailedStatus() {
        return isDisabled() ? "DISABLED (" + transport.getDetailedStatus() + ")" : "OK, "
                + transport.getDetailedStatus();
    }

    @Override
    public Map<String, Object> getExtraInfo() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("Valid frames", validFrameCount.get());
        extra.put("Invalid frames", invalidFrameCount.get());
        return extra;
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
