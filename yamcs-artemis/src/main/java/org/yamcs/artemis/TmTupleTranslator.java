package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import com.google.protobuf.ByteString;

/**
 * Translates between tuples as defined in {@link TmDataLinkInitialiser} and ActiveMQ messages containing TmPacketData
 * 
 * @author nm
 *
 */
public class TmTupleTranslator implements TupleTranslator {
    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {

        byte[] tmbody = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        long recTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        long genTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_GENTIME_COLUMN);
        int seqNum = (Integer) tuple.getColumn(StandardTupleDefinitions.TM_SEQNUM_COLUMN);
        TmPacketData tm = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(tmbody)).setReceptionTime(recTime)
                .setGenerationTime(genTime).setSequenceNumber(seqNum).build();
        Protocol.encode(msg, tm);
        return msg;
    }

    @Override
    public Tuple buildTuple(ClientMessage msg) {
        try {
            TupleDefinition tdef = StandardTupleDefinitions.TM;
            TmPacketData tm = (TmPacketData) Protocol.decode(msg, TmPacketData.newBuilder());
            return new Tuple(tdef, new Object[] { tm.getGenerationTime(), tm.getSequenceNumber(),
                    tm.getReceptionTime(), tm.getPacket().toByteArray() });
        } catch (YamcsApiException e) {
            throw new IllegalArgumentException(e.toString(), e);
        }
    }
}
