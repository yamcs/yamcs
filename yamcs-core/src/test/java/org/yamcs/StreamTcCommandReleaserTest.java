package org.yamcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.InternalStream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

class StreamTcCommandReleaserTest extends YarchTestCase {
    @Test
    void pusCommandsAreForwardedWithoutLocalExecution() {
        var stream = new InternalStream(new ExecutionContext(ydb), "tc", StandardTupleDefinitions.TC);
        var received = new ArrayList<Tuple>();
        stream.addSubscriber((s, tuple) -> received.add(tuple));
        var releaser = new StreamTcCommandReleaser();
        releaser.log = new Log(StreamTcCommandReleaser.class);
        releaser.registerOutStream(0, stream, null);

        // In particular, ST[9] must reach the link regardless of destination APID.
        for (int apid : new int[] { 1, 2 }) {
            for (int service : new int[] { 5, 9, 11, 19, 21, 22 }) {
                byte[] packet = { 0x18, (byte) apid, (byte) 0xC0, 0, 0, 5,
                        0x2D, (byte) service, 1, 0, 0, 2 };
                var command = new PreparedCommand(CommandId.newBuilder().setCommandName("/PUS/TEST")
                        .setOrigin("test").setSequenceNumber(received.size()).setGenerationTime(0).build());
                command.setBinary(packet);
                releaser.releaseCommand(command);
                assertArrayEquals(packet, received.get(received.size() - 1)
                        .getColumn(PreparedCommand.CNAME_BINARY));
            }
        }
        assertEquals(12, received.size());
        stream.close();
    }
}
