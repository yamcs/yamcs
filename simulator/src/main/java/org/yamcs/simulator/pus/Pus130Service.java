package org.yamcs.simulator.pus;

import static org.yamcs.simulator.pus.PusPackedFields.APID_BITS;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.yamcs.utils.BitBuffer;

/** Telemetry mode, event overlay, and pass management on the simulator executor. */
final class Pus130Service extends AbstractPusService {
    private enum Mode {
        NTM, BEACON, CTM
    }

    private enum Trigger {
        TELECOMMAND, LOSS_OF_SIGNAL, DURATION_ELAPSED, AUTONOMOUS, BOOT
    }

    private static final int MAX_COUNT = 0xFF;
    private static final int SID_BITS = 8 + APID_BITS + 8;
    private static final int STM = 0;
    private static final int BEACON = 1;
    private static final int CTM = 2;
    private static final int OVERLAY_MASK = 1 << 3;
    private static final int STORE_MASK = (1 << STM) | OVERLAY_MASK;
    private static final int PACKET_STORE_SLOT = 0;
    private static final int PAYLOAD_DATA_SLOT = 1;
    private static final int DEMO_PAYLOAD_SLOT_ID = 1;
    private static final int START_ERR_INVALID_REQUEST = 8;
    private static final int COMPL_ERR_DUMP_INTERRUPTED = 6;
    private static final long STORE_POLL_MS = 250;

