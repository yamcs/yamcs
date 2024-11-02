package org.yamcs.tctm.csp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.yamcs.utils.StringConverter.hexStringToArray;

import org.junit.jupiter.api.Test;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;

public class CspCommandPostprocessorTest {

    @Test
    public void testCrc() {
        var packet = hexStringToArray("8E62250000");

        var postprocessor = new CspCommandPostprocessor();
        postprocessor.setCommandHistoryPublisher(new DummyCommandHistoryPublisher());

        var processed = postprocessor.process(new PreparedCommand(packet));
        assertArrayEquals(hexStringToArray("8E62250000"), processed);

        CspPacket.setCrcFlag(packet, true);
        processed = postprocessor.process(new PreparedCommand(packet));
        assertArrayEquals(hexStringToArray("8E62250100527D5351"), processed);
    }

    private static class DummyCommandHistoryPublisher implements CommandHistoryPublisher {

        @Override
        public void publish(CommandId cmdId, String key, String value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, int value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, long value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, byte[] binary) {
        }

        @Override
        public void addCommand(PreparedCommand pc) {
        }
    }
}
