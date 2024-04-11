package org.yamcs.archive;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.Assignment;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.mdb.Mdb;
import org.yamcs.protobuf.Commanding.CommandAssignment;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;

/**
 * Maps archived tuples to GPB
 */
public final class GPBHelper {

    public static TmPacketData tupleToTmPacketData(Tuple tuple) {
        long recTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        byte[] pbody = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        long genTime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        int seqNum = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
        String pname = (String) tuple.getColumn(XtceTmRecorder.PNAME_COLUMN);
        var b = TmPacketData.newBuilder()
                .setReceptionTime(TimeEncoding.toProtobufTimestamp(recTime))
                .setPacket(ByteString.copyFrom(pbody))
                .setSize(pbody.length)
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(genTime))
                .setSequenceNumber(seqNum)
                .setId(NamedObjectId.newBuilder().setName(pname).build());
        if (tuple.hasColumn(StandardTupleDefinitions.TM_ERTIME_COLUMN)) {
            long erTime = ((Instant) tuple.getColumn(StandardTupleDefinitions.TM_ERTIME_COLUMN)).getMillis();
            b.setEarthReceptionTime(TimeEncoding.toProtobufTimestamp(erTime));
        }
        if (tuple.hasColumn(StandardTupleDefinitions.TM_LINK_COLUMN)) {
            b.setLink(tuple.getColumn(StandardTupleDefinitions.TM_LINK_COLUMN));
        }
        return b.build();
    }

    public static CommandHistoryEntry tupleToCommandHistoryEntry(Tuple tuple, Mdb mdb) {
        long gentime = (Long) tuple.getColumn(PreparedCommand.CNAME_GENTIME);
        String origin = (String) tuple.getColumn(PreparedCommand.CNAME_ORIGIN);
        int sequenceNumber = (Integer) tuple.getColumn(PreparedCommand.CNAME_SEQNUM);
        String id = gentime + "-" + origin + "-" + sequenceNumber;
        var commandName = (String) tuple.getColumn(PreparedCommand.CNAME_CMDNAME);

        CommandHistoryEntry.Builder che = CommandHistoryEntry.newBuilder()
                .setId(id)
                .setOrigin(origin)
                .setSequenceNumber(sequenceNumber)
                .setCommandName(commandName)
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(gentime))
                .setCommandId(PreparedCommand.getCommandId(tuple));

        // Best effort, not a problem if the command no longer exists
        var command = mdb.getMetaCommand(commandName);
        if (command != null && command.getAliasSet() != null) {
            var aliasSet = command.getAliasSet();
            che.putAllAliases(aliasSet.getAliases());
        }

        for (int i = 1; i < tuple.size(); i++) { // first column is constant ProtoDataType.CMD_HISTORY.getNumber()
            ColumnDefinition cd = tuple.getColumnDefinition(i);
            String name = cd.getName();
            if (PreparedCommand.CNAME_GENTIME.equals(name)
                    || PreparedCommand.CNAME_ORIGIN.equals(name)
                    || PreparedCommand.CNAME_SEQNUM.equals(name)
                    || PreparedCommand.CNAME_CMDNAME.equals(name)) {
                continue;
            } else if (PreparedCommand.CNAME_ASSIGNMENTS.equals(name)) {
                Object assignmentProto = tuple.getColumn(i);
                if (assignmentProto != null) {
                    AssignmentInfo assignmentInfo = (AssignmentInfo) assignmentProto;
                    for (Assignment assignment : assignmentInfo.getAssignmentList()) {
                        CommandAssignment commandAssignment = CommandAssignment.newBuilder()
                                .setName(assignment.getName())
                                .setValue(assignment.getValue())
                                .setUserInput(assignment.hasUserInput() && assignment.getUserInput())
                                .build();
                        che.addAssignments(commandAssignment);
                    }
                }
            } else {
                che.addAttr(CommandHistoryAttribute.newBuilder()
                        .setName(name)
                        .setValue(ValueUtility.toGbp(ValueUtility.getColumnValue(cd, tuple.getColumn(i))))
                        .build());
            }
        }
        return che.build();
    }
}
