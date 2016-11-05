package org.yamcs.archive;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.tctm.TmDataLinkInitialiser;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;

/**
 * Maps archived tuples to GPB
 */
public final class GPBHelper {

    public static TmPacketData tupleToTmPacketData(Tuple tuple) {
        long recTime=(Long)tuple.getColumn(TmDataLinkInitialiser.RECTIME_COLUMN);
        byte[]pbody=(byte[]) tuple.getColumn(TmDataLinkInitialiser.PACKET_COLUMN);
        long genTime = (Long)tuple.getColumn(TmDataLinkInitialiser.GENTIME_COLUMN);
        int seqNum = (Integer)tuple.getColumn(TmDataLinkInitialiser.SEQNUM_COLUMN);
        String pname = (String)tuple.getColumn(XtceTmRecorder.PNAME_COLUMN);
        TmPacketData tm=TmPacketData.newBuilder().setReceptionTime(recTime)
            .setPacket(ByteString.copyFrom(pbody)).setGenerationTime(genTime)
            .setSequenceNumber(seqNum)
            .setId(NamedObjectId.newBuilder().setName(pname).build())
            .build();
        return tm;
    }
    
    public static CommandHistoryEntry tupleToCommandHistoryEntry(Tuple tuple) {
        CommandHistoryEntry.Builder che=CommandHistoryEntry.newBuilder();
        che.setCommandId(PreparedCommand.getCommandId(tuple));
        
        for(int i=1;i<tuple.size(); i++) { //first column is constant ProtoDataType.CMD_HISTORY.getNumber()
            ColumnDefinition cd=tuple.getColumnDefinition(i);
            String name=cd.getName();
            if(PreparedCommand.CNAME_GENTIME.equals(name)
                ||PreparedCommand.CNAME_ORIGIN.equals(name)
                ||PreparedCommand.CNAME_SEQNUM.equals(name)
                ||PreparedCommand.CNAME_CMDNAME.equals(name)) continue;
            che.addAttr(CommandHistoryAttribute.newBuilder()
                    .setName(name)
                    .setValue(ValueUtility.toGbp(ValueUtility.getColumnValue(cd, tuple.getColumn(i))))
                    .build());
        }
        return che.build();
    }
}
