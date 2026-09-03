package org.yamcs.tctm.cfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.protobuf.Db.Event;

public class CfsEventDecoderTest {

    static final int MSG_ID = 0x0808;

    CfsEventDecoder decoder;
    Queue<Event> eventQueue;

    @BeforeEach
    public void setup() throws Exception {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(true);
        eventQueue = EventProducerFactory.getMockupQueue();
        eventQueue.clear();

        decoder = new CfsEventDecoder();
        YConfiguration config = YConfiguration.wrap(Map.of(
                "msgIds", List.of(MSG_ID),
                "streams", List.of("tm_realtime"),
                "byteOrder", "BIG_ENDIAN",
                "appNameMax", 20,
                "eventMsgMax", 122));
        decoder.init("test-instance", "CfsEventDecoder", config);
    }

    private Tuple makeTuple(byte[] packet) {
        TupleDefinition td = new TupleDefinition();
        td.addColumn("gentime", DataType.TIMESTAMP);
        td.addColumn("rectime", DataType.TIMESTAMP);
        td.addColumn("packet", DataType.BINARY);
        return new Tuple(td, new Object[] { 0L, 0L, packet });
    }

    private byte[] makePacket(int length, String app, int eventId, int eventType, int processorId, String msg) {
        ByteBuffer buf = ByteBuffer.allocate(length);
        buf.putShort(0, (short) MSG_ID);
        buf.putShort(4, (short) (length - 7)); // CCSDS packet length field
        buf.position(12);
        byte[] appBytes = app.getBytes(StandardCharsets.US_ASCII);
        buf.put(appBytes);
        buf.position(12 + 20);
        buf.putShort((short) eventId);
        buf.putShort((short) eventType);
        buf.putInt(0); // spacecraftId
        buf.putInt(processorId);
        buf.put(msg.getBytes(StandardCharsets.US_ASCII));
        return buf.array();
    }

    @Test
    public void testValidPacket() {
        byte[] packet = makePacket(12 + 20 + 12 + 5, "EVS", 42, 3, 1, "hello");
        decoder.onTuple(null, makeTuple(packet));

        assertEquals(1, eventQueue.size());
        Event ev = eventQueue.poll();
        assertEquals(EventSeverity.ERROR, ev.getSeverity());
        assertEquals("EVID42", ev.getType());
        assertEquals("/CFS/CPU1/EVS", ev.getSource());
        assertEquals("hello", ev.getMessage());
    }

    @Test
    public void testShortPacketGeneratesWarningEvent() {
        byte[] packet = new byte[20];
        ByteBuffer.wrap(packet).putShort(0, (short) MSG_ID);
        decoder.onTuple(null, makeTuple(packet));

        assertEquals(1, eventQueue.size());
        Event ev = eventQueue.poll();
        assertEquals(EventSeverity.WARNING, ev.getSeverity());
        assertEquals("SHORT_PACKET", ev.getType());
        assertTrue(ev.getMessage().contains("shorter than minimum"));
    }

    @Test
    public void testTruncatedPacketGeneratesWarningEvent() {
        // structurally decodable, but 5 bytes shorter than the length
        // declared in the CCSDS primary header
        byte[] packet = makePacket(12 + 20 + 12 + 5, "EVS", 42, 1, 1, "hello");
        ByteBuffer.wrap(packet).putShort(4, (short) (packet.length - 7 + 5));
        decoder.onTuple(null, makeTuple(packet));

        assertEquals(2, eventQueue.size());
        Event ev = eventQueue.poll();
        assertEquals(EventSeverity.WARNING, ev.getSeverity());
        assertEquals("SHORT_PACKET", ev.getType());
        assertTrue(ev.getMessage().contains("declared in the CCSDS primary header"));
        assertEquals("hello", eventQueue.poll().getMessage());
    }

    @Test
    public void testTinyPacketIsIgnored() {
        decoder.onTuple(null, makeTuple(new byte[1]));
        assertEquals(0, eventQueue.size());
    }

    @Test
    public void testNonMatchingMsgIdIsIgnored() {
        byte[] packet = new byte[10];
        ByteBuffer.wrap(packet).putShort(0, (short) 0x0900);
        decoder.onTuple(null, makeTuple(packet));
        assertEquals(0, eventQueue.size());
    }
}
