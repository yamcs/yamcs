package org.yamcs.simulator.pus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

/**
 * ST[21] Request Sequencing Service simulator. See pus_analysis/pus21.md for the full design
 * rationale.
 *
 * <p>
 * Emulates the on-board SEQUENCE_STORE: a map of request-sequence-ID to {@link RequestSequence},
 * each holding an ordered list of {@code {rawTc, delayMs}} entries. Activation releases entry 0
 * immediately, then reschedules itself on {@link PusSimulator#executor} after each entry's delay
 * -- the same cancellable {@code ScheduledFuture} chain idiom {@code Pus11Service} uses for
 * time-based scheduling, so abort ({@link #abort}) is immediate (no in-flight sleep to wait out).
 *
 * <p>
 * Embedded TC packets (TC[21,1] entries, TM[21,12] entries) carry no length prefix -- each is a
 * complete, self-length-delimited CCSDS/PUS packet, found the same way
 * {@code Pus11Service.insertActivities()} finds TC[11,4]'s embedded commands: read the packet's
 * own primary-header length field (bytes 4-5), total size = that value + 7.
 *
 * <p>
 * TC[21,2]/TC[21,8] (load by reference) have no real on-board filesystem to read from -- this
 * simulator has no ST[23] File Management service. A small in-memory {@code fileRepo} is seeded
 * at construction to stand in for a filesystem provisioned out-of-band, so both subtypes are
 * exercisable in tests instead of being unconditionally rejected.
 */
public class Pus21Service extends AbstractPusService {

    // completion errors (see AbstractPusService for the shared ones)
    static final int COMPL_ERR_SEQ_ALREADY_LOADED = 3;
    static final int COMPL_ERR_SEQ_NOT_FOUND = 4;
    static final int COMPL_ERR_SEQ_NOT_UNLOADABLE = 5;
    static final int COMPL_ERR_SEQ_NOT_ACTIVATABLE = 6;
    static final int COMPL_ERR_SEQ_NOT_ABORTABLE = 7;
    static final int COMPL_ERR_FILE_NOT_FOUND = 8;
    static final int COMPL_ERR_FILE_NOT_RECOGNIZED = 9; // §6.21.5.3.d.4 / §6.21.5.6.d.4, currently unreachable

    private static final int SEQ_ID_LEN = 16;
    private static final String DEFAULT_REPO_PATH = "/seq";
    private static final CrcCciitCalculator CRC = new CrcCciitCalculator();

    enum Status {
        INACTIVE, UNDER_EXECUTION
    }

    static class Entry {
        final byte[] rawTc;
        final int delayMs;

        Entry(byte[] rawTc, int delayMs) {
            this.rawTc = rawTc;
            this.delayMs = delayMs;
        }
    }

    static class RequestSequence {
        final String id;
        final List<Entry> entries;
        Status status = Status.INACTIVE;
        int nextIndex;
        ScheduledFuture<?> pending;

        RequestSequence(String id, List<Entry> entries) {
            this.id = id;
            this.entries = entries;
        }
    }

    private final Map<String, RequestSequence> sequenceStore = new LinkedHashMap<>();
    private final Map<String, List<Entry>> fileRepo = new LinkedHashMap<>();

    Pus21Service(PusSimulator pusSimulator) {
        super(pusSimulator, 21);
        seedFileRepo();
    }

    private void seedFileRepo() {
        // Demo file for TC[21,2]/[21,8] round-trip testing without needing TC[21,1] first.
        fileRepo.put(DEFAULT_REPO_PATH + "/demo-seq-1.bin", List.of(
                new Entry(buildPingTc(), 500),
                new Entry(buildPingTc(), 500)));
    }

