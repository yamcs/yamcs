package org.yamcs;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.management.ManagementService;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class ContainerRequestManagerTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        YConfiguration.setup("refmdb");
        ManagementService.setup(false);
        XtceDbFactory.reset();
        EventProducerFactory.setMockup(false);
    }

    @Test
    public void testSubscriptions() throws Exception {
        RefMdbPacketGenerator packetGenerator = new RefMdbPacketGenerator();
        YProcessor c = ProcessorFactory.create("refmdb", "ContainerRequestManagerTest", "refmdb", new RefMdbTmService(packetGenerator), "refmdb");
        ContainerRequestManager rm = c.getContainerRequestManager();
        XtceDb xtceDb = c.getXtceDb();

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
        List<SequenceContainer> received = new ArrayList<SequenceContainer>();

        @Override
        public void processContainer(ContainerExtractionResult cer) {
            received.add(cer.getContainer());
        }

        void reset() {
            received.clear();
        }
    }
}