    private final List<Set<Structure>> tables = List.of(
            new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    private final boolean[] tableConfigured = new boolean[tables.size()];
    private final Map<Integer, Overlay> overlays = new LinkedHashMap<>();

    private Mode mode = Mode.NTM;
    private Trigger lastTrigger = Trigger.BOOT;
    private long lastTransition;
    private long modeDeadline;
    private ScheduledFuture<?> modeTimer;
    private ContactPass pass;

    Pus130Service(PusSimulator simulator) {
        super(simulator, 130);
        lastTransition = now();
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        ByteBuffer data = tc.getUserDataBuffer();
        int dataBits = data.remaining() * 8;
        BitBuffer fields = PusPackedFields.readBuffer(data);
        try {
            switch (tc.getSubtype()) {
            case 1 -> {
                int duration = read(fields, 16);
                requireEnd(fields, dataBits);
                ack_start(tc);
                cancelPass();
                enterMode(Mode.BEACON, Trigger.TELECOMMAND, now() + duration * 1000L);
                ack_completion(tc);
            }
            case 2 -> {
                requireEnd(fields, dataBits);
                ack_start(tc);
                cancelPass();
                enterMode(Mode.NTM, Trigger.TELECOMMAND, 0);
                ack_completion(tc);
            }
            case 3 -> acknowledgeReport(tc, dataBits, this::reportMode);
            case 5, 6 -> editTable(tc, fields, dataBits, tc.getSubtype() == 5);
            case 7 -> reportTables(tc, fields, dataBits);
            case 9, 10 -> editOverlay(tc, fields, dataBits, tc.getSubtype() == 9);
            case 11, 12 -> setOverlaysEnabled(tc, fields, dataBits, tc.getSubtype() == 11);
            case 13 -> reportOverlayConfig(tc, fields, dataBits);
            case 15 -> acknowledgeReport(tc, dataBits, this::reportOverlayStatus);
            case 17 -> acknowledgeReport(tc, dataBits, this::reportClaims);
            case 19 -> startManualPass(tc, fields, dataBits);
            case 20 -> startAutomaticPass(tc, fields, dataBits);
            case 21 -> acknowledgeReport(tc, dataBits, this::reportPass);
            case 23 -> {
                requireEnd(fields, dataBits);
                require(pass != null && pass.active(), "no dump sequence is active");
                ack_start(tc);
                stopDump(pass, false);
                ack_completion(tc);
            }
            default -> nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            log.debug("Rejected ST[130] subtype {}: {}", tc.getSubtype(), e.getMessage());
            nack_start(tc, START_ERR_INVALID_REQUEST);
        }
    }

    private void acknowledgeReport(PusTcPacket tc, int dataBits, Runnable report) {
        require(dataBits == 0, "unexpected application data");
        ack_start(tc);
        report.run();
        ack_completion(tc);
    }

    private void enterMode(Mode next, Trigger trigger, long deadline) {
        if (modeTimer != null) {
            modeTimer.cancel(false);
            modeTimer = null;
        }
        if (mode != next) {
            lastTransition = now();
            lastTrigger = trigger;
            mode = next;
            refreshGeneration();
        }
        modeDeadline = deadline;
        if (next != Mode.NTM) {
            modeTimer = pusSimulator.executor.schedule(() -> {
                cancelPass();
                enterMode(Mode.NTM, next == Mode.CTM ? Trigger.LOSS_OF_SIGNAL : Trigger.DURATION_ELAPSED, 0);
            }, Math.max(0, deadline - now()), TimeUnit.MILLISECONDS);
        }
    }

    private void reportMode() {
        PusTmPacket report = reportPacket(4, 4 + pusSimulator.timeEncoding.getEncodedLength(), 1, 1);
        ByteBuffer body = report.getUserDataBuffer();
        body.put((byte) mode.ordinal());
        encodeTime(lastTransition, body);
        body.put((byte) lastTrigger.ordinal());
        body.putShort((short) secondsRemaining(modeDeadline));
        pusSimulator.transmitRealtimeTM(report);
    }

    private void editTable(PusTcPacket tc, BitBuffer fields, int dataBits, boolean add) {
        int tableId = read(fields, 8);
        require(tableId < tables.size(), "unknown table ID");
        Set<Structure> table = tables.get(tableId);
        Set<Structure> requested = readStructures(fields, dataBits);
        Set<Structure> updated = applyEdit(table, requested, add);

        ack_start(tc);
        table.clear();
        table.addAll(updated);
        tableConfigured[tableId] = true;
        refreshGeneration();
        ack_completion(tc);
    }

    private void reportTables(PusTcPacket tc, BitBuffer fields, int dataBits) {
        List<Integer> ids = readIds(fields, dataBits);
        if (ids.isEmpty()) {
            ids = List.of(STM, BEACON, CTM);
        }
        List<ReportRow> rows = new ArrayList<>();
        boolean invalid = false;
        for (int id : ids) {
            if (id >= tables.size()) {
                nack_start(tc, START_ERR_INVALID_REQUEST);
                invalid = true;
                continue;
            }
            Set<Structure> structures = tables.get(id);
            rows.add(new ReportRow(16 + structures.size() * SID_BITS, bits -> {
                bits.putBits(id, 8);
                writeStructures(bits, structures);
            }));
        }
        reportSelection(tc, 8, rows, invalid);
    }

    private void editOverlay(PusTcPacket tc, BitBuffer fields, int dataBits, boolean add) {
        int eventId = read(fields, 8);
        int timeout = add ? read(fields, 16) : 0;
        Set<Structure> requested = readStructures(fields, dataBits);
        Overlay overlay = overlays.get(eventId);
        if (add) {
            require(pusSimulator.pus5Service.hasEvent(eventId), "unknown event ID");
            require(overlay != null || overlays.size() < MAX_COUNT, "maximum event overlays reached");
        } else {
            require(overlay != null, "unknown event overlay");
        }
        Set<Structure> updated = applyEdit(overlay == null ? Set.of() : overlay.structures, requested, add);

        ack_start(tc);
        if (overlay == null) {
            overlay = new Overlay();
            overlays.put(eventId, overlay);
        }
        if (add) {
            overlay.timeout = timeout;
        }
        overlay.structures = updated;
        refreshGeneration();
        ack_completion(tc);
    }

    private void setOverlaysEnabled(PusTcPacket tc, BitBuffer fields, int dataBits, boolean enable) {
        List<Integer> ids = readIds(fields, dataBits);
        require(!ids.isEmpty() && ids.stream().allMatch(overlays::containsKey), "invalid event overlay selection");
        ack_start(tc);
        for (int id : ids) {
            Overlay overlay = overlays.get(id);
            overlay.enabled = enable;
            if (!enable) {
                deactivate(overlay);
            }
        }
        refreshGeneration();
        ack_completion(tc);
    }

    void onEvent(int eventId) {
        Overlay overlay = overlays.get(eventId);
        if (overlay == null || !overlay.enabled) {
            return;
        }
        deactivate(overlay);
        overlay.deadline = now() + overlay.timeout * 1000L;
        if (overlay.timeout != 0) {
            overlay.timer = pusSimulator.executor.schedule(() -> {
                deactivate(overlay);
                refreshGeneration();
            }, overlay.timeout, TimeUnit.SECONDS);
        }
        refreshGeneration();
    }

    private static void deactivate(Overlay overlay) {
        if (overlay.timer != null) {
            overlay.timer.cancel(false);
            overlay.timer = null;
        }
        overlay.deadline = 0;
    }

    private void reportOverlayConfig(PusTcPacket tc, BitBuffer fields, int dataBits) {
        List<Integer> ids = readIds(fields, dataBits);
        if (ids.isEmpty()) {
            ids = new ArrayList<>(overlays.keySet());
        }
        List<ReportRow> rows = new ArrayList<>();
        boolean invalid = false;
        for (int id : ids) {
            Overlay overlay = overlays.get(id);
            if (overlay == null) {
                nack_start(tc, START_ERR_INVALID_REQUEST);
                invalid = true;
                continue;
            }
            rows.add(new ReportRow(33 + overlay.structures.size() * SID_BITS, bits -> {
                bits.putBits(id, 8);
                bits.putBits(overlay.enabled ? 1 : 0, 1);
                bits.putBits(overlay.timeout, 16);
                writeStructures(bits, overlay.structures);
            }));
        }
        reportSelection(tc, 14, rows, invalid);
    }

    private void reportOverlayStatus() {
        List<ReportRow> rows = new ArrayList<>();
        overlays.forEach((id, overlay) -> {
            int remaining = secondsRemaining(overlay.deadline);
            rows.add(new ReportRow(26, bits -> {
                bits.putBits(id, 8);
                bits.putBits(overlay.enabled ? 1 : 0, 1);
                bits.putBits(remaining > 0 ? 1 : 0, 1);
                bits.putBits(remaining, 16);
            }));
        });
        sendRows(16, rows);
    }

    private void reportClaims() {
        Map<Structure, Integer> claims = new LinkedHashMap<>();
        tables.get(STM).forEach(sid -> claims.put(sid, 1 << STM));
        int modeTable = mode.ordinal();
        if (modeTable != STM) {
            tables.get(modeTable).forEach(sid -> addClaim(claims, sid, 1 << modeTable));
        }
        long timestamp = now();
        for (Overlay overlay : overlays.values()) {
            if (overlay.deadline > timestamp) {
                overlay.structures.forEach(sid -> addClaim(claims, sid, OVERLAY_MASK));
            }
        }
        List<ReportRow> rows = new ArrayList<>();
        claims.forEach((sid, mask) -> rows.add(new ReportRow(SID_BITS + 11, bits -> {
            sid.write(bits);
            bits.putBits(mask, 8);
            bits.putBits(1, 1);
            bits.putBits((mask & STORE_MASK) != 0 ? 1 : 0, 1);
            bits.putBits(transmissionClaim(mask) ? 1 : 0, 1);
        })));
        sendRows(18, rows);
    }

    private static void addClaim(Map<Structure, Integer> claims, Structure sid, int mask) {
        claims.merge(sid, mask, (first, second) -> first | second);
    }

    private int sourceMask(Structure sid) {
        int mask = tables.get(STM).contains(sid) ? 1 << STM : 0;
        int modeTable = mode.ordinal();
        if (tables.get(modeTable).contains(sid)) {
            mask |= 1 << modeTable;
        }
        long timestamp = now();
        for (Overlay overlay : overlays.values()) {
            if (overlay.deadline > timestamp && overlay.structures.contains(sid)) {
                return mask | OVERLAY_MASK;
            }
        }
        return mask;
    }

    private boolean transmissionClaim(int mask) {
        return switch (mode) {
        case NTM -> false;
        case BEACON -> (mask & (1 << BEACON)) != 0;
        case CTM -> (mask & ((1 << CTM) | OVERLAY_MASK)) != 0;
        };
    }

    private boolean generationManaged() {
        return tableConfigured[STM] || tableConfigured[mode.ordinal()];
    }

    boolean shouldGenerate(int apid, int sid, boolean diagnostic, boolean periodicEnabled) {
        Structure structure = new Structure(diagnostic ? 1 : 0, apid, sid);
        return sourceMask(structure) != 0 || (!generationManaged() && periodicEnabled);
    }

    boolean allowStore(PusTmPacket packet) {
        Structure sid = Structure.fromPacket(packet);
        if (sid == null) {
            return true;
        }
        int mask = sourceMask(sid);
        return (mask == 0 && !tableConfigured[STM]) || (mask & STORE_MASK) != 0;
    }

    boolean allowForward(PusTmPacket packet) {
        Structure sid = Structure.fromPacket(packet);
        if (sid == null) {
            return true;
        }
        int mask = sourceMask(sid);
        return (mask == 0 && !generationManaged()) || transmissionClaim(mask);
    }

    private void refreshGeneration() {
        pusSimulator.pus3Service.refreshManagedGeneration();
    }

    private void startManualPass(PusTcPacket tc, BitBuffer fields, int dataBits) {
        int duration = read(fields, 16);
        requireEnd(fields, dataBits);
        require(duration != 0, "zero pass duration");
        ack_start(tc);
        cancelPass();
        long deadline = now() + duration * 1000L;
        pass = new ContactPass(deadline, 0, 0, List.of(), null);
        enterMode(Mode.CTM, Trigger.TELECOMMAND, deadline);
        ack_completion(tc);
    }

    private void startAutomaticPass(PusTcPacket tc, BitBuffer fields, int dataBits) {
        int duration = read(fields, 16);
        int x = read(fields, 16);
        int y = read(fields, 16);
        int count = read(fields, 8);
        require(duration > x + y && count != 0, "empty dump window or plan");
        require(pass == null || !pass.active(), "a dump sequence is already active");
        List<DumpSlot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = read(fields, 8);
            int id = read(fields, 8);
            int allotted = read(fields, 16);
            require((type == PAYLOAD_DATA_SLOT && id == DEMO_PAYLOAD_SLOT_ID)
                    || (type == PACKET_STORE_SLOT && pusSimulator.pus15Service.hasStore(id)),
                    "unknown dump slot");
            slots.add(new DumpSlot(type, id, allotted));
        }
        requireEnd(fields, dataBits);
        ack_start(tc);
        long start = now();
        long deadline = start + duration * 1000L;
        ContactPass newPass = new ContactPass(deadline, x, y, slots, tc);
        pass = newPass;
        enterMode(Mode.CTM, Trigger.TELECOMMAND, deadline);
        scheduleDump(newPass, start + x * 1000L);
    }

    private void scheduleDump(ContactPass current, long timestamp) {
        current.timer = pusSimulator.executor.schedule(() -> advanceDump(current),
                Math.max(0, timestamp - now()), TimeUnit.MILLISECONDS);
    }

    private void advanceDump(ContactPass current) {
        if (current != pass || !current.active()) {
            return;
        }
        long timestamp = now();
        if (timestamp >= current.dumpEnd) {
            if (current.currentSlot != null && current.slotEnd <= timestamp) {
                finishSlot(current);
            }
            finishDump(current);
            return;
        }
        DumpSlot slot = current.currentSlot;
        if (slot != null) {
            if (timestamp < current.slotEnd
                    && (slot.type == PAYLOAD_DATA_SLOT
                            || pusSimulator.pus15Service.managedRetrievalPending(slot.id, slot))) {
                scheduleDump(current, slot.type == PACKET_STORE_SLOT
                        ? Math.min(current.slotEnd, timestamp + STORE_POLL_MS)
                        : current.slotEnd);
                return;
            }
            finishSlot(current);
        }
        if (current.nextIndex >= current.slots.size()) {
            finishDump(current);
            return;
        }
        slot = current.slots.get(current.nextIndex++);
        if (slot.type == PACKET_STORE_SLOT && !pusSimulator.pus15Service.beginManagedRetrieval(slot.id, slot)) {
            stopDump(current, true);
            return;
        }
        current.currentSlot = slot;
        current.slotEnd = Math.min(timestamp + slot.allotted * 1000L, current.dumpEnd);
        scheduleDump(current, slot.type == PACKET_STORE_SLOT
                ? Math.min(current.slotEnd, timestamp + STORE_POLL_MS)
                : current.slotEnd);
    }

    private void finishSlot(ContactPass current) {
        DumpSlot slot = current.currentSlot;
        releaseSlot(current);
        slot.done = true;
        ack_progress(current.request);
    }

    private void releaseSlot(ContactPass current) {
        DumpSlot slot = current.currentSlot;
        if (slot != null && slot.type == PACKET_STORE_SLOT) {
            pusSimulator.pus15Service.endManagedRetrieval(slot.id, slot);
        }
        current.currentSlot = null;
    }

    private void finishDump(ContactPass current) {
        releaseSlot(current);
        ack_completion(current.request);
        current.request = null;
    }

    private void stopDump(ContactPass current, boolean reportProgressFailure) {
        if (!current.active()) {
            return;
        }
        current.timer.cancel(false);
        releaseSlot(current);
        if (reportProgressFailure) {
            nack_progress(current.request, COMPL_ERR_DUMP_INTERRUPTED);
        }
        nack_completion(current.request, COMPL_ERR_DUMP_INTERRUPTED);
        current.request = null;
    }

    private void cancelPass() {
        if (pass != null) {
            stopDump(pass, true);
            pass = null;
        }
    }

    private void reportPass() {
        ContactPass current = pass;
        int count = current == null ? 0 : current.slots.size();
        int timeBytes = pusSimulator.timeEncoding.getEncodedLength();
        int size = PusPackedFields.bytesForBits(81 + timeBytes * 8 + count * 33);
        PusTmPacket report = reportPacket(22, size, 1, 1);
        ByteBuffer body = report.getUserDataBuffer();
        body.put((byte) mode.ordinal());
        encodeTime(current == null ? 0 : current.los, body);
        BitBuffer fields = PusPackedFields.bitBuffer(body);
        DumpSlot active = current == null ? null : current.currentSlot;
        fields.putBits(current != null && current.active() ? 1 : 0, 1);
        fields.putBits(active == null ? 0 : active.type, 8);
        fields.putBits(active == null ? 0 : active.id, 8);
        fields.putBits(active == null ? 0 : secondsRemaining(current.slotEnd), 16);
        fields.putBits(current == null ? 0 : current.x, 16);
        fields.putBits(current == null ? 0 : current.y, 16);
        fields.putBits(count, 8);
        if (current != null) {
            for (DumpSlot slot : current.slots) {
                fields.putBits(slot.type, 8);
                fields.putBits(slot.id, 8);
                fields.putBits(slot.allotted, 16);
                fields.putBits(slot.done ? 1 : 0, 1);
            }
        }
        pusSimulator.transmitRealtimeTM(report);
    }

    private void reportSelection(PusTcPacket tc, int subtype, List<ReportRow> rows, boolean invalid) {
        if (!invalid) {
            ack_start(tc);
        }
        if (!invalid || !rows.isEmpty()) {
            sendRows(subtype, rows);
        }
        if (!invalid) {
            ack_completion(tc);
        }
    }

    private void sendRows(int subtype, List<ReportRow> rows) {
        int maxBits = pusSimulator.maxTmDataSize() * 8;
        List<List<ReportRow>> pages = new ArrayList<>();
        List<ReportRow> page = new ArrayList<>();
        pages.add(page);
        int pageBits = 8;
        for (ReportRow row : rows) {
            if (page.size() == MAX_COUNT || (!page.isEmpty() && pageBits + row.bits > maxBits)) {
                page = new ArrayList<>();
                pages.add(page);
                pageBits = 8;
            }
            page.add(row);
            pageBits += row.bits;
        }
        for (int i = 0; i < pages.size(); i++) {
            List<ReportRow> entries = pages.get(i);
            int size = PusPackedFields.bytesForBits(8 + entries.stream().mapToInt(ReportRow::bits).sum());
            PusTmPacket report = reportPacket(subtype, size, i + 1, pages.size());
            BitBuffer fields = PusPackedFields.bitBuffer(report.getUserDataBuffer());
            fields.putBits(entries.size(), 8);
            entries.forEach(row -> row.write.accept(fields));
            pusSimulator.transmitRealtimeTM(report);
        }
    }

    private PusTmPacket reportPacket(int subtype, int size, int page, int totalPages) {
        PusTmPacket packet = newPacket(subtype, size);
        packet.setPage(page, totalPages);
        return packet;
    }

    private Set<Structure> readStructures(BitBuffer fields, int dataBits) {
        int count = read(fields, 8);
        require(count != 0, "empty structure selection");
        Set<Structure> structures = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            int type = read(fields, 8);
            int apid = read(fields, APID_BITS);
            int sid = read(fields, 8);
            require(type == 0 || type == 1, "unknown structure type");
            structures.add(new Structure(type, apid, sid));
        }
        requireEnd(fields, dataBits);
        return structures;
    }

