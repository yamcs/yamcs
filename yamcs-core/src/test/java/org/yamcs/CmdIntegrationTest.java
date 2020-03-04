package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Commanding.CommandOptions;
import org.yamcs.protobuf.Commanding.VerifierConfig;
import org.yamcs.protobuf.Commanding.VerifierConfig.CheckWindow;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.AbstractTcDataLink;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueHelper;

import io.netty.handler.codec.http.HttpMethod;

public class CmdIntegrationTest extends AbstractIntegrationTest {

    @Before
    public void subscribeCmdHistory() {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
    }


    @Test
    public void testSendCommandNoTransmissionConstraint() throws Exception {
        wsListener.cmdHistoryDataList.clear();

        IssueCommandRequest cmdreq = getCommand(5, "uint32_arg", "1000");
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/ONE_INT_ARG_TC", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
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

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

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
        CommandOptions co = CommandOptions.newBuilder().setDisableTransmissionConstraints(true).build();
        IssueCommandRequest cmdreq = getCommand(6, "p1", "2").toBuilder().setCommandOptions(co).build();
        byte[] resp = doRealtimeRequest("/commands/REFMDB/SUBSYS1/CRITICAL_TC1", HttpMethod.POST, cmdreq);
        IssueCommandResponse commandResponse = IssueCommandResponse.parseFrom(resp);
        assertTrue(commandResponse.hasBinary());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC1", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

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

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CRITICAL_TC2", cmdid.getCommandName());
        assertEquals(6, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "PENDING");

        cmdhist = wsListener.cmdHistoryDataList.poll(2, TimeUnit.SECONDS);
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

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
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

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdid.getCommandName());
        assertEquals(4, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");
        checkNextCmdHistoryAttrStatusTime("Verifier_Execution", "PENDING");

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
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
        CommandOptions co = CommandOptions.newBuilder()
                .putVerifierConfig("Execution", VerifierConfig.newBuilder().setDisable(true).build())
                .build();

        issueCommand(8, co);

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "PENDING");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime("Verifier_Complete", "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.CommandComplete_KEY, "OK");
    }

    @Test
    public void testCommandWithAllVerifiersDisabled() throws Exception {
        CommandOptions co = CommandOptions.newBuilder().setDisableVerifiers(true).build();

        issueCommand(9, co);

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.Queue_KEY, "default");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeQueued_KEY, "OK");
        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.TransmissionContraints_KEY, "NA");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttrStatusTime(CommandHistoryPublisher.AcknowledgeReleased_KEY, "OK");
    }

    @Test
    public void testCommandVerificationWithModifiedWindow() throws Exception {
        // modify the timeout for the Complete stage from 1000 (in refmdb.xls) to 5000 and sleep 1500 before sending the
        // ack
        CommandOptions co = CommandOptions.newBuilder()
                .putVerifierConfig("Complete", VerifierConfig.newBuilder()
                        .setCheckWindow(CheckWindow.newBuilder()
                                .setTimeToStartChecking(0)
                                .setTimeToStopChecking(5000))
                        .build())
                .build();

        issueCommand(10, co);

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

    private void issueCommand(int id, CommandOptions co) throws Exception {
        IssueCommandRequest cmdreq = getCommand(id);
        if (co != null) {
            cmdreq = cmdreq.toBuilder().setCommandOptions(co).build();
        }

        byte[] resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/CONT_VERIF_TC",
                HttpMethod.POST, cmdreq).get();
        IssueCommandResponse response = IssueCommandResponse.parseFrom(resp);
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertEquals(id, cmdhist.getCommandId().getSequenceNumber());
    }

    
    
    
    
    public static class MyTcDataLink extends AbstractTcDataLink {
        static short seqNum = 5000;

        public MyTcDataLink(String yamcsInstance, String name, YConfiguration config) {
            super(yamcsInstance, name, config);
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
