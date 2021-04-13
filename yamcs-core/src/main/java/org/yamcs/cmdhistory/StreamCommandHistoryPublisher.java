package org.yamcs.cmdhistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Injects the command history updates in the command history stream
 * 
 * @author nm
 *
 */
public class StreamCommandHistoryPublisher implements CommandHistoryPublisher {
    static public final String REALTIME_CMDHIST_STREAM_NAME = "cmdhist_realtime";
    static public final String DUMP_CMDHIST_STREAM_NAME = "cmdhist_dump";

    Stream stream;
    final String instance;

    public StreamCommandHistoryPublisher(String archiveInstance) {
        this.instance = archiveInstance;
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(archiveInstance);
        stream = ydb.getStream(REALTIME_CMDHIST_STREAM_NAME);
    }

    @Override
    public void publish(CommandId cmdId, String key, String value) {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        td.addColumn(key, DataType.STRING);

        Tuple t = new Tuple(td, new Object[] {
                cmdId.getGenerationTime(),
                cmdId.getOrigin(),
                cmdId.getSequenceNumber(),
                cmdId.getCommandName(),
                value
        });
        stream.emitTuple(t);
    }

    @Override
    public void publish(CommandId cmdId, String key, long instant) {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        td.addColumn(key, DataType.TIMESTAMP);

        Tuple t = new Tuple(td, new Object[] {
                cmdId.getGenerationTime(),
                cmdId.getOrigin(),
                cmdId.getSequenceNumber(),
                cmdId.getCommandName(),
                instant
        });
        stream.emitTuple(t);
    }

    @Override
    public void publish(CommandId cmdId, String key, int value) {
        publish(cmdId, key, DataType.INT, value);
    }

    @Override
    public void publish(CommandId cmdId, String key, byte[] binary) {
        publish(cmdId, key, DataType.BINARY, binary);
    }

    public void publish(CommandId cmdId, String key, DataType dt, Object value) {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        td.addColumn(key, dt);

        Tuple t = new Tuple(td, new Object[] {
                cmdId.getGenerationTime(),
                cmdId.getOrigin(),
                cmdId.getSequenceNumber(),
                cmdId.getCommandName(),
                value
        });
        stream.emitTuple(t);
    }

    @Override
    public void publishAck(CommandId cmdId, String key, long time, AckStatus state,
            String message, ParameterValue resultPv) {
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        td.addColumn(key + SUFFIX_STATUS, DataType.STRING);
        td.addColumn(key + SUFFIX_TIME, DataType.TIMESTAMP);
        List<Object> vals = new ArrayList<>(Arrays.asList(cmdId.getGenerationTime(), cmdId.getOrigin(),
                cmdId.getSequenceNumber(), cmdId.getCommandName(), state.toString(),
                time));

        if (message != null) {
            td.addColumn(key + SUFFIX_MESSAGE, DataType.STRING);
            vals.add(message);
        }
        if (resultPv != null) {
            td.addColumn(key + SUFFIX_RETURN, DataType.PARAMETER_VALUE);
            vals.add(resultPv);
        }
        stream.emitTuple(new Tuple(td, vals));
    }

    @Override
    public void addCommand(PreparedCommand pc) {
        stream.emitTuple(pc.toTuple());
    }

    public String getInstance() {
        return instance;
    }

    public Stream getStream() {
        return stream;
    }

}
