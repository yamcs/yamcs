package org.yamcs.tctm.ccsds;

import java.io.IOException;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.DataLinkComponentLoader;
import org.yamcs.tctm.TcChannelEncoder;
import org.yamcs.tctm.TcFrameTransport;
import org.yamcs.utils.StringConverter;

/** A CCSDS telecommand link assembled from protocol, optional transform/coding, and transport components. */
public class ComposedTcFrameLink extends AbstractTcFrameLink implements Runnable {
    private TcChannelEncoder channelEncoder;
    private TcFrameTransport transport;
    private Thread thread;

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
                yamcsInstance, name, components, true);
        super.init(yamcsInstance, name, protocolConfig);

        if (components.containsKey("channelCoding")) {
            channelEncoder = DataLinkComponentLoader.load(components.getConfig("channelCoding"),
                    TcChannelEncoder.class, yamcsInstance, name, "channelCoding");
        }
        transport = DataLinkComponentLoader.load(components.getConfig("transport"), TcFrameTransport.class,
                yamcsInstance, name, "transport");
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            UplinkTransferFrame frame = multiplexer.getFrame();
            if (frame == null) {
                continue;
            }
            byte[] data = frame.getData();
            if (log.isTraceEnabled()) {
                log.trace("Outgoing CCSDS frame: {}", StringConverter.arrayToHexString(data, true));
            }
            if (frameEncapsulator != null) {
                data = frameEncapsulator.encapsulate(frame);
                if (log.isTraceEnabled()) {
                    log.trace("Outgoing outer frame: {}", StringConverter.arrayToHexString(data, true));
                }
            }
            if (channelEncoder != null) {
                data = channelEncoder.encode(frame.getVirtualChannelId(), data);
                if (log.isTraceEnabled()) {
                    log.trace("Outgoing channel-coded frame: {}", StringConverter.arrayToHexString(data, true));
                }
            }

            try {
                transport.send(data);
                dataOut(1, data.length);
            } catch (IOException e) {
                log.warn("Error dispatching frame", e);
                if (frame.isBypass()) {
                    failBypassFrame(frame, e.getMessage());
                }
                transport.stop();
                notifyFailed(e);
                return;
            }
            if (frame.isBypass()) {
                ackBypassFrame(frame);
            }
            frameCount++;
        }
    }

    @Override
    protected synchronized void doEnable() throws Exception {
        transport.start();
        thread = new Thread(this, getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    @Override
    protected synchronized void doDisable() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
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
        multiplexer.quit();
        notifyStopped();
    }

    @Override
    public String getDetailedStatus() {
        return isDisabled() ? "DISABLED (" + transport.getDetailedStatus() + ")" : "OK, "
                + transport.getDetailedStatus();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
