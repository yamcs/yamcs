package org.yamcs.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.yamcs.cmdhistory.StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/**
 * Generates and saves some some command history and then it performs a replay
 * 
 */
public class CmdHistoryRecordingTest extends YarchTestCase {

    @Test
    public void testRecording() throws Exception {
        final int n = 100;
        ydb.execute("create stream " + REALTIME_CMDHIST_STREAM_NAME
                + StandardTupleDefinitions.TC.getStringDefinition());
        CommandHistoryRecorder cmdHistRecorder = new CommandHistoryRecorder();
        List<String> l = Arrays.asList(REALTIME_CMDHIST_STREAM_NAME);
        Map<String, Object> m = new HashMap<>();
        m.put("streams", l);
        cmdHistRecorder.init(ydb.getName(), "test", YConfiguration.wrap(m));
        cmdHistRecorder.startAsync();

        Stream rtstream = ydb.getStream(StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);
        assertNotNull(rtstream);

        for (int i = 0; i < n; i++) {
            CommandId id = CommandId.newBuilder().setOrigin("testorigin").setCommandName("test" + i)
                    .setGenerationTime(i).setSequenceNumber(0).build();
            PreparedCommand pc = new PreparedCommand(id);
            pc.setBinary(new byte[20]);
            pc.setUsername("nico");
            Tuple t = pc.toTuple();
            rtstream.emitTuple(t);
        }

        // read back the data from the table directly in yarch
        List<Tuple> tlist = fetchAllFromTable(CommandHistoryRecorder.TABLE_NAME);
        assertEquals(n, tlist.size());
        for (int i = 0; i < n; i++) {
            Tuple tuple = tlist.get(i);
            Mdb mdb = MdbFactory.getInstance(instance);
            PreparedCommand pc = PreparedCommand.fromTuple(tuple, mdb);
            assertEquals("test" + i, pc.getCmdName());
        }

        cmdHistRecorder.stopAsync();
    }
}
