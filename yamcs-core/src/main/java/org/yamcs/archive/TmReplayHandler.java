package org.yamcs.archive;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

public class TmReplayHandler implements ReplayHandler {
    Set<String> partitions=new HashSet<String>();
    final XtceUtil xtceutil;
    static Logger log=LoggerFactory.getLogger(TmReplayHandler.class.getName());
    ReplayRequest request;


    public TmReplayHandler(XtceUtil xtceutil) {
        this.xtceutil=xtceutil;
    }


    @Override
    public void setRequest(ReplayRequest newRequest) throws YamcsException{
        this.request=newRequest;
        partitions.clear();
        // TODO OLD API, delete this for once deprecated API no longer in use
        for(NamedObjectId pnoi:newRequest.getTmPacketFilterList()) {
            addPartition(pnoi);
        }
        for(NamedObjectId pnoi:newRequest.getPacketRequest().getNameFilterList()) {
            addPartition(pnoi);
        }
    }
    
    private void addPartition(NamedObjectId pnoi) throws YamcsException {
        Integer packetId;
        if(pnoi.hasNamespace()) {
            packetId=xtceutil.getPacketId(pnoi.getName(),pnoi.getNamespace());
        } else {
            packetId=xtceutil.getPacketId(pnoi.getName());
        }
        if(packetId==null) {
            log.warn("cannot find packetid for "+pnoi);
            throw new YamcsException("cannot find packetid for "+pnoi);
        }
        String part=Integer.toHexString(packetId);
        partitions.add(part);        
    }

    @Override
    public String getSelectCmd() {
        if(partitions.isEmpty())return null;
        StringBuilder sb=new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.TM_PACKET.getNumber()).
           append(",* from  tm where part in (");
        boolean first=true;
        for(String pn:partitions) {
            if(first) first=false;
            else sb.append(", ");
            sb.append("'").append(pn).append("'");
        }
        sb.append(")");
        appendTimeClause(sb, request);
        return sb.toString();
    }


    @Override
    public MessageLite transform(Tuple t) {
        long recTime=(Long)t.getColumn("rectime");
        byte[]pbody=(byte[]) t.getColumn("packet");
        TmPacketData tm=TmPacketData.newBuilder().setReceptionTime(recTime).
        setPacket(ByteString.copyFrom(pbody)).build();
        return tm;
    }


    static void appendTimeClause(StringBuilder sb, ReplayRequest request) {
        if(request.hasStart() || (request.hasStop())) {
            if(request.hasStart()) {
                sb.append(" and gentime>="+request.getStart());
                if(request.hasStop()) sb.append(" and gentime<"+request.getStop());
            } else {
                sb.append(" and gentime<"+request.getStop());
            }
        }
    }


    @Override
    public void reset() {
        
    }
}
