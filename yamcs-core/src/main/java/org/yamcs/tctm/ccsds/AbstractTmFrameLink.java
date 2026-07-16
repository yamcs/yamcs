package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.RawFrameDecoder;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;
import org.yamcs.tctm.ccsds.srs4.Srs4ConfigSpec;
import org.yamcs.time.Instant;
import org.yamcs.utils.YObjectLoader;

public abstract class AbstractTmFrameLink extends AbstractLink implements AggregatedDataLink {
    // all the TM frame links should move the TM frame config under this section, to allow having both TM and TC frame
    // in the same link
    final public static String TM_FRAME_CONFIG_SECTION = "tmFrameConfig";

    protected List<Link> subLinks;
    protected MasterChannelFrameHandler frameHandler;
    protected AtomicLong validFrameCount = new AtomicLong(0);
    protected AtomicLong invalidFrameCount = new AtomicLong(0);

    protected long errFrameCount;
    protected RawFrameDecoder rawFrameDecoder;
    protected TmFrameDecapsulator frameDecapsulator;

    @Override
    public Spec getDefaultSpec() {
        Spec spec = super.getDefaultSpec();
        addDefaultOptions(spec);
        return spec;
    }

    public static Spec addDefaultOptions(Spec spec) {
        spec.addOption("frameType", OptionType.STRING).withChoices(CcsdsFrameType.class);

        Spec frameEncryptionSpec = new Spec();
        frameEncryptionSpec.addOption("class", OptionType.STRING).withRequired(true);
        frameEncryptionSpec.addOption("args", OptionType.ANY);
        frameEncryptionSpec.addOption("spi", OptionType.INTEGER).withRequired(true);
        frameEncryptionSpec.addOption("authMask", OptionType.STRING);

        spec.addOption("encryption", OptionType.LIST).withElementType(OptionType.MAP).withSpec(frameEncryptionSpec);

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

        Spec decapsulatorSpec = new Spec();
        decapsulatorSpec.addOption("class", OptionType.STRING).withRequired(true);

        // When using SRS4 decapsulator only
        decapsulatorSpec.addOption("args", OptionType.MAP).withSpec(Srs4ConfigSpec.providerArgsSpec(false));
        decapsulatorSpec.when("class", Srs4ConfigSpec.TM_CLASS).requireAll("args");

        spec.addOption("frameDecapsulation", OptionType.MAP).withSpec(decapsulatorSpec);

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

        if (config.containsKey("frameDecapsulation")) {
            YConfiguration dc = config.getConfig("frameDecapsulation");
            YConfiguration args = dc.containsKey("args") ? dc.getConfig("args") : YConfiguration.emptyConfig();
            Object provider = YObjectLoader.loadObject(dc.getString("class"), args);
            if (!(provider instanceof TmFrameDecapsulator)) {
                throw new ConfigurationException("Frame decapsulation class " + provider.getClass().getName()
                        + " does not implement " + TmFrameDecapsulator.class.getName());
            }
            frameDecapsulator = (TmFrameDecapsulator) provider;
            var vcIds = frameHandler.getVirtualChannelIds();
            frameDecapsulator.validate(frameHandler.getMaxFrameSize(), vcIds);
        }

        if (dfl != -1) {
            int mindfl = frameHandler.getMinFrameSize();
            int maxdfl = frameHandler.getMaxFrameSize() + getFrameDecapsulationOverhead();
            if (dfl < mindfl || dfl > maxdfl) {
                throw new ConfigurationException("Raw frame decoder output frame length " + dfl +
                        " does not match the defined frame length including decapsulation overhead "
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
            Integer expectedVcId = null;
            if (rawFrameDecoder != null) {
                length = rawFrameDecoder.decodeFrame(data, offset, length);
                if (length == -1) {
                    log.debug("Error decoding frame");
                    errFrameCount++;
                    return;
                }
            }
            if (frameDecapsulator != null) {
                var frame = frameDecapsulator.decapsulate(data, offset, length);
                data = frame.data();
                offset = frame.offset();
                length = frame.length();
                expectedVcId = frame.expectedVirtualChannelId();
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

            frameHandler.handleFrame(ert, data, offset, length, expectedVcId);

            validFrameCount.getAndIncrement();
        } catch (TcTmException e) {
            eventProducer.sendWarning("Error processing frame: " + e);
            invalidFrameCount.getAndIncrement();
        }
    }

    protected int getFrameDecapsulationOverhead() {
        return frameDecapsulator == null ? 0 : frameDecapsulator.maxFrameOverhead();
    }

    protected int getMaximumInputFrameLength() {
        if (rawFrameDecoder != null && rawFrameDecoder.encodedFrameLength() != -1) {
            return rawFrameDecoder.encodedFrameLength();
        }
        return frameHandler.getMaxFrameSize() + getFrameDecapsulationOverhead();
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

    public SdlsSecurityAssociation getSdls(short spi) {
        DownlinkManagedParameters.SdlsInfo sdlsInfo = this.frameHandler.params.sdlsSecurityAssociations.get(spi);
        if (sdlsInfo != null)
            return sdlsInfo.sa();
        return null;
    }

    public void setSpis(int vcId, short[] spis) {
        this.frameHandler.params.getVcParams(vcId)
                .encryptionSpis = spis;
    }

    public Collection<Short> getSpis() {
        return this.frameHandler.params.sdlsSecurityAssociations.keySet();
    }
}
