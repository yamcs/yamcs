package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;

public class ContainerRequestManagerTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setupTest("refmdb");
        MdbFactory.reset();
        EventProducerFactory.setMockup(false);
    }

    @Test
    public void testSubscriptions() throws Exception {
        RefMdbPacketGenerator packetGenerator = new RefMdbPacketGenerator();
        Processor c = ProcessorFactory.create("refmdb", "ContainerRequestManagerTest", packetGenerator);
        ContainerRequestManager rm = c.getContainerRequestManager();
        Mdb xtceDb = c.getMdb();

        RecordingPacketConsumer consumer1 = new RecordingPacketConsumer();
        RecordingPacketConsumer consumer2 = new RecordingPacketConsumer();

        rm.subscribeAll(consumer1);
        rm.subscribeAll(consumer2);

        packetGenerator.generate_PKT1_1();
        packetGenerator.generate_PKT1_3();

        assertEquals(6, consumer1.received.size());
        Iterator<SequenceContainer> it = consumer1.received.iterator();
        assertEquals("ccsds-default", it.next().getName());
        assertEquals("PKT1", it.next().getName());
        assertEquals("PKT1_1", it.next().getName());
        assertEquals("ccsds-default", it.next().getName());
        assertEquals("PKT1", it.next().getName());
        assertEquals("PKT1_3", it.next().getName());

        // Same for 2nd consumer
        assertEquals(6, consumer2.received.size());
        it = consumer2.received.iterator();
        assertEquals("ccsds-default", it.next().getName());
        assertEquals("PKT1", it.next().getName());
        assertEquals("PKT1_1", it.next().getName());
        assertEquals("ccsds-default", it.next().getName());
        assertEquals("PKT1", it.next().getName());
        assertEquals("PKT1_3", it.next().getName());

        // Now try unsubscribing 2nd consumer
        consumer1.reset();
        consumer2.reset();
        rm.unsubscribeAll(consumer2);

        packetGenerator.generate_PKT1_1();
        packetGenerator.generate_PKT1_3();

        assertEquals(6, consumer1.received.size());
        assertEquals(0, consumer2.received.size());

        // Now subscribe 2nd consumer to PKT13 only

        rm.subscribe(consumer2, xtceDb.getSequenceContainer("/REFMDB/SUBSYS1/PKT1_3"));

        packetGenerator.generate_PKT1_1();
        packetGenerator.generate_PKT1_3();

        assertEquals(1, consumer2.received.size());
        SequenceContainer cont = consumer2.received.iterator().next();
        assertEquals("PKT1_3", cont.getName());

        // Subscribe consumer2 to all again
        consumer2.reset();
        rm.subscribeAll(consumer2);

        packetGenerator.generate_PKT1_1();
        packetGenerator.generate_PKT1_3();

        assertEquals(6, consumer2.received.size());
    }

    /**
     * PacketConsumer that stores whatever it consumes for later retrieval
     */
    private static class RecordingPacketConsumer implements ContainerConsumer {
        List<SequenceContainer> received = new ArrayList<>();

        @Override
        public void processContainer(ContainerExtractionResult cer) {
            received.add(cer.getContainer());
        }

        void reset() {
            received.clear();
        }
    }
}
