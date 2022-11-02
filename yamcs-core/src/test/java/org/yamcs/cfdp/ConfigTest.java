package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.cfdp.OngoingCfdpTransfer.FaultHandlingAction;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ConfigTest {
    static String yamcsInstance = "cfdp-config-test";

    @BeforeAll
    public static void beforeClass() throws StreamSqlException, ParseException {
        EventProducerFactory.setMockup(false);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        ydb.execute("create stream cfdp_in(pdu binary)");
        ydb.execute("create stream cfdp_out(pdu binary)");
        TimeEncoding.setUp();
    }

    @Test
    public void testInvalidStream() throws Exception {
        verifyInvalidConfig("{inStream: cfdp_in1, outStream: cfdp_out}", "cannot find stream cfdp_in1");
        verifyInvalidConfig("{inStream: cfdp_in, outStream: cfdp_out1}", "cannot find stream cfdp_out1");
    }

    @Test
    public void testNoEntity() throws Exception {
        verifyInvalidConfig("", "No local entity specified");
        verifyInvalidConfig("{localEntities: [ {name: local12, id: 12}]}", "No remote entity specified");
    }

    @Test
    public void testDuplicateEntity() throws Exception {
        verifyInvalidConfig("{localEntities: [ {name: local12, id: 12}, {name: local12, id: 13}]}",
                "Duplicate local entity 'local12'");

        String confs = "{"
                + "   localEntities: [ {name: local12, id: 12}], "
                + "   remoteEntities: [ {name: remote15, id: 15}, {name: remote15, id: 16}], "
                + "}";
        verifyInvalidConfig(confs, "Duplicate remote entity 'remote15'");
    }

    @Test
    public void testFaultHandler() throws Exception {
        String confs = "{"
                + "   localEntities: [ {name: local12, id: 12}], "
                + "   remoteEntities: [ {name: remote15, id: 15}], "
                + "   senderFaultHandlers: { AckLimitReached: SUSPEND},"
                + "   receiverFaultHandlers: { AckLimitReached: ABANDON}"
                + "}";

        YConfiguration conf = new YConfiguration("cfdp", new ByteArrayInputStream(confs.getBytes()), "test");
        CfdpService cfdpService = new CfdpService();
        conf = cfdpService.getSpec().validate(conf);

        cfdpService.init(yamcsInstance, "CfdpService", conf);

        assertEquals(FaultHandlingAction.SUSPEND, cfdpService.getSenderFaultHandler(ConditionCode.ACK_LIMIT_REACHED));
        assertEquals(FaultHandlingAction.ABANDON, cfdpService.getReceiverFaultHandler(ConditionCode.ACK_LIMIT_REACHED));
    }

    @Test
    public void testInvalidFaultHandler1() throws Exception {
        String confs = "{"
                + "   localEntities: [ {name: local12, id: 12}], "
                + "   remoteEntities: [ {name: remote15, id: 15}], "
                + "   senderFaultHandlers: { BauLimitReached: SUSPEND},"
                + "   receiverFaultHandlers: { AckLimitReached: ABANDON}"
                + "}";
        verifyInvalidConfig(confs, "Unknown condition code BauLimitReached");
    }

    @Test
    public void testInvalidFaultHandler2() throws Exception {
        String confs = "{"
                + "   localEntities: [ {name: local12, id: 12}], "
                + "   remoteEntities: [ {name: remote15, id: 15}], "
                + "   senderFaultHandlers: { AckLimitReached: BUM},"
                + "   receiverFaultHandlers: { AckLimitReached: ABANDON}"
                + "}";
        verifyInvalidConfig(confs, "Unknown action BUM");
    }

    void verifyInvalidConfig(String confs, String expectedErr) throws ValidationException, InitException {
        YConfiguration conf = new YConfiguration("cfdp", new ByteArrayInputStream(confs.getBytes()), "test");
        CfdpService cfdpService = new CfdpService();
        conf = cfdpService.getSpec().validate(conf);

        ConfigurationException ce = null;
        try {
            cfdpService.init(yamcsInstance, "CfdpService", conf);
        } catch (ConfigurationException e) {
            ce = e;
        }

        assertNotNull(ce);
        assertTrue(ce.getMessage().contains(expectedErr));
    }
}
