package org.yamcs.simulator.pus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ST[13] Large Packet Transfer simulator service. See pus_analysis/pus13.md and the NoteSet in
 * mdb/pus13.xml for the mission conventions used to work around XTCE's lack of a "deduced size"
 * field type.
 *
 * <p>
 * Downlink: no ground command triggers this (real spacecraft don't need one either -- it fires
 * whenever some other on-board subsystem has a large TM to send). Instead the simulator
 * periodically synthesizes a fake large payload on its own and downlinks it fragmented as
 * TM[13,1] (first) / TM[13,2] (intermediate) / TM[13,3] (last).
 *
 * <p>
 * Uplink: buffers TC[13,9] (first) / TC[13,10] (intermediate) / TC[13,11] (last) parts, checks
 * sequence continuity and a reception timer, and on successful reassembly dispatches the
 * reconstructed bytes as an embedded PUS TC packet via {@link PusSimulator#processTc}, giving it
 * its own independent ACK/NACK/completion chain. On timeout or sequence gap, emits TM[13,16].
 */
public class Pus13Service extends AbstractPusService {

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_NO_ACTIVE_UPLINK = 5;
    static final int COMPL_ERR_SEQUENCE_DISCONTINUITY = 6;
    static final int COMPL_ERR_RECONSTRUCTION_FAILED = 7;

    /** Raw failure_reason values, see pus13.xml. */
    static final int FAILURE_RECEPTION_TIMEOUT = 1;
    static final int FAILURE_SEQUENCE_DISCONTINUITY = 2;
    static final int FAILURE_INTERNAL_ERROR = 3;

    static final int PART_SIZE = 64;
    static final int RECEPTION_TIMEOUT_MS = 5000;
    static final int DOWNLINK_PERIOD_MS = 20000;
    static final int INTER_PART_DELAY_MS = 200;
    static final int DOWNLINK_MIN_SIZE = 150;
    static final int DOWNLINK_MAX_EXTRA = 150; // total downlink payload size in [150, 300]

    private final Random random = new Random();
    private int nextDownlinkTransactionId = 1;

    /** Single concurrent uplink reassembly (mission simplification, see pus13.xml NoteSet). */
    private UplinkState uplink;

    private static class UplinkState {
        final int transactionId;
        int nextSeq;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ScheduledFuture<?> timeoutTask;

        UplinkState(int transactionId, int firstSeq) {
            this.transactionId = transactionId;
            this.nextSeq = firstSeq + 1;
        }
    }

    Pus13Service(PusSimulator pusSimulator) {
        super(pusSimulator, 13);
    }

    @Override
    public void start() {
        pusSimulator.executor.scheduleAtFixedRate(this::startDownlinkSequence, DOWNLINK_PERIOD_MS,
                DOWNLINK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    // ---- Downlink (TM[13,1]/[13,2]/[13,3]) ----

    private void startDownlinkSequence() {
        int total = DOWNLINK_MIN_SIZE + random.nextInt(DOWNLINK_MAX_EXTRA + 1);
        byte[] payload = new byte[total];
        for (int i = 0; i < total; i++) {
            payload[i] = (byte) i; // incrementing pattern, makes reassembly easy to eyeball
        }

        int fullParts = total / PART_SIZE;
        int remainder = total % PART_SIZE;
        if (remainder == 0) {
            remainder = PART_SIZE;
            fullParts--;
        }

        int transactionId = nextDownlinkTransactionId;
        nextDownlinkTransactionId = (nextDownlinkTransactionId % 0xFFFF) + 1;

        log.info("ST13: downlinking large packet, transaction_id={}, total_size={}, parts={}",
                transactionId, total, fullParts + 1);

        long delay = 0;
        int seq = 1;
        for (int i = 0; i < fullParts; i++) {
            int subtype = (i == 0) ? 1 : 2;
            byte[] chunk = new byte[PART_SIZE];
            System.arraycopy(payload, i * PART_SIZE, chunk, 0, PART_SIZE);
            int seqNum = seq++;
            pusSimulator.executor.schedule(() -> sendFixedPart(subtype, transactionId, seqNum, chunk), delay,
                    TimeUnit.MILLISECONDS);
            delay += INTER_PART_DELAY_MS;
        }

        byte[] lastChunk = new byte[remainder];
        System.arraycopy(payload, fullParts * PART_SIZE, lastChunk, 0, remainder);
        int lastSeqNum = seq;
        pusSimulator.executor.schedule(() -> sendLastPart(transactionId, lastSeqNum, lastChunk), delay,
                TimeUnit.MILLISECONDS);
    }

    private void sendFixedPart(int subtype, int transactionId, int seqNum, byte[] chunk) {
        PusTmPacket pkt = newPacket(subtype, 2 + 2 + PART_SIZE);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) transactionId);
        bb.putShort((short) seqNum);
        bb.put(chunk);
        pusSimulator.transmitRealtimeTM(pkt);
    }

    private void sendLastPart(int transactionId, int seqNum, byte[] chunk) {
        PusTmPacket pkt = newPacket(3, 2 + 2 + 1 + chunk.length);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) transactionId);
        bb.putShort((short) seqNum);
        bb.put((byte) chunk.length);
        bb.put(chunk);
        pusSimulator.transmitRealtimeTM(pkt);
    }

    // ---- Uplink (TC[13,9]/[13,10]/[13,11]) ----

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        case 9 -> {
            ack_start(tc);
            handleFirstPart(tc);
        }
        case 10 -> {
            ack_start(tc);
            handleIntermediatePart(tc);
        }
        case 11 -> {
            ack_start(tc);
            handleLastPart(tc);
        }
        default -> {
            log.warn("Unknown ST[13] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    private void handleFirstPart(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int transactionId = bb.getShort() & 0xFFFF;
        int seqNum = bb.getShort() & 0xFFFF;
        byte[] chunk = new byte[PART_SIZE];
        bb.get(chunk);

        if (uplink != null) {
            log.warn("ST13: new uplink transaction_id={} started while transaction_id={} was still in "
                    + "progress, aborting the old one", transactionId, uplink.transactionId);
            abortUplink(FAILURE_INTERNAL_ERROR);
        }

        uplink = new UplinkState(transactionId, seqNum);
        uplink.buffer.write(chunk, 0, chunk.length);
        rescheduleTimeout();
        ack_completion(tc);
    }

    private void handleIntermediatePart(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int transactionId = bb.getShort() & 0xFFFF;
        int seqNum = bb.getShort() & 0xFFFF;
        byte[] chunk = new byte[PART_SIZE];
        bb.get(chunk);

        if (!validatePart(tc, transactionId, seqNum)) {
            return;
        }
        uplink.buffer.write(chunk, 0, chunk.length);
        uplink.nextSeq++;
        rescheduleTimeout();
        ack_completion(tc);
    }

    private void handleLastPart(PusTcPacket tc) {
        ByteBuffer bb = tc.getUserDataBuffer();
        int transactionId = bb.getShort() & 0xFFFF;
        int seqNum = bb.getShort() & 0xFFFF;
        int partLen = bb.get() & 0xFF;
        byte[] chunk = new byte[partLen];
        bb.get(chunk);

        if (!validatePart(tc, transactionId, seqNum)) {
            return;
        }
        cancelTimeout();
        uplink.buffer.write(chunk, 0, chunk.length);
        byte[] reconstructed = uplink.buffer.toByteArray();
        uplink = null;

        try {
            PusTcPacket embedded = new PusTcPacket(reconstructed);
            log.info("ST13: reconstructed large command, transaction_id={}, size={}, dispatching "
                    + "embedded TC type={} subtype={}", transactionId, reconstructed.length,
                    embedded.getType(), embedded.getSubtype());
            pusSimulator.processTc(embedded);
            ack_completion(tc);
        } catch (Exception e) {
            log.warn("ST13: failed to reconstruct/dispatch large command for transaction_id={}", transactionId, e);
            nack_completion(tc, COMPL_ERR_RECONSTRUCTION_FAILED);
            sendAbort(transactionId, FAILURE_INTERNAL_ERROR);
        }
    }

    private boolean validatePart(PusTcPacket tc, int transactionId, int seqNum) {
        if (uplink == null) {
            log.warn("ST13: received part for transaction_id={} with no active uplink, sending NACK completion",
                    transactionId);
            nack_completion(tc, COMPL_ERR_NO_ACTIVE_UPLINK);
            return false;
        }
        if (transactionId != uplink.transactionId || seqNum != uplink.nextSeq) {
            log.warn("ST13: sequence discontinuity (active transaction_id={}, expected seq={}, got "
                    + "transaction_id={}, seq={})", uplink.transactionId, uplink.nextSeq, transactionId, seqNum);
            nack_completion(tc, COMPL_ERR_SEQUENCE_DISCONTINUITY);
            abortUplink(FAILURE_SEQUENCE_DISCONTINUITY);
            return false;
        }
        return true;
    }

    private void rescheduleTimeout() {
        cancelTimeout();
        UplinkState state = uplink;
        state.timeoutTask = pusSimulator.executor.schedule(() -> onReceptionTimeout(state), RECEPTION_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
    }

    private void cancelTimeout() {
        if (uplink != null && uplink.timeoutTask != null) {
            uplink.timeoutTask.cancel(false);
        }
    }

    private void onReceptionTimeout(UplinkState state) {
        if (uplink != state) {
            return; // already completed/aborted/replaced
        }
        log.warn("ST13: reception timeout for transaction_id={}", state.transactionId);
        uplink = null;
        sendAbort(state.transactionId, FAILURE_RECEPTION_TIMEOUT);
    }

    private void abortUplink(int failureReason) {
        if (uplink == null) {
            return;
        }
        cancelTimeout();
        UplinkState state = uplink;
        uplink = null;
        sendAbort(state.transactionId, failureReason);
    }

    private void sendAbort(int transactionId, int failureReason) {
        PusTmPacket pkt = newPacket(16, 2 + 1);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.putShort((short) transactionId);
        bb.put((byte) failureReason);
        pusSimulator.transmitRealtimeTM(pkt);
    }
}