    /** Minimal self-contained TC[17,1] "are you alive" ping, used only to seed the demo file. */
    private byte[] buildPingTc() {
        PusTcPacket ping = new PusTcPacket(PusSimulator.MAIN_APID, 0, 7, 17, 1);
        ping.fillChecksum();
        return ping.getBytes();
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        switch (tc.getSubtype()) {
        // TC[21,1] direct-load a request sequence
        case 1 -> loadDirectly(tc);
        // TC[21,2] load a request sequence by reference
        case 2 -> loadByReference(tc, false);
        // TC[21,3] unload a request sequence
        case 3 -> unloadSequence(tc);
        // TC[21,4] activate a request sequence
        case 4 -> activateSequence(tc);
        // TC[21,5] abort a request sequence
        case 5 -> abortSequence(tc);
        // TC[21,6] report execution status of each request sequence -> TM[21,7]
        case 6 -> reportExecutionStatus(tc);
        // TC[21,8] load by reference and activate a request sequence
        case 8 -> loadByReference(tc, true);
        // TC[21,9] checksum a request sequence -> TM[21,10]
        case 9 -> checksumSequence(tc);
        // TC[21,11] report the content of a request sequence -> TM[21,12]
        case 11 -> reportSequenceContent(tc);
        // TC[21,13] abort all request sequences and report -> TM[21,14]
        case 13 -> abortAll(tc);
        default -> {
            log.warn("Unknown ST[21] subtype {}, sending NACK start", tc.getSubtype());
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
        }
    }

    // ---- seq_id helpers: 16-byte null-padded ASCII ----

    private static String readSeqId(ByteBuffer bb) {
        byte[] raw = new byte[SEQ_ID_LEN];
        bb.get(raw);
        int len = 0;
        while (len < raw.length && raw[len] != 0) {
            len++;
        }
        return new String(raw, 0, len, StandardCharsets.US_ASCII);
    }

    private static void writeSeqId(ByteBuffer bb, String id) {
        byte[] raw = new byte[SEQ_ID_LEN];
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, raw, 0, Math.min(idBytes.length, SEQ_ID_LEN));
        bb.put(raw);
    }

    // ---- TC[21,1]: direct-load a request sequence ----

