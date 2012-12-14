package org.yamcs.archive;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;

/**
 * Provides replay of the telemetry recorded by XtceTmRecorder
 * @author nm
 *
 */
public class XtceTmReplayHandler implements ReplayHandler {
    Set<String> partitions=new HashSet<String>();
    final XtceDb xtcedb;
    static Logger log=LoggerFactory.getLogger(XtceTmReplayHandler.class.getName());
    ReplayRequest request;


    public XtceTmReplayHandler(XtceDb xtcedb) {
        this.xtcedb=xtcedb;
    }

    @Override
    public void setRequest(ReplayRequest newRequest) throws YamcsException {
        this.request=newRequest;
        partitions.clear();
        SequenceContainer rootSc=xtcedb.getRootSequenceContainer();
        for(NamedObjectId pnoi:newRequest.getTmPacketFilterList()) {
            SequenceContainer sc=xtcedb.getSequenceContainer(pnoi);
            if(sc==null) throw new YamcsException("Cannot find any sequence container for "+pnoi);
            if(sc==rootSc) { //retrieve all
                partitions=null;
                break;
            }
            //go up in the XTCE hierarchy to find a container on the level one (i.e. is child of the root)
            while(sc!=null) {
                if(sc.getBaseContainer()==rootSc) {
                    partitions.add(sc.getQualifiedName());
                    break;
                } else {
                    sc=sc.getBaseContainer();
                }
            }
        }
    }

    @Override
    public String getSelectCmd() {
        StringBuilder sb=new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.TM_PACKET.getNumber()).
        append(",* from tm where"); 
        if(partitions!=null) {
            if(partitions.isEmpty())return null;
            sb.append(" pname in (");
            boolean first=true;
            for(String pn:partitions) {
                if(first) first=false;
                else sb.append(", ");
                sb.append("'").append(pn).append("'");
            }
            sb.append(")");    
        } else {
            sb.append(" true");
        }
        
        
        appendTimeClause(sb, request);
        return sb.toString();
    }


    @Override
    public MessageLite transform(Tuple t) {
        long recTime=(Long)t.getColumn(TmProviderAdapter.RECTIME_COLUMN);
        byte[]pbody=(byte[]) t.getColumn(TmProviderAdapter.PACKET_COLUMN);
        long genTime = (Long)t.getColumn(TmProviderAdapter.GENTIME_COLUMN);
        int seqNum = (Integer)t.getColumn(TmProviderAdapter.SEQNUM_COLUMN);
        TmPacketData tm=TmPacketData.newBuilder().setReceptionTime(recTime)
            .setPacket(ByteString.copyFrom(pbody)).setGenerationTime(genTime)
            .setSequenceNumber(seqNum).build();
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
        // TODO Auto-generated method stub
        
    }
}
