package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.client.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.IssueCommandRequest;
import org.yamcs.protobuf.IssueCommandResponse;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.utils.TimeEncoding;

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

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeQueued_KEY + "_Status", "OK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeQueued_KEY + "_Time");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.TransmissionContraints_KEY + "_Status", "NA");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.TransmissionContraints_KEY + "_Time");

        checkNextCmdHistoryAttr("Verifier_Execution_Status", "PENDING");
        checkNextCmdHistoryAttr("Verifier_Execution_Time");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Status", "OK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Time");

        checkNextCmdHistoryAttr("Verifier_Execution_Status", "OK");
        checkNextCmdHistoryAttr("Verifier_Execution_Time");

        packetGenerator.generateContVerifCmdAck((short) 1001, (byte) 5, 0);

        checkNextCmdHistoryAttr("Verifier_Complete_Status", "PENDING");
        checkNextCmdHistoryAttr("Verifier_Complete_Time");

        checkNextCmdHistoryAttr("Verifier_Complete_Status", "OK");
        checkNextCmdHistoryAttr("Verifier_Complete_Time");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Status", "OK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Time");

        // check commands histogram
        String start = TimeEncoding.toString(TimeEncoding.getWallclockTime() - 10000);
        String stop = TimeEncoding.toString(TimeEncoding.getWallclockTime());
        resp = restClient.doRequest("/archive/IntegrationTest/indexes/commands?start=" + start + "&stop=" + stop,
                HttpMethod.GET, "").get();
        ArchiveRecord ar = fromJson(resp, ArchiveRecord.newBuilder()).build();
        assertEquals(1, ar.getNum());
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", ar.getId().getName());
    }

    @Test
    public void testCommandVerificationAlgorithm() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr).get();

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

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeQueued_KEY + "_Status", "OK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeQueued_KEY + "_Time");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.TransmissionContraints_KEY + "_Status", "NA");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.TransmissionContraints_KEY + "_Time");

        checkNextCmdHistoryAttr("Verifier_Execution_Status", "PENDING");
        checkNextCmdHistoryAttr("Verifier_Execution_Time");

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());

        CommandHistoryAttribute cha = cmdhist.getAttr(0);
        assertEquals("packetSeqNum", cha.getName());
        assertEquals(5000, cha.getValue().getSint32Value());

        packetGenerator.generateAlgVerifCmdAck((short) 25, MyTcDataLink.seqNum, (byte) 1, 5);

        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Status", "OK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.AcknowledgeReleased_KEY + "_Time");

        checkNextCmdHistoryAttr("Verifier_Execution_Status", "OK");
        checkNextCmdHistoryAttr("Verifier_Execution_Time");

        checkNextCmdHistoryAttr("Verifier_Complete_Status", "PENDING");
        checkNextCmdHistoryAttr("Verifier_Complete_Time");

        checkNextCmdHistoryAttr("Verifier_Complete_Status", "NOK");
        checkNextCmdHistoryAttr("Verifier_Complete_Time");

        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Status", "NOK");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Time");
        checkNextCmdHistoryAttr(CommandHistoryPublisher.CommandComplete_KEY + "_Message",
                "Verifier Complete result: NOK");
    }

    public static class MyTcDataLink implements TcDataLink {
        static short seqNum = 5000;
        CommandHistoryPublisher commandHistoryPublisher;
        String name;

        public MyTcDataLink(String yamcsInstance, String name, YConfiguration config) {
            this.name = name;
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
        public long getDataInCount() {
            return 0;
        }

        @Override
        public long getDataOutCount() {
            return 0;
        }

        @Override
        public void resetCounters() {
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
        public YConfiguration getConfig() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
