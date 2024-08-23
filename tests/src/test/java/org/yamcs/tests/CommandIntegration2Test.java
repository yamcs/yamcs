package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.client.Command;
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.protobuf.SubscribeCommandsRequest;

/**
 * Tests commands with two links (instance IntegrationTest2)
 * 
 * @author nm
 *
 */
public class CommandIntegration2Test extends AbstractIntegrationTest {

    private ProcessorClient processorClient;
    private CommandSubscription subscription;
    TcDataLink mtdl1;
    TcDataLink mtdl2;

    String yamcsInstance2 = "instance2";

    @BeforeEach
    public void prepareTests() throws InterruptedException {

        processorClient = yamcsClient.createProcessorClient(yamcsInstance2, "realtime");
        subscription = yamcsClient.createCommandSubscription();

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
