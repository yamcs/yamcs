package org.yamcs.artemis;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.api.artemis.ArtemisApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import com.google.protobuf.ByteString;

/**
 * Translates between tuples as defined in {@link org.yamcs.tctm.DataLinkInitialiser} and ActiveMQ messages containing
 * TmPacketData
 * 
 * @author nm
 *
 */
public class TmTupleTranslator implements TupleTranslator {
    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {

        byte[] tmbody = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        long recTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        long genTime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        int seqNum = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
        TmPacketData tm = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(tmbody))
                .setReceptionTime(TimeEncoding.toProtobufTimestamp(recTime))
                .setGenerationTime(TimeEncoding.toProtobufTimestamp(genTime)).setSequenceNumber(seqNum).build();
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
        } catch (ArtemisApiException e) {
            throw new IllegalArgumentException(e.toString(), e);
        }
    }
}
