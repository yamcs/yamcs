package org.yamcs.pus;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.CommandComplete_KEY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamTcCommandReleaser;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Single {@link org.yamcs.commanding.CommandReleaser} for all PUS services.
 * <p>
 * Dispatches incoming TCs by PUS service type to registered {@link PusTcHandler} instances.
 * Handlers that are not registered fall through to {@link StreamTcCommandReleaser} (TC stream → data link).
 * <p>
 * Provides shared TM-building infrastructure ({@link #emitTm}) for all handlers.
 *
 * <pre>
 * processor.yaml snippet:
 *   - class: org.yamcs.pus.PusCommandReleaser
 *     args:
 *       apid: 1
 *       timeEncoding:
 *         implicitPfield: false
 *         pfield: 0x2f
 *       handlers:
 *         - serviceType: 5
 *           class: org.yamcs.pus.Pus5Service
 *           args:
 *             eventIdParameter: /PUS5/event_id
 * </pre>
 */
public class PusCommandReleaser extends StreamTcCommandReleaser {

    int apid;
    Stream tmStream;
    CucTimeEncoder timeEncoder;
    AtomicInteger seqCounter = new AtomicInteger();
    ErrorDetectionWordCalculator crcCalculator;
    Map<Integer, PusTcHandler> handlers = new HashMap<>();

    @Override
    public void init(Processor proc, YConfiguration config, Object spec) {
        super.init(proc, config, spec);

        apid = config.getInt("apid");

        if (config.containsKey("timeEncoding")) {
            timeEncoder = configureTimeEncoding(config.getConfig("timeEncoding"));
        } else {
            timeEncoder = new CucTimeEncoder(0x2e, true);
        }

        if (config.containsKey("errorDetection")) {
            crcCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
        } else {
            crcCalculator = new CrcCciitCalculator();
        }

        // Resolve TM stream for the realtime processor
        String yamcsInstance = proc.getInstance();
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        var ydb = YarchDatabase.getInstance(yamcsInstance);
        for (var sce : sc.getTmEntries()) {
            if (proc.getName().equals(sce.getProcessor())) {
                tmStream = ydb.getStream(sce.getName());
                break;
            }
        }
        if (tmStream == null) {
            throw new ConfigurationException("No TM stream found for processor '" + proc.getName() + "'");
        }

        // Instantiate and init handlers
        if (config.containsKey("handlers")) {
            List<YConfiguration> handlerList = config.getConfigList("handlers");
            for (YConfiguration hc : handlerList) {
                int serviceType = hc.getInt("serviceType");
                String className = hc.getString("class");
                YConfiguration handlerArgs = hc.containsKey("args")
                        ? hc.getConfig("args")
                        : YConfiguration.emptyConfig();
                PusTcHandler handler = YObjectLoader.loadObject(className);
                handler.init(this, handlerArgs);
                handlers.put(serviceType, handler);
            }
        }
    }

    @Override
    protected void doStart() {
        handlers.values().forEach(PusTcHandler::doStart);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        handlers.values().forEach(PusTcHandler::doStop);
        notifyStopped();
    }

    @Override
    public void releaseCommand(PreparedCommand pc) {
        byte[] bin = pc.getBinary();
        if (bin != null && bin.length >= 9) {
            int serviceType = PusPacket.getType(bin);
            PusTcHandler handler = handlers.get(serviceType);
            if (handler != null) {
                handler.handleTc(pc);
                return;
            }
        }
        super.releaseCommand(pc);
    }

    /**
     * Builds a PUS TM packet and emits it on the TM stream.
     * Bypasses {@link org.yamcs.pus.PusPacketPreprocessor} — gentime is set from {@link #getCurrentTime()}.
     */
    void emitTm(int serviceType, int subtype, byte[] appData) {
        byte[] pkt = buildPusTmPacket(serviceType, subtype, appData);
        long now = getCurrentTime();
        int seqNum = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
        // TM definition: gentime, seqNum, rectime, status, packet, ertime, obt, link, rootContainer
        Tuple t = new Tuple(StandardTupleDefinitions.TM,
                new Object[] { now, seqNum, now, 0, pkt, null, null, null, null });
        tmStream.emitTuple(t);
    }

    byte[] buildPusTmPacket(int serviceType, int subtype, byte[] appData) {
        int timeLen = timeEncoder.getEncodedLength();
        // 6 CCSDS + 1 PUS-ver + 1 type + 1 subtype + 2 counter + 2 dest + timeLen + appData + 2 CRC
        int totalLen = 6 + 7 + timeLen + appData.length + 2;
        byte[] pkt = new byte[totalLen];

        int seq = seqCounter.getAndIncrement() & 0x3FFF;

        // CCSDS primary header
        ByteArrayUtils.encodeUnsignedShort((1 << 11) | (apid & 0x7FF), pkt, 0); // TM, secondary header present
        ByteArrayUtils.encodeUnsignedShort((3 << 14) | seq, pkt, 2);            // unsegmented, seq count
        ByteArrayUtils.encodeUnsignedShort(totalLen - 7, pkt, 4);               // packet data length

        // PUS TM secondary header
        pkt[6] = 0x21; // PUS version=2, spacecraft time reference status=1
        pkt[7] = (byte) serviceType;
        pkt[8] = (byte) subtype;
        // [9..10] message type counter = 0, [11..12] destination ID = 0

        timeEncoder.encode(getCurrentTime(), pkt, 13);

        System.arraycopy(appData, 0, pkt, 13 + timeLen, appData.length);

        if (crcCalculator != null) {
            int crcPos = totalLen - 2;
            int crc = crcCalculator.compute(pkt, 0, crcPos);
            ByteArrayUtils.encodeUnsignedShort(crc, pkt, crcPos);
        }

        return pkt;
    }

    void publishAckSent(PreparedCommand pc) {
        processor.getCommandHistoryPublisher().publishAck(
                pc.getCommandId(), AcknowledgeSent_KEY, getCurrentTime(), AckStatus.OK);
    }

    void publishCompletion(PreparedCommand pc, boolean success, String msg) {
        processor.getCommandHistoryPublisher().publishAck(
                pc.getCommandId(), CommandComplete_KEY, getCurrentTime(),
                success ? AckStatus.OK : AckStatus.NOK, msg);
    }

    /**
     * Releases an embedded TC back to the TC stream, bypassing handler dispatch.
     * Used by handlers (e.g. ST[21]) that relay wrapped TCs.
     */
    void relayTc(PreparedCommand pc) {
        super.releaseCommand(pc);
    }

    long getCurrentTime() {
        return processor.getCurrentTime();
    }

    private CucTimeEncoder configureTimeEncoding(YConfiguration config) {
        boolean implicitPfield = config.getBoolean("implicitPfield", true);
        int pfield1 = config.getInt("pfield");
        int pfield2 = config.getInt("pfieldCont", -1);
        return new CucTimeEncoder(pfield1, pfield2, implicitPfield);
    }
}
