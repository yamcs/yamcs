package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import io.netty.handler.codec.http.HttpMethod;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Rest.RestSendCommandRequest;
import org.yamcs.tctm.TcUplinker;

import com.google.common.util.concurrent.AbstractService;

public class IntegrationTestComVerif extends AbstractIntegrationTest {

    @Test
    public void testCommandVerificationContainter() throws Exception {
        WebSocketRequest wsr = new WebSocketRequest("cmdhistory", "subscribe");
        wsClient.sendRequest(wsr);
        
        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/CONT_VERIF_TC", 7);
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("{}", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/CONT_VERIF_TC", cmdid.getCommandName());
        assertEquals(7, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
        packetGenerator.generateContVerifCmdAck((short)1001, (byte)0, 0);



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
        assertEquals("Verifier_Execution", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());

        packetGenerator.generateContVerifCmdAck((short)1001, (byte)5, 0);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());


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

       
        RestSendCommandRequest cmdreq = getCommand("/REFMDB/SUBSYS1/ALG_VERIF_TC", 4, "p1", "10", "p2", "20");
        String resp = httpClient.doRequest("http://localhost:9190/IntegrationTest/api/commanding/queue", HttpMethod.POST, toJson(cmdreq, SchemaRest.RestSendCommandRequest.WRITE), currentUser);
        assertEquals("{}", resp);

        CommandHistoryEntry cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);

        assertNotNull(cmdhist);
        CommandId cmdid = cmdhist.getCommandId();
        assertEquals("/REFMDB/SUBSYS1/ALG_VERIF_TC", cmdid.getCommandName());
        assertEquals(4, cmdid.getSequenceNumber());
        assertEquals("IntegrationTest", cmdid.getOrigin());
        packetGenerator.generateAlgVerifCmdAck((short)25, MyTcUplinliker.seqNum, (byte)0, 0);


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

        packetGenerator.generateAlgVerifCmdAck((short)25, MyTcUplinliker.seqNum, (byte)1, 5);

        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Execution", cha.getName());
        assertEquals("OK", cha.getValue().getStringValue());


        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals("Verifier_Complete", cha.getName());
        
        cmdhist = wsListener.cmdHistoryDataList.poll(3, TimeUnit.SECONDS);
        assertNotNull(cmdhist);
        assertEquals(1, cmdhist.getAttrCount());
        cha = cmdhist.getAttr(0);
        assertEquals(CommandHistoryPublisher.CommandFailed_KEY, cha.getName());
        assertEquals("NOK", cha.getValue().getStringValue());
    }



    public static class MyTcUplinliker extends AbstractService implements TcUplinker {
        static short seqNum = 5000;
        CommandHistoryPublisher commandHistoryPublisher;
        
        public MyTcUplinliker(String yamcsInstance, String name) {
        }

        @Override
        public String getLinkStatus() {
            return "OK";
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
            commandHistoryPublisher.publish(preparedCommand.getCommandId(), "packetSeqNum", seqNum);
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
