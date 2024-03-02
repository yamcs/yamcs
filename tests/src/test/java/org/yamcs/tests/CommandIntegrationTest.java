package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.Command;
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.Page;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Commanding.VerifierConfig.CheckWindow;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ValueHelper;

import com.google.protobuf.util.Timestamps;

public class CommandIntegrationTest extends AbstractIntegrationTest {

    private ProcessorClient processorClient;
    private ArchiveClient archiveClient;
    private CommandSubscription subscription;
    private MessageCaptor<CommandHistoryEntry> captor;

    @BeforeEach
    public void prepareTests() throws Exception {
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
        archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        subscription = yamcsClient.createCommandSubscription();
        captor = MessageCaptor.of(subscription);

        SubscribeCommandsRequest request = SubscribeCommandsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();
    }

    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC")
                .withArgument("uint32_arg", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(5)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        CommandHistoryEntry entry = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", entry.getCommandName());
        assertEquals(5, entry.getSequenceNumber());
        assertEquals("IntegrationTest", entry.getOrigin());
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC1")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NOK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.NOK,
                "Transmission constraints check failed");

        checkNextCmdHistoryAck(CommandHistoryPublisher.CommandComplete_KEY, AckStatus.NOK,
                "Transmission constraints check failed");
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint2() throws Exception {
        processorClient.setValue("/REFMDB/SUBSYS1/AllowCriticalTC2", ValueHelper.newValue(false)).get();
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC2")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(12)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdhist.getCommandName());
        assertEquals(12, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.PENDING);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NOK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.NOK,
                "Transmission constraints check failed");

