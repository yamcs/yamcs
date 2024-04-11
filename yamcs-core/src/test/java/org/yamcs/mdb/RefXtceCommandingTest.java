package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeQueued_KEY;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeReleased_KEY;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.TransmissionConstraints_KEY;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.AbstractProcessorService;
import org.yamcs.ErrorInCommand;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryProvider;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.MetaCommandProcessor.CommandBuildResult;
import org.yamcs.parameter.LocalParameterManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.security.User;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.MetaCommand;

/**
 * Tests command encoding with the ref-xtce.xml
 */
public class RefXtceCommandingTest {
    static Mdb mdb;
    static User user;

    CommandingManager commandingManager;
    MetaCommandProcessor metaCommandProcessor;
    Processor proc;
    MyCommandReleaser cmdReleaser;
    MyCmdHistPublisher cmdHistPublisher;
    MyCmdHistoryProvider cmdHistProvider;

    LocalParameterManager localParaMgr;

    @BeforeAll
    public static void beforeClass() throws Exception {
        YConfiguration.setupTest(null);
        mdb = MdbFactory.getInstance("refxtce");
        user = new User("test", null);
    }

    @BeforeEach
    public void before() throws Exception {
        cmdReleaser = new MyCommandReleaser();
        cmdHistPublisher = new MyCmdHistPublisher();
        cmdHistProvider = new MyCmdHistoryProvider();
        localParaMgr = new LocalParameterManager();

        proc = ProcessorFactory.create("refxtce", "test", cmdHistProvider, cmdHistPublisher, cmdReleaser, localParaMgr);
        commandingManager = proc.getCommandingManager();
        metaCommandProcessor = commandingManager.getMetaCommandProcessor();
        proc.start();
    }

    @AfterEach
    public void after() {
        proc.stopAsync();
    }

    @Test
    public void testAbsTimeArg() throws ErrorInCommand {
        // encode command
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command1");

        String tstring = "2020-01-01T00:00:00.123Z";
        long tlong = TimeEncoding.parse(tstring);

        Map<String, Object> args = new HashMap<>();
        args.put("t1", tstring);
        args.put("t2", tstring);
        CommandBuildResult cbr = metaCommandProcessor.buildCommand(mc, args);
        Value v1 = cbr.args.get(mc.getArgument("t1")).getEngValue();
        assertEquals(tlong, v1.getTimestampValue());

        Value v2 = cbr.args.get(mc.getArgument("t2")).getEngValue();
        assertEquals(tlong, v2.getTimestampValue());

        byte[] cmdb = cbr.getCmdPacket();
        assertEquals(8, cmdb.length);

        int gpsTime = ByteArrayUtils.decodeInt(cmdb, 0);
        assertEquals(TimeEncoding.toGpsTimeMillisec(tlong) / 1000, gpsTime);

        int unixTime = ByteArrayUtils.decodeInt(cmdb, 4);
        assertEquals(Instant.parse(tstring).toEpochMilli() / 1000, unixTime);

    }