    private Set<Structure> applyEdit(Set<Structure> existing, Set<Structure> requested, boolean add) {
        Set<Structure> updated = new LinkedHashSet<>(existing);
        if (add) {
            require(requested.stream().allMatch(sid -> pusSimulator.pus3Service.hasStructure(sid.apid, sid.id, sid.type == 1)), "undefined structure");
            updated.addAll(requested);
            require(updated.size() <= MAX_COUNT, "structure capacity exceeded");
        } else {
            require(updated.containsAll(requested), "unconfigured structure");
            updated.removeAll(requested);
        }
        return updated;
    }

    private static void writeStructures(BitBuffer bits, Set<Structure> structures) {
        bits.putBits(structures.size(), 8);
        structures.forEach(sid -> sid.write(bits));
    }

    private static List<Integer> readIds(BitBuffer fields, int dataBits) {
        int count = read(fields, 8);
        List<Integer> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(read(fields, 8));
        }
        requireEnd(fields, dataBits);
        return result;
    }

    private static int read(BitBuffer fields, int size) {
        return PusPackedFields.readUnsigned(fields, size);
    }

    private static void requireEnd(BitBuffer fields, int size) {
        int remaining = size - fields.getPosition();
        require(remaining >= 0 && remaining < 8 && (remaining == 0 || read(fields, remaining) == 0),
                "unexpected application data");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new IllegalArgumentException(description);
        }
    }

    private long now() {
        return pusSimulator.timeEncoding.now().millis();
    }

    private int secondsRemaining(long deadline) {
        return (int) Math.min(0xFFFF, Math.max(0, deadline - now() + 999) / 1000);
    }

    private void encodeTime(long timestamp, ByteBuffer body) {
        pusSimulator.timeEncoding.encode(new PusTime(timestamp), body);
    }

    private record Structure(int type, int apid, int id) {
        void write(BitBuffer fields) {
            fields.putBits(type, 8);
            fields.putBits(apid, APID_BITS);
            fields.putBits(id, 8);
        }

        static Structure fromPacket(PusTmPacket packet) {
            int subtype = packet.getSubtype();
            if (packet.getType() != 3 || (subtype != 25 && subtype != 26)) {
                return null;
            }
            return new Structure(subtype == 25 ? 0 : 1, packet.getAPID(), Pus3Service.reportStructureId(packet));
        }
    }

    private record ReportRow(int bits, Consumer<BitBuffer> write) {
    }

    private static final class Overlay {
        Set<Structure> structures;
        int timeout;
        boolean enabled;
        long deadline;
        ScheduledFuture<?> timer;
    }

    private static final class DumpSlot {
        final int type;
        final int id;
        final int allotted;
        boolean done;

        DumpSlot(int type, int id, int allotted) {
            this.type = type;
            this.id = id;
            this.allotted = allotted;
        }
    }

    private static final class ContactPass {
        final long los;
        final long dumpEnd;
        final int x;
        final int y;
        final List<DumpSlot> slots;
        int nextIndex;
        long slotEnd;
        DumpSlot currentSlot;
        PusTcPacket request;
        ScheduledFuture<?> timer;

        ContactPass(long los, int x, int y, List<DumpSlot> slots, PusTcPacket request) {
            this.los = los;
            this.dumpEnd = los - y * 1000L;
            this.x = x;
            this.y = y;
            this.slots = slots;
            this.request = request;
        }

        boolean active() {
            return request != null;
        }
    }
}
