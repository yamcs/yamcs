package org.yamcs.cmdhistory;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.Assignment;
import org.yamcs.cmdhistory.protobuf.Cmdhistory.AssignmentInfo;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandAssignment;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public class Util {

    /**
     * Transforms a tuple as received on the command history stream to protobuf
     * 
     */
    public static CommandHistoryEntry transform(Tuple t) {
        CommandHistoryEntry.Builder che = CommandHistoryEntry.newBuilder();
        che.setCommandId(PreparedCommand.getCommandId(t));

        for (int i = 1; i < t.size(); i++) { // first column is constant
            // ProtoDataType.CMD_HISTORY.getNumber()
            ColumnDefinition cd = t.getColumnDefinition(i);
            String name = cd.getName();
            if (PreparedCommand.CNAME_GENTIME.equals(name)
                    || PreparedCommand.CNAME_ORIGIN.equals(name)
                    || PreparedCommand.CNAME_SEQNUM.equals(name)
                    || PreparedCommand.CNAME_CMDNAME.equals(name)) {
                continue;
            }

            if (PreparedCommand.CNAME_ASSIGNMENTS.equals(name)) {
                AssignmentInfo assignmentInfo = (AssignmentInfo) t.getColumn(i);
                for (Assignment assignment : assignmentInfo.getAssignmentList()) {
                    CommandAssignment.Builder cheAssignment = CommandAssignment.newBuilder()
                            .setName(assignment.getName())
                            .setValue(assignment.getValue());
                    if (assignment.hasUserInput()) {
                        cheAssignment.setUserInput(assignment.getUserInput());
                    }
                    che.addAssignment(cheAssignment.build());
                }
            } else {
                che.addAttr(CommandHistoryAttribute.newBuilder()
                        .setName(name)
                        .setValue(ValueUtility.toGbp(ValueUtility.getColumnValue(cd, t.getColumn(i))))
                        .build());
            }
        }
        return che.build();
    }

    /**
     * Transforms protobuf messages of type CommandHistoryEntry to tuples.
     */
    public static Tuple transform(CommandHistoryEntry che) {
        if (!che.hasCommandId()) {
            throw new IllegalArgumentException("Cannot transform command history entry without command id");
        }
        CommandId id = che.getCommandId();
        TupleDefinition td = StandardTupleDefinitions.TC.copy();
        ArrayList<Object> al = new ArrayList<>();
        al.add(id.getGenerationTime());
        al.add(id.getOrigin());
        al.add(id.getSequenceNumber());
        al.add(id.getCommandName());

        for (CommandHistoryAttribute cha : che.getAttrList()) {
            td.addColumn(cha.getName(), ValueUtility.getYarchType(cha.getValue().getType()));
            al.add(ValueUtility.getYarchValue(cha.getValue()));
        }

        AssignmentInfo.Builder infob = AssignmentInfo.newBuilder();
        for (CommandAssignment cheAssignment : che.getAssignmentList()) {
            Assignment.Builder assignment = Assignment.newBuilder()
                    .setName(cheAssignment.getName())
                    .setValue(cheAssignment.getValue());
            if (cheAssignment.hasUserInput()) {
                assignment.setUserInput(cheAssignment.getUserInput());
            }
            infob.addAssignment(assignment);
        }
        td.addColumn(PreparedCommand.CNAME_ASSIGNMENTS,
                DataType.protobuf("org.yamcs.cmdhistory.protobuf.Cmdhistory$AssignmentInfo"));
        al.add(infob.build());

        return new Tuple(td, al);
    }
}
