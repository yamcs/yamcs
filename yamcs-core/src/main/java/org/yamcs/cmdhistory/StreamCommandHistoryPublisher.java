package org.yamcs.cmdhistory;

import org.yamcs.tctm.TcDataLinkInitialiser;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.protobuf.Commanding.CommandId;

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
        TupleDefinition td = TcDataLinkInitialiser.TC_TUPLE_DEFINITION.copy();
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
        TupleDefinition td = TcDataLinkInitialiser.TC_TUPLE_DEFINITION.copy();
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

    public void publish(CommandId cmdId, String key, DataType dt, Object value) {
        TupleDefinition td = TcDataLinkInitialiser.TC_TUPLE_DEFINITION.copy();
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
    public void publishWithTime(CommandId cmdId, String key, long time, String value) {
        TupleDefinition td = TcDataLinkInitialiser.TC_TUPLE_DEFINITION.copy();
        td.addColumn(key+"_Status", DataType.STRING);
        td.addColumn(key+"_Time", DataType.TIMESTAMP);

        Tuple t = new Tuple(td, new Object[] {
                cmdId.getGenerationTime(),
                cmdId.getOrigin(),
                cmdId.getSequenceNumber(),
                cmdId.getCommandName(),
                value,
                time
        });
        stream.emitTuple(t);
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
