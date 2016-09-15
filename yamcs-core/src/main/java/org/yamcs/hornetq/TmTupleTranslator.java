package org.yamcs.hornetq;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import com.google.protobuf.ByteString;

import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.tctm.TmProviderAdapter;

/**
 * Translates between tuples as defined in {@link TmProviderAdapter} and ActiveMQ messages containing TmPacketData
 * @author nm
 *
 */
public class TmTupleTranslator implements TupleTranslator {
    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {
        
        byte[] tmbody=(byte[])tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
        long recTime=(Long)tuple.getColumn(TmProviderAdapter.RECTIME_COLUMN);
        long genTime=(Long)tuple.getColumn(TmProviderAdapter.GENTIME_COLUMN);
        int seqNum = (Integer)tuple.getColumn(TmProviderAdapter.SEQNUM_COLUMN);
        TmPacketData tm=TmPacketData.newBuilder().setPacket(ByteString.copyFrom(tmbody))
        .setReceptionTime(recTime).setGenerationTime(genTime).setSequenceNumber(seqNum).build();
        Protocol.encode(msg, tm);
        return msg;

    }

    @Override
    public Tuple buildTuple(TupleDefinition tdef, ClientMessage msg) {
        try {
            TmPacketData tm=(TmPacketData)Protocol.decode(msg, TmPacketData.newBuilder());
            Tuple t=new Tuple(tdef, new Object[]{tm.getGenerationTime(), tm.getSequenceNumber(), tm.getReceptionTime(), tm.getPacket().toByteArray() });
            return t;
        } catch (YamcsApiException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

}
