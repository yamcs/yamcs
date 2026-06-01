package org.yamcs.pus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.memento.MementoDb;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Parameter;

import com.google.gson.JsonObject;

/**
 * Native YAMCS implementation of PUS ST[05] — Event Reporting Service.
 * <p>
 * Handles:
 * <ul>
 *   <li>TC[5,5] — Enable report generation for a list of event definitions</li>
 *   <li>TC[5,6] — Disable report generation for a list of event definitions</li>
 *   <li>TC[5,7] — Report the list of disabled event definitions (responds with TM[5,8])</li>
 * </ul>
 * <p>
 * Provides {@link #raiseEvent(int, int, byte[])} for other services to emit TM[5,1-4].
 * <p>
 * The event registry is derived from the MDB enumeration of {@code eventIdParameter}.
 * State (enabled/disabled per event) is persisted across restarts via {@link MementoDb}.
 * <p>
 * Config example (under {@code PusCommandReleaser} handlers):
 * <pre>
 *   - serviceType: 5
 *     class: org.yamcs.pus.Pus5Service
 *     args:
 *       eventIdParameter: /PUS5/event_id
 *       defaultEnabled: true   # optional, default is true
 * </pre>
 */
public class Pus5Service extends PusTcHandler {

    static final int SERVICE_TYPE = 5;
    static final String MEMENTO_KEY = "pus5.enabled";

    /** Event registry: event ID → enabled flag. */
    private final Map<Integer, Boolean> enabled = new HashMap<>();
    private MementoDb db;

    @Override
    protected void doInit(YConfiguration config) {
        String paramFqn = config.getString("eventIdParameter");
        boolean defaultEnabled = config.getBoolean("defaultEnabled", true);

        // Derive the event registry from the MDB enum type — single source of truth
        var mdb = MdbFactory.getInstance(releaser.getYamcsInstance());
        Parameter param = mdb.getParameter(paramFqn);
        if (param == null) {
            throw new ConfigurationException("eventIdParameter '" + paramFqn + "' not found in MDB");
        }
        if (!(param.getParameterType() instanceof EnumeratedParameterType ept)) {
            throw new ConfigurationException("Parameter '" + paramFqn + "' must be an EnumeratedParameterType");
        }
        for (var ve : ept.getValueEnumerationList()) {
            enabled.put((int) ve.getValue(), defaultEnabled);
        }

        // Overlay with any TC[5,5/6] changes that survived a restart
        db = MementoDb.getInstance(releaser.getYamcsInstance());
        db.getJsonObject(MEMENTO_KEY).ifPresent(obj -> {
            for (var entry : obj.entrySet()) {
                int id = Integer.parseInt(entry.getKey());
                if (enabled.containsKey(id)) {
                    enabled.put(id, entry.getValue().getAsBoolean());
                }
            }
        });

        log.debug("Pus5Service initialised: {} event definition(s) from {}", enabled.size(), paramFqn);
    }

    @Override
    public void handleTc(PreparedCommand pc) {
        byte[] bin = pc.getBinary();
        publishAckSent(pc);
        if (bin.length < APP_DATA_OFFSET) {
            publishCompletion(pc, false, "packet too short");
            return;
        }
        switch (PusPacket.getSubtype(bin)) {
            case 5 -> enableDisableEvents(pc, true);
            case 6 -> enableDisableEvents(pc, false);
            case 7 -> sendDisabledList(pc);
            default -> publishCompletion(pc, false, "unknown subtype " + PusPacket.getSubtype(bin));
        }
    }

    /**
     * Handles TC[5,5] (enable=true) and TC[5,6] (enable=false).
     * Per §6.5.5.2f / §6.5.5.3f: invalid event IDs are rejected individually;
     * valid instructions in the same TC still execute.
     */
    synchronized void enableDisableEvents(PreparedCommand pc, boolean enable) {
        byte[] bin = pc.getBinary();
        int offset = APP_DATA_OFFSET;
        if (bin.length < offset + 1) {
            publishCompletion(pc, false, "packet too short");
            return;
        }
        int n = bin[offset++] & 0xFF;
        if (bin.length < offset + n) {
            publishCompletion(pc, false, "packet too short for " + n + " event IDs");
            return;
        }
        boolean anyFailure = false;

        for (int i = 0; i < n; i++) {
            int eventId = bin[offset++] & 0xFF;
            if (!enabled.containsKey(eventId)) {
                log.info("TC[5,{}]: unknown event ID {}, sending NOK completion", enable ? 5 : 6, eventId);
                publishCompletion(pc, false, "INVALID_EVENT_ID: " + eventId);
                anyFailure = true;
            } else {
                enabled.put(eventId, enable);
            }
        }

        persistState();

        if (!anyFailure) {
            publishCompletion(pc, true, null);
        }
    }

    /** Handles TC[5,7]: emits TM[5,8] with the current list of disabled event IDs. */
    synchronized void sendDisabledList(PreparedCommand pc) {
        List<Integer> disabled = new ArrayList<>();
        for (var entry : enabled.entrySet()) {
            if (!entry.getValue()) {
                disabled.add(entry.getKey());
            }
        }
        disabled.sort(null);

        byte[] appData = new byte[1 + disabled.size()];
        appData[0] = (byte) disabled.size();
        for (int i = 0; i < disabled.size(); i++) {
            appData[1 + i] = (byte) disabled.get(i).intValue();
        }

        emitTm(SERVICE_TYPE, 8, appData);
        publishCompletion(pc, true, null);
    }

    /**
     * Raises a PUS event report (TM[5,1-4]).
     * Called by other YAMCS services to emit on-board events through the native ST[05] pipeline.
     *
     * @param eventId  event definition identifier (uint8, must appear in the MDB enum)
     * @param subtype  1=informative, 2=low, 3=medium, 4=high severity
     * @param auxData  raw bytes for the auxiliary data fields (must match the XTCE sub-container layout)
     */
    public synchronized void raiseEvent(int eventId, int subtype, byte[] auxData) {
        if (!enabled.getOrDefault(eventId, false)) {
            return;
        }
        byte[] appData = new byte[1 + auxData.length];
        appData[0] = (byte) eventId;
        System.arraycopy(auxData, 0, appData, 1, auxData.length);
        emitTm(SERVICE_TYPE, subtype, appData);
    }

    private void persistState() {
        JsonObject obj = new JsonObject();
        for (var entry : enabled.entrySet()) {
            obj.addProperty(String.valueOf(entry.getKey()), entry.getValue());
        }
        db.putJsonObject(MEMENTO_KEY, obj);
    }
}
