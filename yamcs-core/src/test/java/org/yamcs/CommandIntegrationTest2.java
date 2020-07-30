package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.Command;
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.Page;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Commanding.VerifierConfig.CheckWindow;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.utils.ValueHelper;

/**
 * Tests commands with two links (instance IntegrationTest2)
 * 
 * @author nm
 *
 */
public class CommandIntegrationTest2 extends AbstractIntegrationTest {

    private ProcessorClient processorClient;
    private CommandSubscription subscription;
    private MessageCaptor<CommandHistoryEntry> captor;
    TcDataLink mtdl1;
    TcDataLink mtdl2;

    String yamcsInstance2 = "IntegrationTest2";

    @Before
    public void prepareTests() throws InterruptedException {

        processorClient = yamcsClient.createProcessorClient(yamcsInstance2, "realtime");
        subscription = yamcsClient.createCommandSubscription();
        captor = MessageCaptor.of(subscription);

        SubscribeCommandsRequest request = SubscribeCommandsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        mtdl1 = TcDataLink.instance[1];
        mtdl2 = TcDataLink.instance[2];
        assertNotNull(mtdl1);
        assertNotNull(mtdl2);
        mtdl1.commands.clear();
        mtdl2.commands.clear();
    }

    @Test
    public void testSendCommandDifferentLinks() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC")
                .withArgument("uint32_arg", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(20)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        assertEquals(1, mtdl1.commands.size());
        assertEquals(0, mtdl2.commands.size());

        command = processorClient.prepareCommand("/REFMDB/SUBSYS1/LE_ARG_TC")
                .withArgument("p2", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(21)
                .issue()
                .get();

        assertEquals(1, mtdl1.commands.size());
        assertEquals(1, mtdl2.commands.size());
    }
}
