package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtceproc.XtceDbFactory;

public class ContainerRequestManagerTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false, false);
        XtceDbFactory.reset();
        EventProducerFactory.setMockup(false);
    }

    @Test
    public void testSubscriptions() throws Exception {
        RefMdbPacketGenerator packetGenerator = new RefMdbPacketGenerator();
        Channel c = ChannelFactory.create("refmdb", "ContainerRequestManagerTest", "refmdb", "refmdb",
                        new RefMdbTmService(packetGenerator), "refmdb", null);
        ContainerRequestManager rm = c.getContainerRequestManager();

        RecordingPacketConsumer consumer1 = new RecordingPacketConsumer();
        RecordingPacketConsumer consumer2 = new RecordingPacketConsumer();

        rm.subscribeAll(consumer1);
        rm.subscribeAll(consumer2);

        packetGenerator.generate_PKT11();
        packetGenerator.generate_PKT13();

        assertEquals(6, consumer1.received.size());
        Iterator<ItemIdPacketConsumerStruct> it = consumer1.received.keySet().iterator();
        assertEquals("ccsds-default", it.next().def.getName());
        assertEquals("PKT1", it.next().def.getName());
        assertEquals("PKT11", it.next().def.getName());
        assertEquals("ccsds-default", it.next().def.getName());
        assertEquals("PKT1", it.next().def.getName());
        assertEquals("PKT13", it.next().def.getName());

        // Same for 2nd consumer
        assertEquals(6, consumer2.received.size());
        it = consumer2.received.keySet().iterator();
        assertEquals("ccsds-default", it.next().def.getName());
        assertEquals("PKT1", it.next().def.getName());
        assertEquals("PKT11", it.next().def.getName());
        assertEquals("ccsds-default", it.next().def.getName());
        assertEquals("PKT1", it.next().def.getName());
        assertEquals("PKT13", it.next().def.getName());

        // Now try unsubscribing 2nd consumer
        consumer1.reset();
        consumer2.reset();
        rm.unsubscribeAll(consumer2);

        packetGenerator.generate_PKT11();
        packetGenerator.generate_PKT13();

        assertEquals(6, consumer1.received.size());
        assertEquals(0, consumer2.received.size());

        // Now subscribe 2nd consumer to PKT13 only
        rm.subscribe(consumer2, NamedObjectId.newBuilder().setName("/REFMDB/SUBSYS1/PKT13").build());

        packetGenerator.generate_PKT11();
        packetGenerator.generate_PKT13();

        assertEquals(1, consumer2.received.size());
        SequenceContainer cont = consumer2.received.keySet().iterator().next().def;
        assertEquals("PKT13", cont.getName());

        // Subscribe consumer2 to all again
        consumer2.reset();
        rm.subscribeAll(consumer2);

        packetGenerator.generate_PKT11();
        packetGenerator.generate_PKT13();

        assertEquals(6, consumer2.received.size());
    }
    /**
     * PacketConsumer that stores whatever it consumes for later retrieval
     */
    private static class RecordingPacketConsumer implements PacketConsumer {
        Map<ItemIdPacketConsumerStruct, ByteBuffer> received = new LinkedHashMap<ItemIdPacketConsumerStruct, ByteBuffer>();

        @Override
        public void processPacket(ItemIdPacketConsumerStruct iipcs, ByteBuffer content) {
            received.put(iipcs, content);
        }

        void reset() {
            received.clear();
        }
    }
}
