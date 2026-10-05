package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Commanding.CommandId;

public class UplinkPacketHandlerAggregationTest {

    @Test
    public void testProviderAggregationKeySplitsFrames() {
        var vcConfig = Map.<String, Object>of(
                "vcId", 0,
                "service", "PACKET",
                "multiplePacketsPerFrame", true);
        var config = YConfiguration.wrap(Map.<String, Object>of(
                "spacecraftId", 1,
                "maxFrameLength", 100,
                "errorDetection", "NONE",
                "virtualChannels", List.of(vcConfig)));
        var parameters = new TcManagedParameters(config, "test", "test");
        EventProducerFactory.setMockup(true);
        var handler = new UplinkPacketHandler<TcTransferFrame>("test", "test.vc0", parameters.getVcParams(0));
        EventProducerFactory.setMockup(false);
        handler.setDataAvailableSemaphore(new Semaphore(0));
        handler.setFrameEncapsulator(new TcFrameEncapsulator() {
            @Override
            public Object getAggregationKey(PreparedCommand command) {
                return command.getBooleanAttribute("route");
            }

            @Override
            public byte[] encapsulate(UplinkTransferFrame frame) {
                return frame.getData();
            }
        });

        handler.sendCommand(command(1, false));
        handler.sendCommand(command(2, true));
        handler.sendCommand(command(3, true));

        assertEquals(1, handler.getFrame().getCommands().size());
        assertEquals(2, handler.getFrame().getCommands().size());
    }

    private static PreparedCommand command(int sequenceNumber, boolean route) {
        var id = CommandId.newBuilder()
                .setGenerationTime(sequenceNumber)
                .setOrigin("test")
                .setSequenceNumber(sequenceNumber)
                .setCommandName("test")
                .build();
        PreparedCommand command = new PreparedCommand(id);
        command.setBinary(new byte[] { (byte) sequenceNumber });
        command.disablePostprocessing(true);
        command.setAttribute("route", route);
        return command;
    }
}