        checkNextCmdHistoryAck(CommandHistoryPublisher.CommandComplete_KEY, AckStatus.NOK,
                "Transmission constraints check failed");
    }

    @Test
    public void testSendCommandDisableTransmissionConstraint() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC1")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .withDisableTransmissionConstraints()
                .issue()
                .get();
        assertNotNull(command.getBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CRITICAL_TC2")
                .withArgument("p1", 2)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(6)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdhist.getCommandName());
        assertEquals(6, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);

        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY,
                AckStatus.PENDING);

        Value v = ValueHelper.newValue(true);
        processorClient.setValue("/REFMDB/SUBSYS1/AllowCriticalTC2", v).get();

        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
    }

    @Test
    public void testCommandVerificationContainer() throws Exception {
        Command command = processorClient
                .prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withOrigin("IntegrationTest")
                .withSequenceNumber(7)
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", command.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", cmdhist.getCommandName());
        assertEquals(7, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.PENDING);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.OK);

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.PENDING);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.CommandComplete_KEY, AckStatus.OK);

        // check commands histogram
        Instant start = Instant.ofEpochMilli(Timestamps.toMillis(cmdhist.getGenerationTime())).minusMillis(1);
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
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/ALG_VERIF_TC")
                .withArgument("p1", 10)
                .withArgument("p2", 20)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(4)
                .issue()
                .get();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC(p1: 10, p2: 20)", command.getSource());
        CommandHistoryEntry cmdhist = captor.expectTimely();

        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdhist.getCommandName());
        assertEquals(4, cmdhist.getSequenceNumber());
        assertEquals("IntegrationTest", cmdhist.getOrigin());
        packetGenerator.generateAlgVerifCmdAck((short) 25, (short) 5000, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.PENDING);

        cmdhist = captor.expectTimely();
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals("packetSeqNum", cha.getName());
        assertEquals(5000, cha.getValue().getSint32Value());

        packetGenerator.generateAlgVerifCmdAck((short) 25, (short) 5000, (byte) 1, 5);

        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.PENDING);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.NOK);
        checkNextCmdHistoryAck("CommandComplete", AckStatus.NOK, "Verifier Complete result: NOK");
    }

    @Test
    public void testCommandWithOneVerifierDisabled() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(8)
                .withVerifierConfig("Execution", VerifierConfig.newBuilder().setDisable(true).build())
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", command.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(8, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.DISABLED);

        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.PENDING);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.CommandComplete_KEY, AckStatus.OK);
    }

    @Test
    public void testCommandWithAllVerifiersDisabled() throws Exception {
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(9)
                .withDisableVerification()
                .issue()
                .get();

        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", command.getSource());
        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(9, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.DISABLED);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.DISABLED);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
    }

    @Test
    public void testCommandVerificationWithModifiedWindow() throws Exception {
        // modify the timeout for the Complete stage from 1000 (in refmdb.xls) to 5000 and sleep 1500 before sending the
        // ack
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC")
                .withSequenceNumber(10)
                .withVerifierConfig("Complete", VerifierConfig.newBuilder()
                        .setCheckWindow(CheckWindow.newBuilder()
                                .setTimeToStartChecking(0)
                                .setTimeToStopChecking(5000))
                        .build())
                .issue()
                .get();

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", command.getSource());
        assertEquals(10, cmdhist.getSequenceNumber());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeQueued_KEY, AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.TransmissionConstraints_KEY, AckStatus.NA);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.PENDING);
        checkNextCmdHistoryAck(CommandHistoryPublisher.AcknowledgeReleased_KEY, AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Execution", AckStatus.OK);
        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.PENDING);

        Thread.sleep(1500); // the default verifier would have timed out in 1000ms
        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAck("Verifier_Complete", AckStatus.OK);
        checkNextCmdHistoryAck(CommandHistoryPublisher.CommandComplete_KEY, AckStatus.OK);
    }

    @Test
    public void testPermissionSendCommand() throws Exception {
        yamcsClient.login("testuser", "password".toCharArray());

        // Command INT_ARG_TC is allowed
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/INT_ARG_TC")
                .withSequenceNumber(5)
                .withArgument("uint32_arg", 1000)
                .issue()
                .get();
        assertNotNull(command.getBinary());

        // Command FLOAT_ARG_TC is denied
        try {
            command = processorClient.prepareCommand("/REFMDB/SUBSYS1/FLOAT_ARG_TC")
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
        Command command = processorClient.prepareCommand("/REFMDB/SUBSYS1/ONE_INT_ARG_TC")
                .withArgument("uint32_arg", 1000)
                .withOrigin("IntegrationTest")
                .withSequenceNumber(5)
                .issue()
                .get();
        assertNotNull(command.getBinary());

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
        processorClient.updateCommand(command.getName(), command.getId(), attributes).get();

        // Query command history and check that we can retreive the inserted values
        Command entry = archiveClient.listCommands().get().iterator().next();
        assertEquals("testValue1", entry.getAttribute("testKey1"));
        assertEquals("testValue2", entry.getAttribute("testKey2"));
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

    private void checkNextCmdHistoryAttr(String name, String value) throws InterruptedException, TimeoutException {
        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(1, cmdhist.getAttrCount());
        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(name, cha.getName());
        assertEquals(value, cha.getValue().getStringValue());
    }

    private void checkNextCmdHistoryAck(String name, AckStatus ack)
            throws InterruptedException, TimeoutException {
        checkNextCmdHistoryAck(name, ack, null);
    }

    private void checkNextCmdHistoryAck(String name, AckStatus ack, String message)
            throws InterruptedException, TimeoutException {

        CommandHistoryEntry cmdhist = captor.expectTimely();

        // Filter out permitted attributes, that we are not testing for
        var filteredArgs = cmdhist.getAttrList().stream()
                .filter(attr -> !attr.getName().equals(name + "_Return"))
                .collect(Collectors.toList());

        assertEquals(message == null ? 2 : 3, filteredArgs.size());

        CommandHistoryAttribute cha = filteredArgs.get(0);
        assertEquals(name + "_Status", cha.getName());
        assertEquals(ack.name(), cha.getValue().getStringValue());

        cha = filteredArgs.get(1);
        assertEquals(name + "_Time", cha.getName());

        if (message != null) {
            cha = filteredArgs.get(2);
            assertEquals(name + "_Message", cha.getName());
            assertEquals(message, cha.getValue().getStringValue());
        }
    }
}