    private List<Entry> parseEntries(ByteBuffer bb, int n) {
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int tcLen = (bb.getShort(bb.position() + 4) & 0xFFFF) + 7; // self-describing, no prefix
            byte[] rawTc = new byte[tcLen];
            bb.get(rawTc);
            int delayMs = bb.getInt();
            entries.add(new Entry(rawTc, delayMs));
        }
        return entries;
    }

    private void loadDirectly(PusTcPacket tc) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        String seqId = readSeqId(bb);
        int n = bb.get() & 0xFF;
        if (sequenceStore.containsKey(seqId)) {
            log.warn("ST21: seq '{}' already loaded, rejecting TC[21,1]", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_ALREADY_LOADED);
            return;
        }
        List<Entry> entries = parseEntries(bb, n);
        sequenceStore.put(seqId, new RequestSequence(seqId, entries));
        log.info("ST21: loaded seq '{}' with {} entries", seqId, entries.size());
        ack_completion(tc);
    }

    // ---- TC[21,2]/TC[21,8]: load (and optionally activate) a request sequence by reference ----

    private void loadByReference(PusTcPacket tc, boolean activate) {
        ack_start(tc);
        ByteBuffer bb = tc.getUserDataBuffer();
        String seqId = readSeqId(bb);
        RequestSequence seq = loadFromRepo(tc, bb, seqId);
        if (seq == null) {
            return; // NACK already sent by loadFromRepo
        }
        if (activate) {
            seq.status = Status.UNDER_EXECUTION;
            seq.nextIndex = 0;
            releaseNext(seq);
            log.info("ST21: loaded and activated seq '{}' from repo", seqId);
        }
        ack_completion(tc);
    }

    private RequestSequence loadFromRepo(PusTcPacket tc, ByteBuffer bb, String seqId) {
        // §6.21.5.3.d.1 / §6.21.5.6.d.1: reject if seq_id already loaded -- same condition as TC[21,1].
        if (sequenceStore.containsKey(seqId)) {
            log.warn("ST21: seq '{}' already loaded, rejecting", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_ALREADY_LOADED);
            return null;
        }
        String repoPath = DEFAULT_REPO_PATH;
        String fileName = seqId + ".bin";
        // getUserDataBuffer() runs to the end of the packet, so a bare TC_21_2_NO_PATH (seq_id
        // only) still has the trailing 2-byte CRC "remaining" here -- only take the WITH_PATH
        // branch when more than just that CRC trailer is left.
        if (bb.remaining() > 2) {
            int repoLen = bb.get() & 0xFF;
            byte[] repoBytes = new byte[repoLen];
            bb.get(repoBytes);
            repoPath = new String(repoBytes, StandardCharsets.US_ASCII);
            int fileLen = bb.get() & 0xFF;
            byte[] fileBytes = new byte[fileLen];
            bb.get(fileBytes);
            fileName = new String(fileBytes, StandardCharsets.US_ASCII);
        }
        String key = repoPath + "/" + fileName;
        // §6.21.5.3.d.3: "refers to a file that does not exist". §6.21.5.3.d.4 also requires
        // rejecting a file that exists but is "not recognized as a request sequence file"
        // (COMPL_ERR_FILE_NOT_RECOGNIZED) -- not reachable here: fileRepo only ever holds
        // well-formed entries, since there is no ST[23] TC that could write malformed bytes into it.
        if (!fileRepo.containsKey(key)) {
            log.warn("ST21: file '{}' not found in on-board repository", key);
            nack_completion(tc, COMPL_ERR_FILE_NOT_FOUND);
            return null;
        }
        List<Entry> entries = fileRepo.get(key);
        RequestSequence seq = new RequestSequence(seqId, entries);
        sequenceStore.put(seqId, seq);
        log.info("ST21: loaded seq '{}' from repo file '{}'", seqId, key);
        return seq;
    }

    // ---- TC[21,3]: unload a request sequence ----

    private void unloadSequence(PusTcPacket tc) {
        ack_start(tc);
        String seqId = readSeqId(tc.getUserDataBuffer());
        RequestSequence seq = sequenceStore.get(seqId);
        if (seq == null || seq.status == Status.UNDER_EXECUTION) {
            log.warn("ST21: cannot unload '{}' (not loaded or under execution)", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_NOT_UNLOADABLE);
            return;
        }
        sequenceStore.remove(seqId);
        log.info("ST21: unloaded seq '{}'", seqId);
        ack_completion(tc);
    }

    // ---- TC[21,4]/TC[21,5]: activate / abort a request sequence ----

    private void activateSequence(PusTcPacket tc) {
        ack_start(tc);
        String seqId = readSeqId(tc.getUserDataBuffer());
        RequestSequence seq = sequenceStore.get(seqId);
        if (seq == null || seq.status == Status.UNDER_EXECUTION) {
            log.warn("ST21: cannot activate '{}'", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_NOT_ACTIVATABLE);
            return;
        }
        seq.status = Status.UNDER_EXECUTION;
        seq.nextIndex = 0;
        releaseNext(seq);
        log.info("ST21: activated seq '{}'", seqId);
        ack_completion(tc);
    }

    private void releaseNext(RequestSequence seq) {
        if (seq.status != Status.UNDER_EXECUTION) {
            return; // aborted since this callback was scheduled
        }
        if (seq.nextIndex >= seq.entries.size()) {
            seq.status = Status.INACTIVE;
            seq.pending = null;
            log.info("ST21: sequence '{}' completed", seq.id);
            return;
        }
        Entry e = seq.entries.get(seq.nextIndex++);
        try {
            pusSimulator.processTc(new PusTcPacket(e.rawTc));
        } catch (Exception ex) {
            log.warn("ST21: failed to release entry {} of seq '{}'", seq.nextIndex - 1, seq.id, ex);
        }
        seq.pending = pusSimulator.executor.schedule(() -> releaseNext(seq), e.delayMs, TimeUnit.MILLISECONDS);
    }

    private void abortSequence(PusTcPacket tc) {
        ack_start(tc);
        String seqId = readSeqId(tc.getUserDataBuffer());
        RequestSequence seq = sequenceStore.get(seqId);
        if (seq == null || seq.status == Status.INACTIVE) {
            log.warn("ST21: cannot abort '{}' (not loaded or already inactive)", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_NOT_ABORTABLE);
            return;
        }
        abort(seq);
        log.info("ST21: aborted seq '{}'", seqId);
        ack_completion(tc);
    }

    private void abort(RequestSequence seq) {
        seq.status = Status.INACTIVE;
        if (seq.pending != null) {
            seq.pending.cancel(false);
            seq.pending = null;
        }
    }

    // ---- TC[21,6]/TM[21,7]: report execution status of each request sequence ----

    private void reportExecutionStatus(PusTcPacket tc) {
        ack_start(tc);
        sendTm21_7();
        ack_completion(tc);
    }

    private void sendTm21_7() {
        List<RequestSequence> all = new ArrayList<>(sequenceStore.values());
        int n = all.size();
        PusTmPacket pkt = newPacket(7, 1 + n * (SEQ_ID_LEN + 1));
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (RequestSequence seq : all) {
            writeSeqId(bb, seq.id);
            bb.put((byte) (seq.status == Status.UNDER_EXECUTION ? 1 : 0));
        }
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST21: TM[21,7] sent, {} entries", n);
    }

    // ---- TC[21,9]/TM[21,10]: checksum a request sequence ----

    private int computeChecksum(RequestSequence seq) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (Entry e : seq.entries) {
            bos.writeBytes(e.rawTc);
            bos.write((e.delayMs >>> 24) & 0xFF);
            bos.write((e.delayMs >>> 16) & 0xFF);
            bos.write((e.delayMs >>> 8) & 0xFF);
            bos.write(e.delayMs & 0xFF);
        }
        byte[] raw = bos.toByteArray();
        return CRC.compute(raw, 0, raw.length) & 0xFFFF;
    }

    private void checksumSequence(PusTcPacket tc) {
        ack_start(tc);
        String seqId = readSeqId(tc.getUserDataBuffer());
        RequestSequence seq = sequenceStore.get(seqId);
        if (seq == null) {
            log.warn("ST21: seq '{}' not loaded, cannot checksum", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_NOT_FOUND);
            return;
        }
        sendTm21_10(seqId, computeChecksum(seq));
        ack_completion(tc);
    }

    private void sendTm21_10(String seqId, int checksum) {
        PusTmPacket pkt = newPacket(10, SEQ_ID_LEN + 2);
        ByteBuffer bb = pkt.getUserDataBuffer();
        writeSeqId(bb, seqId);
        bb.putShort((short) checksum);
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST21: TM[21,10] sent, seq '{}' checksum=0x{}", seqId, Integer.toHexString(checksum));
    }

    // ---- TC[21,11]/TM[21,12]: report the content of a request sequence ----

    private void writeEntries(ByteBuffer bb, List<Entry> entries) {
        for (Entry e : entries) {
            bb.put(e.rawTc); // no length prefix -- the packet is self-describing
            bb.putInt(e.delayMs);
        }
    }

    private void reportSequenceContent(PusTcPacket tc) {
        ack_start(tc);
        String seqId = readSeqId(tc.getUserDataBuffer());
        RequestSequence seq = sequenceStore.get(seqId);
        if (seq == null) {
            log.warn("ST21: seq '{}' not loaded, cannot report content", seqId);
            nack_completion(tc, COMPL_ERR_SEQ_NOT_FOUND);
            return;
        }
        int userDataLength = SEQ_ID_LEN + 1;
        for (Entry e : seq.entries) {
            userDataLength += e.rawTc.length + 4;
        }
        PusTmPacket pkt = newPacket(12, userDataLength);
        ByteBuffer bb = pkt.getUserDataBuffer();
        writeSeqId(bb, seq.id);
        bb.put((byte) seq.entries.size());
        writeEntries(bb, seq.entries);
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST21: TM[21,12] sent, seq '{}', {} entries", seqId, seq.entries.size());
        ack_completion(tc);
    }

    // ---- TC[21,13]/TM[21,14]: abort all request sequences and report ----

    private void abortAll(PusTcPacket tc) {
        ack_start(tc);
        List<String> aborted = new ArrayList<>();
        for (RequestSequence seq : sequenceStore.values()) {
            if (seq.status == Status.UNDER_EXECUTION) {
                abort(seq);
                aborted.add(seq.id);
            }
        }
        sendTm21_14(aborted);
        log.info("ST21: TC[21,13] aborted {} sequences", aborted.size());
        ack_completion(tc);
    }

    private void sendTm21_14(List<String> abortedIds) {
        int n = abortedIds.size();
        PusTmPacket pkt = newPacket(14, 1 + n * SEQ_ID_LEN);
        ByteBuffer bb = pkt.getUserDataBuffer();
        bb.put((byte) n);
        for (String id : abortedIds) {
            writeSeqId(bb, id);
        }
        pusSimulator.transmitRealtimeTM(pkt);
        log.info("ST21: TM[21,14] sent, {} entries", n);
    }
}
