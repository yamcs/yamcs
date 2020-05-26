package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.CommandSubscription;
import org.yamcs.client.RestClient;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Commanding.VerifierConfig.CheckWindow;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandRequest.Assignment;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.SubscribeCommandsRequest;
import org.yamcs.protobuf.UpdateCommandHistoryRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;

import io.netty.handler.codec.http.HttpMethod;

public class CommandIntegrationTest extends AbstractIntegrationTest {

    private CommandSubscription subscription;
    private MessageCaptor<CommandHistoryEntry> captor;

    @Before
    public void prepareTests() throws InterruptedException {
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
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry entry = captor.expectTimely();
        CommandId cmdid = entry.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ONE_INT_ARG_TC", cmdid.getCommandName());
        assertEquals(5, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
    }

    @Test
    public void testSendCommandFailedTransmissionConstraint() throws Exception {
        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

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
        IssueCommandRequest cmdreq = getCommand(6, "p1", "2").toBuilder()
                .setDisableTransmissionConstraints(true)
                .build();
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testSendCommandSucceedTransmissionConstraint() throws Exception {
        IssueCommandRequest cmdreq = getCommand(6, "p1", "2");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC2", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY,
                "PENDING");

        cmdhist = captor.poll(2000);
        assertNull(cmdhist);
        Value v = ValueHelper.newValue(true);
        restClient.doRequest("/processors/IntegrationTest/realtime/parameters/REFMDB/SUBSYS1/AllowCriticalTC2",
                HttpMethod.POST, v).get();

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testCommandVerificationContainer() throws Exception {
        IssueCommandRequest cmdreq = getCommand(7);
        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/CONT_VERIF_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", cmdid.getCommandName());
        assertEquals(7, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

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
        long now = TimeEncoding.getWallclockTime();
        StreamCommandIndexRequest options = StreamCommandIndexRequest.newBuilder()
                .setStart(TimeEncoding.toProtobufTimestamp(cmdid.getGenerationTime() - 1))
                .setStop(TimeEncoding.toProtobufTimestamp(now))
                .build();
        resp = restClient.doRequest("/archive/IntegrationTest:streamCommandIndex",
                HttpMethod.POST, options).get();
        ArchiveRecord ar = ArchiveRecord.parseDelimitedFrom(new ByteArrayInputStream(resp));
        assertEquals(1, ar.getNum());
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", ar.getId().getName());
    }

    @Test
    public void testCommandVerificationAlgorithm() throws Exception {
        IssueCommandRequest cmdreq = getCommand(4, "p1", "10", "p2", "20");
        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/ALG_VERIF_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC(p1: 10, p2: 20)", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();

        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdid.getCommandName());
        assertEquals(4, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
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
        issueCommand(getCommand(8).toBuilder()
                .putVerifierConfig("Execution", VerifierConfig.newBuilder().setDisable(true).build())
                .build());

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
        issueCommand(getCommand(9).toBuilder().setDisableVerifiers(true).build());

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
        IssueCommandRequest cmdreq = getCommand(10);
        cmdreq = cmdreq.toBuilder()
                .putVerifierConfig("Complete", VerifierConfig.newBuilder()
                        .setCheckWindow(CheckWindow.newBuilder()
                                .setTimeToStartChecking(0)
                                .setTimeToStopChecking(5000))
                        .build())
                .build();
        issueCommand(cmdreq);

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
        RestClient restClient1 = getRestClient("testuser", "password");

        // Command INT_ARG_TC is allowed
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/INT_ARG_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertTrue(response.hasBinary());

        // Command FLOAT_ARG_TC is denied
        cmdreq = getCommand(5, "float_arg", "-15", "double_arg", "0");
        try {
            resp = restClient1.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/FLOAT_ARG_TC",
                    HttpMethod.POST, cmdreq).get();
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
        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        // insert two values in the command history
        String commandId = commandResponse.getId();
        UpdateCommandHistoryRequest.Builder updateHistoryRequest = UpdateCommandHistoryRequest.newBuilder()
                .setId(commandId);
        updateHistoryRequest.addAttributes(CommandHistoryAttribute.newBuilder()
                .setName("testKey1")
                .setValue(Value.newBuilder().setType(Type.STRING).setStringValue("testValue1")));
        updateHistoryRequest.addAttributes(CommandHistoryAttribute.newBuilder()
                .setName("testKey2")
                .setValue(Value.newBuilder().setType(Type.STRING).setStringValue("testValue2")));
        doRealtimeRequest("/commandhistory/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST,
                updateHistoryRequest.build());

        // Query command history and check that we can retreive the inserted values
        byte[] respDl = restClient.doRequest("/archive/IntegrationTest/commands", HttpMethod.GET).get();
        ListCommandsResponse lastPage = ListCommandsResponse.parseFrom(respDl);
        CommandHistoryEntry lastEntry = lastPage.getEntry(0);
        boolean foundKey1 = false, foundKey2 = false;
        for (CommandHistoryAttribute cha : lastEntry.getAttrList()) {
            if (cha.getName().equals("testKey1") &&
                    cha.getValue().getStringValue().equals("testValue1")) {
                foundKey1 = true;
            }
            if (cha.getName().equals("testKey2") &&
                    cha.getValue().getStringValue().equals("testValue2")) {
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

    private void issueCommand(IssueCommandRequest cmdreq) throws Exception {
        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/CONT_VERIF_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = captor.expectTimely();
        assertEquals(cmdreq.getSequenceNumber(), cmdhist.getCommandId().getSequenceNumber());
    }

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

    private IssueCommandRequest getCommand(int seq, String... args) {
        IssueCommandRequest.Builder b = IssueCommandRequest.newBuilder();
        b.setOrigin("IntegrationTest");
        b.setSequenceNumber(seq);
        for (int i = 0; i < args.length; i += 2) {
            b.addAssignment(Assignment.newBuilder().setName(args[i]).setValue(args[i + 1]).build());
        }

        return b.build();
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
