package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Pus5Service extends AbstractPusService {

    private static final String MDB_RESOURCE_PATH = "/jtyu_mdb.xml";
    private static final List<Integer> DEFAULT_EVENT_SUBTYPES = List.of(1, 2, 3, 4);

    private Map<Integer, String> eventDefinitions = new LinkedHashMap<>();
    private final Map<Integer, Boolean> eventEnabled = new LinkedHashMap<>();
    private List<Integer> eventReportSubtypes = DEFAULT_EVENT_SUBTYPES;
    private int count = 0;

    Pus5Service(PusSimulator pusSimulator) {
        super(pusSimulator, 5);
    }

    @Override
    public void start() {
        eventDefinitions = MdbLoader.loadEventDefinitions(MDB_RESOURCE_PATH);
        eventReportSubtypes = MdbLoader.loadEventReportSubtypes(MDB_RESOURCE_PATH);

        if (eventDefinitions.isEmpty()) {
            log.warn("No events loaded from MDB, falling back to defaults");
            eventDefinitions.put(1, "IsAttitudeNominal");
            eventDefinitions.put(2, "MagStatus");
        }
        if (eventReportSubtypes.isEmpty()) {
            log.warn("No Service 5 event report subtypes found in MDB, falling back to {}", DEFAULT_EVENT_SUBTYPES);
            eventReportSubtypes = DEFAULT_EVENT_SUBTYPES;
        }

        eventEnabled.clear();
        for (int eventId : eventDefinitions.keySet()) {
            eventEnabled.put(eventId, true);
            log.info("Registered event id={} label={}", eventId, eventDefinitions.get(eventId));
        }

        pusSimulator.executor.scheduleAtFixedRate(
            () -> sendEvent(), 0, 1000, TimeUnit.MILLISECONDS
        );

        log.info("Pus5Service started with {} event(s) loaded from MDB", eventDefinitions.size());
    }

    public void sendEvent() {
        Integer eventId = nextEnabledEventId();
        if (eventId == null) {
            return;
        }
        String eventLabel = eventDefinitions.get(eventId);
        int subtype = getSubtypeForEmission(count);
        byte[] payload = buildEventPayload(eventId);
        PusTmPacket packet = newPacket(subtype, payload.length);
        ByteBuffer bb = packet.getUserDataBuffer();
        bb.put(payload);

        pusSimulator.transmitRealtimeTM(packet);

        log.debug("Sent TM[05,{}] for event id={} label={}", subtype, eventId, eventLabel);
        count++;
    }

    private Integer nextEnabledEventId() {
        if (eventDefinitions.isEmpty()) {
            return null;
        }
        List<Integer> eventIds = new ArrayList<>(eventDefinitions.keySet());
        for (int i = 0; i < eventIds.size(); i++) {
            int eventId = eventIds.get((count + i) % eventIds.size());
            if (eventEnabled.getOrDefault(eventId, false)) {
                return eventId;
            }
        }
        return null;
    }

    private int getSubtypeForEmission(int emissionCount) {
        return eventReportSubtypes.get(emissionCount % eventReportSubtypes.size());
    }

    @Override
    public void executeTc(PusTcPacket tc) {
        int subtype = tc.getSubtype();

        if (subtype == 5 || subtype == 6) {
            boolean enable = (subtype == 5);
            ack_start(tc);
            enableDisableEvents(tc, enable);
        } else if (subtype == 7) {
            ack_start(tc);
            reportDisabledEvents(tc);
        } else {
            log.info("Pus5Service: invalid subtype {}, sending NACK", subtype);
            nack_start(tc, START_ERR_INVALID_PUS_SUBTYPE);
        }
    }

    private void enableDisableEvents(PusTcPacket tc, boolean enable) {
        ByteBuffer bb = tc.getUserDataBuffer();
        if (bb.remaining() < 1) {
            log.info("Pus5Service: missing event count in TC[05,{}] payload", enable ? 5 : 6);
            nack_completion(tc, COMPL_ERR_INVALID_PACKET_DATA);
            return;
        }

        int n = bb.get() & 0xFF;
        if (n == 0) {
            for (int eventId : eventDefinitions.keySet()) {
                eventEnabled.put(eventId, enable);
            }
            log.info("Pus5Service: {} all {} MDB-defined events",
                    enable ? "enabled" : "disabled", eventDefinitions.size());
            ack_completion(tc);
            return;
        }
        if (bb.remaining() < n) {
            log.info("Pus5Service: TC payload declares {} event(s) but only {} byte(s) remain", n, bb.remaining());
            nack_completion(tc, COMPL_ERR_INVALID_PACKET_DATA);
            return;
        }

        for (int i = 0; i < n; i++) {
            int eventId = bb.get() & 0xFF;

            if (!eventDefinitions.containsKey(eventId)) {
                log.info("Pus5Service: unknown event id={} (not in MDB), sending NACK", eventId);
                nack_completion(tc, COMPL_ERR_INVALID_EVENT_ID);
                return;
            }

            eventEnabled.put(eventId, enable);
            log.info("Pus5Service: event id={} label={} {}",
                eventId,
                eventDefinitions.get(eventId),
                enable ? "ENABLED" : "DISABLED"
            );
        }

        ack_completion(tc);
    }

    private void reportDisabledEvents(PusTcPacket tc) {
        int reported = 0;
        for (int eventId : eventDefinitions.keySet()) {
            if (eventEnabled.getOrDefault(eventId, false)) {
                continue;
            }
            int subtype = getSubtypeForEmission(reported);
            String eventLabel = eventDefinitions.get(eventId);
            byte[] payload = buildEventPayload(eventId);
            PusTmPacket packet = newPacket(subtype, payload.length);
            packet.getUserDataBuffer().put(payload);
            pusSimulator.transmitRealtimeTM(packet);
            reported++;
        }
        log.info("Pus5Service: reported {} disabled event(s)", reported);
        ack_completion(tc);
    }

    private byte[] buildEventPayload(int eventId) {
        // In jtyu_mdb.xml, Service 5 event packets carry only `event_definition_id`.
        return new byte[] { (byte) (eventId & 0xFF) };
    }
}
