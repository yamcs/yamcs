package org.yamcs.xtceproc;

import static org.junit.Assert.*;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
import org.yamcs.parameter.LocalParameterManager;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.security.User;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.MetaCommandProcessor.CommandBuildResult;

/**
 * Tests command encoding with the ref-xtce.xml
 */
public class RefXtceCommandingTest {
    static XtceDb xtcedb;
    static User user;

    CommandingManager commandingManager;
    MetaCommandProcessor metaCommandProcessor;
    Processor proc;
    MyCommandReleaser cmdReleaser;
    MyCmdHistPublisher cmdHistPublisher;
    MyCmdHistoryProvider cmdHistProvider;

    LocalParameterManager localParaMgr;
    @BeforeClass
    public static void beforeClass() throws Exception {
        YConfiguration.setupTest(null);
        xtcedb = XtceDbFactory.getInstance("refxtce");
        user = new User("test", null);
    }

    @Before
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

    @After
    public void after() {
        proc.stopAsync();
    }

    @Test
    public void testAbsTimeArg() throws ErrorInCommand {
        // encode command
        MetaCommand mc = xtcedb.getMetaCommand("/RefXtce/command1");

        String tstring = "2020-01-01T00:00:00.123Z";
        long tlong = TimeEncoding.parse(tstring);
        
        List<ArgumentAssignment> aaList = Arrays.asList(new ArgumentAssignment("t1", tstring), new ArgumentAssignment("t2", tstring));
        CommandBuildResult cbr = metaCommandProcessor.buildCommand(mc, aaList);
        Value v1 = cbr.args.get(mc.getArgument("t1")).getEngValue();
        assertEquals(tlong, v1.getTimestampValue());
        
        Value v2 = cbr.args.get(mc.getArgument("t2")).getEngValue();
        assertEquals(tlong, v2.getTimestampValue());
        
        byte[] cmdb = cbr.getCmdPacket();
        assertEquals(8, cmdb.length);
        
        int gpsTime = ByteArrayUtils.decodeInt(cmdb, 0);
        assertEquals(TimeEncoding.toGpsTimeMillisec(tlong)/1000, gpsTime);
        
        int unixTime = ByteArrayUtils.decodeInt(cmdb, 4);
        assertEquals(Instant.parse(tstring).toEpochMilli()/1000, unixTime);
        
    }
    
    @Test(expected = org.yamcs.ErrorInCommand.class)
    public void testAggregateCmdArgIncompleteValue() throws Exception{
        MetaCommand mc = xtcedb.getMetaCommand("/RefXtce/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("arg1", "{m1: 0}");
        arguments.add(argumentAssignment1);
        metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
    }
    
    @Test
    public void testAggregateCmdArg() throws Exception{
        MetaCommand mc = xtcedb.getMetaCommand("/RefXtce/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("arg1", "{m1: 42, m2: 23.4}");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals(12, b.length);
        ByteBuffer bb = ByteBuffer.wrap(b);
        assertEquals(42, bb.getInt());
        assertEquals(23.4, bb.getDouble(), 1e-5);
    }

    @Test(expected = org.yamcs.ErrorInCommand.class)
    public void testAggregateCmdArgOutOfRange() throws Exception{
        MetaCommand mc = xtcedb.getMetaCommand("/RefXtce/command2");
        List<ArgumentAssignment> arguments = new LinkedList<>();
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("arg1", "{m1: 42, m2: 123.4}");
        arguments.add(argumentAssignment1);
        metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
    }
    
    @Test
    public void testBinaryArgCmd() throws Exception {
        MetaCommand mc = xtcedb.getMetaCommand("/RefXtce/command3");
        List<ArgumentAssignment> arguments = new LinkedList<>();
        ArgumentAssignment argumentAssignment1 = new ArgumentAssignment("arg1", "010203AB");
        arguments.add(argumentAssignment1);
        byte[] b = metaCommandProcessor.buildCommand(mc, arguments).getCmdPacket();
        assertEquals(6, b.length);
        assertEquals(4, ByteBuffer.wrap(b).getShort());
        assertEquals("010203AB", StringConverter.arrayToHexString(b, 2, 4));
    }

    @Test
    public void testTransmissionConstraint1Fail() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_constraint1");
        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "PENDING",
                TransmissionContraints_KEY, "NOK",
                AcknowledgeReleased_KEY, "NOK");
        assertNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testTransmissionConstraint1OK() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_constraint1");
        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(42));

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK");
        verifyCmdHist(TransmissionContraints_KEY, "OK");
        verifyCmdHist(AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testTransmissionConstraint2OK() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_constraint2");
        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "15"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "OK",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
    }

    @Test
    public void testVerifier1Timeout() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier1");
        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        // localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(42));

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "SCHEDULED",
                AcknowledgeReleased_KEY, "OK");

        assertNotNull(cmdReleaser.getCmd(2000));

        verifyCmdHist("Verifier_Complete", "PENDING",
                "Verifier_Complete", "TIMEOUT");
    }

    @Test
    public void testVerifier1OK() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier1");
        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "SCHEDULED",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));

        verifyCmdHist("Verifier_Complete", "PENDING");
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(47));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    @Test
    public void testVerifier2Timeout() throws Exception {
        // set first a value
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(30));

        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier2");

        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        // update the value
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(31));
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(32));
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(33));

        verifyCmdHist("Verifier_Complete", "TIMEOUT");
    }

    @Test
    public void testVerifier2OK() throws Exception {
        // set first a value
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(30));

        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier2");

        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "3"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);

        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        // update the value
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(47));
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(32));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    @Test
    public void testVerifier3OK() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier3");

        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "101"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(101));

        verifyCmdHist("Verifier_Complete", "OK");
    }

    @Test
    public void testVerifier4OK() throws Exception {
        MetaCommand cmd = xtcedb.getMetaCommand("/RefXtce/cmd_with_verifier4");

        List<ArgumentAssignment> argList = Arrays.asList(new ArgumentAssignment("arg1", "101"));
        PreparedCommand pc = commandingManager.buildCommand(cmd, argList, "localhost", 1, user);
        commandingManager.sendCommand(user, pc);

        verifyCmdHist(AcknowledgeQueued_KEY, "OK",
                TransmissionContraints_KEY, "NA",
                "Verifier_Complete", "PENDING",
                AcknowledgeReleased_KEY, "OK");
        assertNotNull(cmdReleaser.getCmd(2000));
        localParaMgr.updateParameter(xtcedb.getParameter("/RefXtce/local_para1"), ValueUtility.getUint32Value(101));

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
