package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.memento.MementoDb;
import org.yamcs.protobuf.Commanding.CommandId;

/**
 * Unit tests for {@link Pus5Service} business logic.
 * <p>
 * Bypasses {@code doInit()} (which needs a live MDB and MementoDb) by
 * setting private fields directly. All side-effects go through a mocked
 * {@link PusCommandReleaser}, captured by Mockito.
 */
class Pus5ServiceTest {

    Pus5Service service;
    PusCommandReleaser mockReleaser;
    MementoDb mockDb;
    Map<Integer, Boolean> enabled;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new Pus5Service();
        mockReleaser = mock(PusCommandReleaser.class);
        mockDb = mock(MementoDb.class);

        when(mockReleaser.getYamcsInstance()).thenReturn("test");

        // PusTcHandler fields are package-private (same package as this test)
        service.releaser = mockReleaser;
        service.log = new Log(Pus5Service.class, "test");

        // Pre-populate enabled map (EVENT_1=1, EVENT_2=2, both enabled)
        Field enabledField = Pus5Service.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabled = (Map<Integer, Boolean>) enabledField.get(service);
        enabled.put(1, true);
        enabled.put(2, true);

        Field dbField = Pus5Service.class.getDeclaredField("db");
        dbField.setAccessible(true);
        dbField.set(service, mockDb);
    }

    /**
     * Build a minimal PUS TC binary with the given subtype and application-data payload.
     * Layout: 11-byte PUS header (bytes 7=type, 8=subtype) + appData.
     */
    private PreparedCommand makeTc(int subtype, byte... appData) {
        CommandId cmdId = CommandId.newBuilder()
                .setOrigin("test")
                .setSequenceNumber(1)
                .setCommandName("/PUS5/TC_5_" + subtype)
                .setGenerationTime(0)
                .build();
        byte[] bin = new byte[11 + appData.length];
        bin[7] = 5;              // PUS service type
        bin[8] = (byte) subtype;
        System.arraycopy(appData, 0, bin, 11, appData.length);
        PreparedCommand pc = new PreparedCommand(cmdId);
        pc.setBinary(bin);
        return pc;
    }

    // ── TC[5,6] disable ─────────────────────────────────────────────────────

    @Test
    void tc56_disablesSingleEvent() {
        // app data: N=1, id=1
        service.handleTc(makeTc(6, (byte) 1, (byte) 1));

        assertFalse(enabled.get(1), "EVENT_1 should be disabled");
        assertTrue(enabled.get(2),  "EVENT_2 should be unaffected");
        verify(mockReleaser).publishAckSent(any());
        verify(mockReleaser).publishCompletion(any(), eq(true), isNull());
        verify(mockDb).putJsonObject(eq(Pus5Service.MEMENTO_KEY), any());
    }

    @Test
    void tc56_disablesBothEvents() {
        // app data: N=2, id=1, id=2
        service.handleTc(makeTc(6, (byte) 2, (byte) 1, (byte) 2));

        assertFalse(enabled.get(1));
        assertFalse(enabled.get(2));
        verify(mockReleaser).publishCompletion(any(), eq(true), isNull());
    }

    @Test
    void tc56_unknownId_completesNok() {
        // app data: N=1, id=99 (not in registry)
        service.handleTc(makeTc(6, (byte) 1, (byte) 99));

        assertTrue(enabled.get(1), "known events must be unchanged");
        assertTrue(enabled.get(2));
        verify(mockReleaser).publishCompletion(any(), eq(false), contains("INVALID_EVENT_ID"));
    }

    // ── TC[5,5] enable ──────────────────────────────────────────────────────

    @Test
    void tc55_enablesDisabledEvent() {
        enabled.put(1, false);

        service.handleTc(makeTc(5, (byte) 1, (byte) 1));

        assertTrue(enabled.get(1), "EVENT_1 should be re-enabled");
        verify(mockReleaser).publishCompletion(any(), eq(true), isNull());
    }

    // ── TC[5,7] disabled-list query ─────────────────────────────────────────

    @Test
    void tc57_emitsDisabledList_whenAllEnabled() {
        service.handleTc(makeTc(7));

        ArgumentCaptor<byte[]> cap = ArgumentCaptor.forClass(byte[].class);
        verify(mockReleaser).emitTm(eq(5), eq(8), cap.capture());
        verify(mockReleaser).publishCompletion(any(), eq(true), isNull());

        byte[] appData = cap.getValue();
        assertEquals(1, appData.length, "only N byte when list is empty");
        assertEquals(0, appData[0] & 0xFF, "N should be 0");
    }

    @Test
    void tc57_emitsDisabledList_withBothDisabled() {
        enabled.put(1, false);
        enabled.put(2, false);

        service.handleTc(makeTc(7));

        ArgumentCaptor<byte[]> cap = ArgumentCaptor.forClass(byte[].class);
        verify(mockReleaser).emitTm(eq(5), eq(8), cap.capture());

        byte[] d = cap.getValue();
        assertEquals(3, d.length);            // N + 2 IDs
        assertEquals(2, d[0] & 0xFF);         // N=2
        assertEquals(1, d[1] & 0xFF);         // sorted: id=1 first
        assertEquals(2, d[2] & 0xFF);         // id=2 second
    }

    @Test
    void tc57_emitsDisabledList_partiallyDisabled() {
        enabled.put(2, false);

        service.handleTc(makeTc(7));

        ArgumentCaptor<byte[]> cap = ArgumentCaptor.forClass(byte[].class);
        verify(mockReleaser).emitTm(eq(5), eq(8), cap.capture());

        byte[] d = cap.getValue();
        assertEquals(2, d.length);      // N + 1 ID
        assertEquals(1, d[0] & 0xFF);   // N=1
        assertEquals(2, d[1] & 0xFF);   // EVENT_2 (id=2)
    }

    // ── raiseEvent API ──────────────────────────────────────────────────────

    @Test
    void raiseEvent_whenEnabled_emitsTm() {
        byte[] auxData = {0x10, 0x20};
        service.raiseEvent(1, 2, auxData);

        ArgumentCaptor<byte[]> cap = ArgumentCaptor.forClass(byte[].class);
        verify(mockReleaser).emitTm(eq(5), eq(2), cap.capture());

        byte[] appData = cap.getValue();
        assertEquals(3, appData.length);
        assertEquals(1,    appData[0] & 0xFF, "eventId prepended");
        assertEquals(0x10, appData[1] & 0xFF, "auxData[0]");
        assertEquals(0x20, appData[2] & 0xFF, "auxData[1]");
    }

    @Test
    void raiseEvent_whenDisabled_isSuppressed() {
        enabled.put(1, false);
        service.raiseEvent(1, 2, new byte[0]);
        verify(mockReleaser, never()).emitTm(anyInt(), anyInt(), any());
    }

    @Test
    void raiseEvent_unknownEventId_isSuppressed() {
        // Event 99 not in registry → getOrDefault returns false
        service.raiseEvent(99, 1, new byte[0]);
        verify(mockReleaser, never()).emitTm(anyInt(), anyInt(), any());
    }
}
