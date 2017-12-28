package org.yamcs.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.yamcs.tctm.TcDataLinkInitialiser;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;

/**
 * Generates and saves some some command history and then it performs a replay via ActiveMQ
 * 
 * 
 * @author nm
 *
 */
public class CmdHistoryRecordingTest extends YarchTestCase {
    
    @Test
    public void testRecording() throws Exception {
        final int n=100;
        ydb.execute("create stream "+StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME+TcDataLinkInitialiser.TC_TUPLE_DEFINITION.getStringDefinition());
        CommandHistoryRecorder cmdHistRecorder =new CommandHistoryRecorder(ydb.getName()); 
        cmdHistRecorder.startAsync();
       
        Stream rtstream=ydb.getStream(StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);
        assertNotNull(rtstream);
        
        for(int i=0;i<n;i++) {
            CommandId id=CommandId.newBuilder().setOrigin("testorigin").setCommandName("test"+i)
                .setGenerationTime(i).setSequenceNumber(0).build();
            PreparedCommand pc = new PreparedCommand(id);
            pc.setSource("test1(blabla)");
            pc.setBinary(new byte[20]);
            pc.setUsername("nico");
            Tuple t = pc.toTuple();
            rtstream.emitTuple(t);
        }
        
        //read back the data from the table directly in yarch
        List<Tuple> tlist = fetchAllFromTable(CommandHistoryRecorder.TABLE_NAME);
        assertEquals(n, tlist.size());
        for(int i=0; i<n; i++) {
            Tuple tuple = tlist.get(i);
            PreparedCommand pc = PreparedCommand.fromTuple(tuple);
            assertEquals("test"+i, pc.getCmdName());
        }
                
        cmdHistRecorder.stopAsync();
    }
}
