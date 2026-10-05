package org.yamcs.pus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Tuple;

/**
 * Native YAMCS implementation of PUS ST[09] — Time Management Service.
 * <p>
 * Handles:
 * <ul>
 *   <li>TC[9,1] — Set time report generation rate (rateExponent in 0..8)</li>
 * </ul>
 * Periodically emits TM[9,2] packets on APID=0 (no PUS secondary header) at a
 * rate of {@code 2^rateExponent} seconds.
 * <p>
 * Packet layout (17 bytes):
 * <pre>
 *   bytes  0-5:  CCSDS primary header (APID=0, no sec-hdr, TM)
 *   byte   6:    rateExponent (uint8)
 *   byte   7:    P-field = 0x2F  (CUC 1+4+3)
 *   bytes  8-11: OBT coarse (uint32, big-endian, seconds)
 *   bytes 12-14: OBT fine   (uint24, big-endian, 2^-24 s units)
 *   bytes 15-16: CRC-16-CCITT
 * </pre>
 * <p>
 * Register as a handler in {@code processor.yaml} under {@link PusCommandReleaser}:
 * <pre>
 *   handlers:
 *     - serviceType: 9
 *       class: org.yamcs.pus.Pus9Service
 *       args:
 *         defaultRateExponent: 2   # optional, default 2 → period = 4 s
 * </pre>
 *
 * Key implementation notes:
 *   - Pus9Service uses its own seqCounter (separate from APID=1 traffic, since TM[9,2] uses APID=0)
 *   - timeEncoder = new CucTimeEncoder(0x2F, false) writes the explicit P-field at byte 7, then 4 coarse + 3 fine bytes — matching exactly what the pus-time XTCE container expects
 *   - CRC accesses releaser.crcCalculator (package-private, same org.yamcs.pus package)
 *   - TM emission accesses releaser.tmStream directly, same as the other handlers already do via PusCommandReleaser.emitTm()
 *   - Rate changes are thread-safe: rateExponent is volatile, reschedule() is synchronized
 */

public class Pus9Service extends PusTcHandler {

    static final int SERVICE_TYPE = 9;
    // pfield 0x2F: CUC, basic=4 bytes, fractional=3 bytes, explicit pfield written
    static final int PFIELD = 0x2F;
    static final int PKT_LEN = 17;

    int defaultRateExponent;
    volatile int rateExponent;

    private CucTimeEncoder timeEncoder;
    private ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> scheduledFuture;
    private final AtomicInteger seqCounter = new AtomicInteger();

    @Override
    protected void doInit(YConfiguration config) {
        defaultRateExponent = config.getInt("defaultRateExponent", 2);
        rateExponent = defaultRateExponent;
        timeEncoder = new CucTimeEncoder(PFIELD, false);
    }

    @Override
    public void doStart() {
        executor = Executors.newSingleThreadScheduledExecutor();
        reschedule(rateExponent);
    }

    @Override
    public void doStop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Override
    public void handleTc(PreparedCommand pc) {
        byte[] bin = pc.getBinary();
        publishAckSent(pc);
        if (bin == null || bin.length < APP_DATA_OFFSET + 1) {
            publishCompletion(pc, false, "packet too short");
            return;
        }
        int subtype = PusPacket.getSubtype(bin);
        if (subtype == 1) {
            handleSetRate(pc, bin);
        } else {
            publishCompletion(pc, false, "unknown ST[09] subtype: " + subtype);
        }
    }

    private void handleSetRate(PreparedCommand pc, byte[] bin) {
        int exp = bin[APP_DATA_OFFSET] & 0xFF;
        if (exp > 8) {
            publishCompletion(pc, false, "rateExponent " + exp + " exceeds max 8");
            return;
        }
        rateExponent = exp;
        reschedule(exp);
        publishCompletion(pc, true, null);
    }

    private synchronized void reschedule(int exp) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        long periodSec = 1L << exp;
        scheduledFuture = executor.scheduleAtFixedRate(this::sendTimePacket, 0, periodSec, TimeUnit.SECONDS);
    }

    private void sendTimePacket() {
        long now = getCurrentTime();
        byte[] pkt = new byte[PKT_LEN];

        // CCSDS primary header: version=0, TM, no secondary header, APID=0
        pkt[0] = 0x00;
        pkt[1] = 0x00;
        int seq = seqCounter.getAndIncrement() & 0x3FFF;
        pkt[2] = (byte) (0xC0 | (seq >> 8));   // unsegmented (0xC0) | seq high bits
        pkt[3] = (byte) (seq & 0xFF);
        // data length = PKT_LEN - 6 - 1 = 10
        pkt[4] = 0x00;
        pkt[5] = 0x0A;

        pkt[6] = (byte) rateExponent;

        // writes pfield (1 byte) + coarse (4 bytes) + fine (3 bytes) = 8 bytes at offset 7
        timeEncoder.encode(now, pkt, 7);

        int crc = releaser.crcCalculator.compute(pkt, 0, PKT_LEN - 2);
        ByteArrayUtils.encodeUnsignedShort(crc, pkt, PKT_LEN - 2);

        int seqNum = ((pkt[2] & 0x3F) << 8) | (pkt[3] & 0xFF);
        Tuple t = new Tuple(StandardTupleDefinitions.TM,
                new Object[] { now, seqNum, now, 0, pkt, null, null, null, null });
        releaser.tmStream.emitTuple(t);
    }
}
