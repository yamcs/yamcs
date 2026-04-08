package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

public class Pus5ServiceTest {

    @Test
    void emitsXtceAlignedEventPayloadAndRespectsEnableDisableTc() {
        RecordingSimulator simulator = new RecordingSimulator();
        Pus5Service service = new Pus5Service(simulator);
        service.start();

        service.sendEvent();
        PusTmPacket event = simulator.takeLast();
        assertEquals(2, event.getUserDataBuffer().get(0) & 0xFF);
        assertEquals(1, event.getBytes().length - PusTmPacket.DATA_OFFSET - 2);

        simulator.clear();
        service.executeTc(tc(simulator.getMainApid(), 6, 2));
        assertEquals(2, simulator.packets.size());

        simulator.clear();
        service.sendEvent();
        event = simulator.takeLast();
        assertEquals(1, event.getUserDataBuffer().get(0) & 0xFF);
        assertEquals(1, event.getBytes().length - PusTmPacket.DATA_OFFSET - 2);
    }

    @Test
    void tc05_7ReportsDisabledEventsViaService5Telemetry() {
        RecordingSimulator simulator = new RecordingSimulator();
        Pus5Service service = new Pus5Service(simulator);
        service.start();

        simulator.clear();
        service.executeTc(tc(simulator.getMainApid(), 6, 2));
        simulator.clear();

        service.executeTc(new PusTcPacket(simulator.getMainApid(), 0, 0, 5, 7));

        assertEquals(3, simulator.packets.size());
        assertEquals(PusSimulator.PUS_TYPE_ACK, simulator.packets.get(0).getType());
        assertEquals(5, simulator.packets.get(1).getType());
        assertEquals(2, simulator.packets.get(1).getUserDataBuffer().get(0) & 0xFF);
        assertEquals(PusSimulator.PUS_TYPE_ACK, simulator.packets.get(2).getType());
    }

    private static PusTcPacket tc(int apid, int subtype, int eventId) {
        PusTcPacket tc = new PusTcPacket(apid, 2, 0, 5, subtype);
        ByteBuffer bb = tc.getUserDataBuffer();
        bb.put((byte) 1);
        bb.put((byte) eventId);
        return tc;
    }

    private static class RecordingSimulator extends PusSimulator {
        final List<PusTmPacket> packets = new ArrayList<>();

        RecordingSimulator() {
            super(new File("."));
            executor = new NoopScheduler();
        }

        @Override
        void transmitRealtimeTM(PusTmPacket packet) {
            packets.add(packet);
        }

        void clear() {
            packets.clear();
        }

        PusTmPacket takeLast() {
            return packets.get(packets.size() - 1);
        }
    }

    private static class NoopScheduler extends ScheduledThreadPoolExecutor {

        NoopScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new NoopScheduledFuture();
        }
    }

    private static class NoopScheduledFuture implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
