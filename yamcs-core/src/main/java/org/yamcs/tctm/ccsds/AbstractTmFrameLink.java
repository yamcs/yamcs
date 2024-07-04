package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.RawFrameDecoder;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;
import org.yamcs.time.Instant;

public abstract class AbstractTmFrameLink extends AbstractLink implements AggregatedDataLink {
    protected List<Link> subLinks;
    protected MasterChannelFrameHandler frameHandler;
    protected AtomicLong validFrameCount = new AtomicLong(0);
    protected AtomicLong invalidFrameCount = new AtomicLong(0);

    protected long errFrameCount;
    protected RawFrameDecoder rawFrameDecoder;

    @Override
    public Spec getDefaultSpec() {
        var spec = super.getDefaultSpec();

        spec.addOption("frameType", OptionType.STRING).withChoices(CcsdsFrameType.class);
        spec.addOption("clcwStream", OptionType.STRING);
        spec.addOption("goodFrameStream", OptionType.STRING);
        spec.addOption("badFrameStream", OptionType.STRING);

        spec.addOption("spacecraftId", OptionType.INTEGER);
        spec.addOption("physicalChannelName", OptionType.STRING);
        spec.addOption("errorDetection", OptionType.STRING);

        spec.addOption("frameLength", OptionType.INTEGER);
        spec.addOption("insertZoneLength", OptionType.INTEGER);
        spec.addOption("frameHeaderErrorControlPresent", OptionType.BOOLEAN);
        spec.addOption("virtualChannels", OptionType.LIST).withElementType(OptionType.ANY);
        spec.addOption("maxFrameLength", OptionType.INTEGER);
        spec.addOption("minFrameLength", OptionType.INTEGER);

        spec.addOption("rawFrameDecoder", OptionType.MAP).withSpec(Spec.ANY);

        return spec;
    }

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        int dfl = -1;
        if (config.containsKey("rawFrameDecoder")) {
            YConfiguration rconfig = config.getConfig("rawFrameDecoder");
            rawFrameDecoder = new CcsdsFrameDecoder(rconfig);
            dfl = rawFrameDecoder.decodedFrameLength();
        }

        frameHandler = new MasterChannelFrameHandler(yamcsInstance, name, config);

        if (dfl != -1) {
            int mindfl = frameHandler.getMinFrameSize();
            int maxdfl = frameHandler.getMinFrameSize();
            if (dfl < mindfl || dfl > maxdfl) {
                throw new ConfigurationException("Raw frame decoder output frame length " + dfl +
                        " does not match the defined frame length "
                        + (mindfl == maxdfl ? Integer.toString(mindfl) : "[" + mindfl + ", " + maxdfl + "]"));
            }
        }

        subLinks = new ArrayList<>();
        for (VcDownlinkHandler vch : frameHandler.getVcHandlers()) {
            if (vch instanceof Link) {
                Link l = (Link) vch;
                subLinks.add(l);
                l.setParent(this);
            }
        }
    }

    /**
     * sends a frame to the multiplexer, after decoding and derandomizing it (if necessary)
     * 
     * @param ert
     *            - earth reception time
     * @param data
     *            - buffer containing frame data
     * @param offset
     *            - offset in the buffer where the frame data starts
     * @param length
     *            - length of the frame data
     */
    protected void handleFrame(Instant ert, byte[] data, int offset, int length) {
        try {
            if (rawFrameDecoder != null) {
                length = rawFrameDecoder.decodeFrame(data, offset, length);
                if (length == -1) {
                    log.debug("Error decoding frame");
                    errFrameCount++;
                    return;
                }
            }

            if (length < frameHandler.getMinFrameSize()) {
                eventProducer.sendWarning("Error processing frame: size " + length
                        + " shorter than minimum allowed " + frameHandler.getMinFrameSize());
                errFrameCount++;
                return;
            }
            if (length > frameHandler.getMaxFrameSize()) {
                eventProducer.sendWarning("Error processing frame: size " + length + " longer than maximum allowed "
                        + frameHandler.getMaxFrameSize());
                errFrameCount++;
            }

            frameHandler.handleFrame(ert, data, offset, length);

            validFrameCount.getAndIncrement();
        } catch (TcTmException e) {
            eventProducer.sendWarning("Error processing frame: " + e.toString());
            invalidFrameCount.getAndIncrement();
        }
    }

    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

    @Override
    public void resetCounters() {
        super.resetCounters();
        validFrameCount.set(0);
        invalidFrameCount.set(0);
    }

}
