package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.tctm.pus.tuples.Quattro;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.security.encryption.SymmetricEncryption;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.RawFrameEnDec;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;
import org.yamcs.time.Instant;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public abstract class AbstractTmFrameLink extends AbstractLink implements AggregatedDataLink {
    protected List<Link> subLinks;
    protected MasterChannelFrameHandler frameHandler;
    protected AtomicLong validFrameCount = new AtomicLong(0);
    protected AtomicLong invalidFrameCount = new AtomicLong(0);

    protected long errFrameCount;
    protected RawFrameEnDec rawFrameDecoder;

    // Redirection
    Map<byte[], Quattro<Integer, Integer, Stream, String>> redirection;  // offset, size, stream
    protected Map<String, AtomicLong> redirectionCounters = new HashMap<>();

    // srs3
    int stripHeader;

    // which error detection algorithm to use (null = no checksum)
    protected ErrorDetectionWordCalculator crc;

    // Encryption parameters
    protected SymmetricEncryption se;

    YarchDatabaseInstance ydb;

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
		ydb = YarchDatabase.getInstance(instance);

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

        if (config.containsKey("srs3")) {
            YConfiguration srs3 = config.getConfig("srs3");
            crc = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(srs3);

            if (srs3.containsKey("stripHeaders")) {
                YConfiguration sc = srs3.getConfig("stripHeaders");
                stripHeader = sc.getInt("length");
            }

            if (srs3.containsKey("encryption")) {
                YConfiguration en = srs3.getConfig("encryption");

                String className = en.getString("class");
                YConfiguration enConfig = en.getConfigOrEmpty("args");

                se = YObjectLoader.loadObject(className);
                se.init(enConfig);
            }
        }

        if (config.containsKey("redirection")) {
            redirection = new HashMap<>();

            List<YConfiguration> rConfig = config.getConfigList("redirection");
            for(YConfiguration yc: rConfig) {
                byte[] header = yc.getBinary("header");
                int offset = yc.getInt("offset");
                int size = yc.getInt("size");
                Stream stream = ydb.getStream(yc.getString("stream"));
                String rName = yc.getString("name");

                redirectionCounters.put(rName, new AtomicLong(0));
                redirection.put(header, new Quattro<>(offset, size, stream, rName));
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

            int dataEnd = offset + length;
            if (crc != null) {
                int crcSize = crc.sizeInBits() / 8;
                dataEnd -= crcSize;
                int c1 = crc.compute(data, offset, dataEnd - offset);

                int c2 = (int) ByteArrayUtils.decodeCustomInteger(data, dataEnd, crcSize);
                if (c1 != c2) {
                    throw new CorruptedFrameException("Bad SRS3 CRC computed: " + c1 + " in the frame: " + c2);
                }

                // Reduce length by removing CRC
                length -= crcSize;
            }

            if (se != null) {
                try {
                    data = se.decrypt(Arrays.copyOfRange(data, offset, offset + length));

                    // Update frame offset and length
                    offset = 0;
                    length = data.length;
                
                } catch (Exception e) {
                    throw new CorruptedFrameException("Bad decyrption: " + e.toString());
                }
            }

            if (redirection != null) {
                for(Map.Entry<byte[], Quattro<Integer, Integer, Stream, String>> mc: redirection.entrySet()) {
                    Quattro<Integer, Integer, Stream, String> value = mc.getValue();

                    int roffset = value.getFirst();
                    int rsize = value.getSecond();
                    Stream rstream = value.getThird();
                    String rName = value.getFourth();

                    byte[] contention = Arrays.copyOfRange(data, offset + roffset, offset + roffset + rsize);
                    if (Arrays.equals(mc.getKey(), contention)) {
                        data = Arrays.copyOfRange(data, offset + stripHeader, offset + length);
                        Tuple t = new Tuple(StandardTupleDefinitions.TM, new Object[] { null, null, timeService.getMissionTime(), null,
                                                data, timeService.getHresMissionTime(), null, linkName, null});
                        rstream.emitTuple(t);

                        // Update redirection counter
                        redirectionCounters.get(rName).incrementAndGet();
                        return;
                    }
                }
            }

            offset += stripHeader;
            length -= stripHeader;

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