    @Test
    public void testAggregateCmdArgIncompleteValue() {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command2");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "{m1: 0}");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testAggregateCmdArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command2");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "{m1: 42, m2: 23.4}");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(42, bb.getInt());
        assertEquals(23.4, bb.getDouble(), 1e-5);
    }

    @Test
    public void testAggregateCmdArgInitialValue() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command4");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "{m1: 42}");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(42, bb.getInt());
        assertEquals(3.14, bb.getDouble(), 1e-5);
    }

    @Test
    public void testAggregateCmdArgInitialValue2() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command4");
        Map<String, Object> args = new HashMap<>();
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(7, bb.getInt());
        assertEquals(3.14, bb.getDouble(), 1e-5);
    }

    @Test
    public void testAggregateCmdArgOutOfRange() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command2");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "{m1: 42, m2: 123.4}");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testBinaryArgCmd() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command3");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "010203AB");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(6, b.length);
        assertEquals(4, ByteBuffer.wrap(b).getShort());
        assertEquals("010203AB", StringConverter.arrayToHexString(b, 2, 4));
    }

    @Test
    public void testBinaryArgCmdTooLong() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command3");
        Map<String, Object> args = new HashMap<>();
        // max allowed length for arg1 is 10, the value below has 11 bytes, it will throw an exception
        args.put("arg1", "0102030405060708090A0B");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testBinaryArgCmdTooShort() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command3");
        Map<String, Object> args = new HashMap<>();
        // min allowed length for arg1 is 2, the value below has 1 byte, it will throw an exception
        args.put("arg1", "01");
        assertThrows(ErrorInCommand.class, () -> {
            metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        });
    }

    @Test
    public void testCmdWithArg() throws Exception {
        MetaCommand mc = mdb.getMetaCommand("/RefXtce/command_with_algo");
        Map<String, Object> args = new HashMap<>();

        args.put("arg1", "3.14");
        args.put("arg2", "150");
        byte[] b = metaCommandProcessor.buildCommand(mc, args).getCmdPacket();
        assertEquals(4, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(31, bb.getShort());
        assertEquals(0x3859, bb.getShort());
    }

    @Test
    public void testTransmissionConstraint1Fail() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_constraint1");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "PENDING",
                TransmissionConstraints_KEY, "NOK",
                AcknowledgeReleased_KEY, "NOK");
        assertNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testTransmissionConstraint1OK() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_constraint1");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(42));

        localParaMgr.sync();

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK");
        verifyCmdHist(TransmissionConstraints_KEY, "OK");
        verifyCmdHist(AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testTransmissionConstraint2OK() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_constraint2");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "15");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "OK",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testVerifier1Timeout() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier1");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "SCHEDULED",
                AcknowledgeReleased_KEY, "OK");

        assertNotNull(cmdReleaser.getCmd(2000));

        verifyCmdHist("Verifier_Complete", "PENDING",
                "Verifier_Complete", "TIMEOUT");
    }

    @Test
    public void testVerifier1OK() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier1");
        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "SCHEDULED",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));

        verifyCmdHist("Verifier_Complete", "PENDING");
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(47));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    @Test
    public void testVerifier2Timeout() throws Exception {
        // set first a value
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(30));

        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier2");

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        // update the value
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(31));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(32));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(33));

        verifyCmdHist("Verifier_Complete", "TIMEOUT");
    }

    @Test
    public void testVerifier2OK() throws Exception {
        // set first a value
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(30));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para2"), ValueUtility.getUint32Value(13));

        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier2");

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "3");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        // update the value
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(47));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(32));

        verifyCmdHist("Verifier_Complete", "OK");
        CmdHistEntry che = cmdHistPublisher.getCmdHist(3000);
        assertEquals("Verifier_Complete_Return", che.key);
        ParameterValue returnPv = (ParameterValue) che.value;
        assertEquals(13, returnPv.getEngValue().getUint32Value());
    }

    @Test
    public void testVerifier3OK() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier3");

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "101");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(101));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    @Test
    public void testVerifier4OK() throws Exception {
        MetaCommand cmd = mdb.getMetaCommand("/RefXtce/cmd_with_verifier4");

        Map<String, Object> args = new HashMap<>();
        args.put("arg1", "101");
        PreparedCommand pc = commandingManager.buildCommand(cmd, args, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionConstraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        localParaMgr.updateParameter(mdb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(101));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    private void verifyCmdHist(String... keyValue) throws InterruptedException {
        if (keyValue.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "An array with an even number of elements [ke1,value1, key2,value2...] is needed");
        }
        for (int i = 0; i < keyValue.length; i += 2) {
            String key = keyValue[i];
            String value = keyValue[i + 1];
            CmdHistEntry status = cmdHistPublisher.getCmdHist(3000);
            assertEquals(key + "_Status", status.key);
            assertEquals(value, status.value);

            CmdHistEntry time = cmdHistPublisher.getCmdHist(1000);
            assertEquals(key + "_Time", time.key);

        }

    }

    static class MyCommandReleaser extends AbstractProcessorService implements CommandReleaser {
        BlockingQueue<PreparedCommand> cmdList = new ArrayBlockingQueue<>(100);

        PreparedCommand getCmd(long timeout) throws InterruptedException {
            return cmdList.poll(timeout, TimeUnit.MILLISECONDS);
        }

        @Override
        public void init(Processor proc, YConfiguration config, Object spec) {
            super.init(proc, config, spec);
        }

        @Override
        public void releaseCommand(PreparedCommand preparedCommand) {
            cmdList.add(preparedCommand);
        }

        @Override
        public void setCommandHistory(CommandHistoryPublisher commandHistory) {
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

    static class CmdHistEntry {
        final CommandId cmdId;
        final String key;
        final Object value;

        CmdHistEntry(CommandId cmdId, String key, Object value) {
            this.cmdId = cmdId;
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "CmdHistEntry [cmdId=" + cmdId.getCommandName() + ", key=" + key + ", value=" + value + "]";
        }
    }

    public static class MyCmdHistPublisher extends AbstractProcessorService implements CommandHistoryPublisher {
        BlockingQueue<CmdHistEntry> entries = new ArrayBlockingQueue<>(100);

        CmdHistEntry getCmdHist(long timeout) throws InterruptedException {
            return entries.poll(timeout, TimeUnit.MILLISECONDS);
        }

        @Override
        public void publish(CommandId cmdId, String key, long value) {
            entries.add(new CmdHistEntry(cmdId, key, value));
        }

        @Override
        public void publish(CommandId cmdId, String key, String value) {
            if (Queue_KEY.equals(key)) {
                return;
            }
            entries.add(new CmdHistEntry(cmdId, key, value));
        }

        @Override
        public void publish(CommandId cmdId, String key, int value) {
            entries.add(new CmdHistEntry(cmdId, key, value));
        }

        @Override
        public void publish(CommandId cmdId, String key, byte[] value) {
            entries.add(new CmdHistEntry(cmdId, key, value));
        }

        @Override
        public void publish(CommandId cmdId, String key, ParameterValue value) {
            entries.add(new CmdHistEntry(cmdId, key, value));
        }

        @Override
        public void addCommand(PreparedCommand pc) {
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

    public static class MyCmdHistoryProvider extends AbstractProcessorService implements CommandHistoryProvider {

        @Override
        public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm) {
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
