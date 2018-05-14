package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Rest.IssueCommandRequest;
import org.yamcs.protobuf.Rest.IssueCommandResponse;
import org.yamcs.tctm.TcDataLink;

import com.google.common.util.concurrent.AbstractService;

import io.netty.handler.codec.http.HttpMethod;

public class ComVerifIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void testCommandVerificationContainer() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(7);
        String resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/CONT_VERIF_TC",
                HttpMethod.POST, toJson(cmdreq)).get();
        IssueCommandResponse response = fromJson(resp, IssueCommandResponse.newBuilder()).build();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC()", response.getSource());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", cmdid.getCommandName());
        assertEquals(7, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 0, 0);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("NA", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Execution_Status", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Execution_Time", cha.getName());

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete_Status", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete_Time", cha.getName());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandComplete_KEY, cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
    }

    @Test
    public void testCommandVerificationAlgorithm() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);

        IssueCommandRequest cmdreq = getCommand(4, "p1", "10", "p2", "20");
        String resp = restClient.doRequest("/processors/IntegrationTest/realtime/commands/REFMDB/SUBSYS1/ALG_VERIF_TC",
                HttpMethod.POST, toJson(cmdreq)).get();
        IssueCommandResponse response = fromJson(resp, IssueCommandResponse.newBuilder()).build();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC(p1: 10, p2: 20)", response.getSource());

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdid.getCommandName());
        assertEquals(4, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 0, 0);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);

        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.TransmissionContraints_KEY, cha.getName());
        assertEquals("NA", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        cha = cmdhist.getAttr(0);
        assertEquals("packetSeqNum", cha.getName());
        assertEquals(5000, cha.getValue().getSint32Value());

        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 1, 5);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Execution_Status", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertEquals("Verifier_Execution_Time", cmdhist.getAttr(0).getName());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete_Status", cha.getName());
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertEquals("Verifier_Complete_Time", cmdhist.getAttr(0).getName());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandComplete_KEY, cha.getName());
        assertEquals("NOK", cha.getValue().getStringValue());

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandFailed_KEY, cha.getName());
        assertEquals("Verifier Complete result: NOK", cha.getValue().getStringValue());
    }

    public static class MyTcDataLink extends AbstractService implements TcDataLink {
        static short seqNum = 5000;
        CommandHistoryPublisher commandHistoryPublisher;

        public MyTcDataLink(String yamcsInstance, String name) {
        }

        @Override
        public Status getLinkStatus() {
            return Status.OK;
        }

        @Override
        public String getDetailedStatus() {
            return "OK";
        }

        @Override
        public void enable() {

        }

        @Override
        public void disable() {

        }

        @Override
        public boolean isDisabled() {
            return false;
        }

        @Override
        public long getDataCount() {
            return 0;
        }

        @Override
        public void sendTc(PreparedCommand preparedCommand) {
            if (preparedCommand.getCmdName().contains("ALG_VERIF_TC")) {
                commandHistoryPublisher.publish(preparedCommand.getCommandId(), "packetSeqNum", seqNum);
            }
        }

        @Override
        public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher) {
            this.commandHistoryPublisher = commandHistoryPublisher;

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
