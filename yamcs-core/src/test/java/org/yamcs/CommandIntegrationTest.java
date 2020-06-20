package org.yamcs;

import static org.junit.Assert.assertEquals;
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
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.Page;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Commanding.VerifierConfig.CheckWindow;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.utils.ValueHelper;

public class CommandIntegrationTest extends AbstractIntegrationTest {

    private ProcessorClient processorClient;
    private ArchiveClient archiveClient;
    private CommandSubscription subscription;
    private MessageCaptor<CommandHistoryEntry> captor;

    @Before
    public void prepareTests() throws InterruptedException {
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        subscription = yamcsClient.createCommandSubscription();
        captor = MessageCaptor.of(subscription);

        SubscribeCommandsRequest request = SubscribeCommandsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);
    }

    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC")
                .withArgument("uint32_arg", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(5)
                .issue()
                .get();
        assertTrue(response.hasBinary());

        CommandHistoryEntry entry = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", entry.getCommandName());
        assertEquals(5, entry.getSequenceNumber());
        assertEquals("IntegrationTest", entry.getOrigin());
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC1")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .issue()
                .get();
        assertTrue(response.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NOK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "NOK");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Message",
                "Transmission constraints check failed");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "NOK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Message",
                "Transmission constraints check failed");
    }

    @Test
    public void testSendCommandDisableTransmissionConstraint() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC1")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .withDisableTransmissionConstraints()
                .issue()
                .get();
        assertTrue(response.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC2")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .issue()
                .get();
        assertTrue(response.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY,
                "PENDING");

        cmdhist = captor.poll(2000);
        assertNull(cmdhist);
        Value v = ValueHelper.newValue(true);
        processorClient.setValue("/REFMDB/SUBSYS1/AllowCriticalTC2", v).get();

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testCommandVerificationContainer() throws Exception {
        IssueCommandResponse response = processorClient
                .prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withOrigin("IntegrationTest")
                .withSequenceNumber(7)
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", cmdhist.getCommandName());
        assertEquals(7, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "PENDING");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "OK");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "PENDING");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "OK");

        // check commands histogram
        Instant start = Instant.parse(cmdhist.getGenerationTimeUTC()).minusMillis(1);
        Page<IndexGroup> page = archiveClient.listCommandIndex(start, Instant.now()).get();
        List<IndexGroup> allItems = new ArrayList<>();
        page.iterator().forEachRemaining(allItems::add);

        assertEquals(1, allItems.size());
        IndexGroup item = allItems.get(0);
        assertEquals(1, item.getEntryCount());
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", item.getId().getName());
    }

    @Test
    public void testCommandVerificationAlgorithm() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/ALG_VERIF_TC")
                .withArgument("p1", 10)
                .withArgument("p2", 20)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(4)
                .issue()
                .get();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC(p1: 10, p2: 20)", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();

        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdhist.getCommandName());
        assertEquals(4, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());
        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "PENDING");

        cmdhist = captor.expectTimely();
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals("packetSeqNum", cha.getName());
        assertEquals(5000, cha.getValue().getSint32Value());

        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 1, 5);

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "PENDING");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "NOK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "NOK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Message",
                "Verifier Complete result: NOK");
    }

    @Test
    public void testCommandWithOneVerifierDisabled() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(8)
                .withVerifierConfig("Execution", VerifierConfig.newBuilder().setDisable(true).build())
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(8, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "DISABLED");

        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "PENDING");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "OK");
    }

    @Test
    public void testCommandWithAllVerifiersDisabled() throws Exception {
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(9)
                .withDisableVerification()
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());
        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(9, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "DISABLED");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "DISABLED");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testCommandVerificationWithModifiedWindow() throws Exception {
        // modify the timeout for the Complete stage from 1000 (in refmdb.xls) to 5000 and sleep 1500 before sending the
        // ack
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(10)
                .withVerifierConfig("Complete", VerifierConfig.newBuilder()
                        .setCheckWindow(CheckWindow.newBuilder()
                                .setTimeToStartChecking(0)
                                .setTimeToStopChecking(5000))
                        .build())
                .issue()
                .get();

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());
        assertEquals(10, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "PENDING");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "PENDING");

        Thread.sleep(1500); // the default verifier would have timed out in 1000ms
        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "OK");
    }

    @Test
    public void testPermissionSendCommand() throws Exception {
        yamcsClient.connect("testuser", "password".toCharArray());

        // Command INT_ARG_TC is allowed
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/INT_ARG_TC")
                .withSequenceNumber(5)
                .withArgument("uint32_arg", 1000)
                .issue()
                .get();
        assertTrue(response.hasBinary());

        // Command FLOAT_ARG_TC is denied
        try {
            response = processorClient.prepareCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC")
                    .withSequenceNumber(5)
                    .withArgument("float_arg", -15)
                    .withArgument("double_arg", 0)
                    .issue()
                    .get();
            fail("should have thrown an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClientException);
        }
    }

    /*-@Test
    public void testValidateCommand() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get(2, TimeUnit.SECONDS);
    
        ValidateCommandRequest cmdreq = getValidateCommand("/REFMDB/SUBSYS1/CRITICAL_TC1", 10, "p1", "2");
        String resp = doRequest("/commanding/validator", HttpMethod.POST, cmdreq, SchemaRest.ValidateCommandRequest.WRITE);
        ValidateCommandResponse vcr = (fromJson(resp, SchemaRest.ValidateCommandResponse.MERGE)).build();
        assertEquals(1, vcr.getCommandSignificanceCount());
        CommandSignificance significance = vcr.getCommandSignificance(0);
        assertEquals(10, significance.getSequenceNumber());
        assertEquals(SignificanceLevelType.CRITICAL, significance.getSignificance().getConsequenceLevel());
        assertEquals("this is a critical command, pay attention", significance.getSignificance().getReasonForWarning());
    
    }*/

    @Test
    public void testUpdateCommandHistory() throws Exception {

        // Send a command a store its commandId
        IssueCommandResponse response = processorClient.prepareCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC")
                .withArgument("uint32_arg", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(5)
                .issue()
                .get();
        assertTrue(response.hasBinary());

        // Insert two values in the command history
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("testKey1", Value.newBuilder()
                .setType(Type.STRING)
                .setStringValue("testValue1")
                .build());
        attributes.put("testKey2", Value.newBuilder()
                .setType(Type.STRING)
                .setStringValue("testValue2")
                .build());
        processorClient.updateCommand(response.getCommandName(), response.getId(), attributes);

        // Query command history and check that we can retreive the inserted values
        CommandHistoryEntry command = archiveClient.listCommands().get().iterator().next();
        boolean foundKey1 = false, foundKey2 = false;
        for (CommandHistoryAttribute attribute : command.getAttrList()) {
            if (attribute.getName().equals("testKey1") &&
                    attribute.getValue().getStringValue().equals("testValue1")) {
                foundKey1 = true;
            }
            if (attribute.getName().equals("testKey2") &&
                    attribute.getValue().getStringValue().equals("testValue2")) {
                foundKey2 = true;
            }
        }
        assertTrue(foundKey1);
        assertTrue(foundKey2);
    }

    /*
     * private ValidateCommandRequest getValidateCommand(String cmdName, int seq, String... args) { NamedObjectId cmdId
     * = NamedObjectId.newBuilder().setName(cmdName).build();
     * 
     * CommandType.Builder cmdb =
     * CommandType.newBuilder().setOrigin("IntegrationTest").setId(cmdId).setSequenceNumber(seq); for(int i =0
     * ;i<args.length; i+=2) {
     * cmdb.addArguments(ArgumentAssignmentType.newBuilder().setName(args[i]).setValue(args[i+1]).build()); }
     * 
     * return ValidateCommandRequest.newBuilder().addCommand(cmdb.build()).build(); }
     */

    private void checkNextCmdHistoryAttr(String name) throws InterruptedException, TimeoutException {
        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(1, cmdhist.getAttrCount());
        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(name, cha.getName());
    }

    private void checkNextCmdHistoryAttr(String name, String value) throws InterruptedException, TimeoutException {
        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(1, cmdhist.getAttrCount());
        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(name, cha.getName());
        assertEquals(value, cha.getValue().getStringValue());
    }

    private void checkNextCmdHistoryAttrStatusTime(String name, String value)
            throws InterruptedException, TimeoutException {
        checkNextCmdHistoryAttr(name + "_Status", value);
        checkNextCmdHistoryAttr(name + "_Time");
    }

    public static class MyTcDataLink extends AbstractTcDataLink {
        static short seqNum = 5000;

        @Override
        public void init(String yamcsInstance, String name, YConfiguration config) {
            super.init(yamcsInstance, name, config);
        }

        @Override
        public void sendTc(PreparedCommand preparedCommand) {
            if (preparedCommand.getCmdName().contains("ALG_VERIF_TC")) {
                commandHistoryPublisher.publish(preparedCommand.getCommandId(), "packetSeqNum", seqNum);
            }
        }

        @Override
        protected Status connectionStatus() {
            return Status.OK;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }
}
