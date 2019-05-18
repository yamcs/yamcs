package org.yamcs.archive;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.yarch.Tuple;

/**
 * Provides replay of the telemetry recorded by XtceTmRecorder
 * 
 * @author nm
 *
 */
public class XtceTmReplayHandler implements ReplayHandler {
    Set<String> partitions;
    final XtceDb xtcedb;
    static Logger log = LoggerFactory.getLogger(XtceTmReplayHandler.class);
    ReplayRequest request;

    public XtceTmReplayHandler(XtceDb xtcedb) {
        this.xtcedb = xtcedb;
    }

    @Override
    public void setRequest(ReplayRequest newRequest) throws YamcsException {
        this.request = newRequest;
        if (newRequest.getPacketRequest().getNameFilterList().isEmpty()) {
            partitions = null; // retrieve all
            return;
        }
        partitions = new HashSet<>();
        SequenceContainer rootSc = xtcedb.getRootSequenceContainer();
        addPartitions(newRequest.getPacketRequest().getNameFilterList(), rootSc);
    }

    private void addPartitions(List<NamedObjectId> pnois, SequenceContainer rootSc) throws YamcsException {
        for (NamedObjectId pnoi : pnois) {
            SequenceContainer sc = xtcedb.getSequenceContainer(pnoi);
            if (sc == null) {
                throw new YamcsException("Cannot find any sequence container for " + pnoi);
            }
            if (sc == rootSc) { // retrieve all
                partitions = null;
                break;
            }
            // go up in the XTCE hierarchy to find a container on the level one
            // (i.e. is child of the root)
            while (sc != null) {
                if (sc.getBaseContainer() == rootSc) {
                    partitions.add(sc.getQualifiedName());
                    break;
                } else {
                    sc = sc.getBaseContainer();
                }
            }
        }
    }

    @Override
    public String getSelectCmd() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(ProtoDataType.TM_PACKET.getNumber()).append(",* from tm ");
        if (partitions != null) {
            if (partitions.isEmpty()) {
                return null;
            }
            sb.append("WHERE pname IN (");
            boolean first = true;
            for (String pn : partitions) {
                if (first) {
                    first = false;
                } else
                    sb.append(", ");
                sb.append("'").append(pn).append("'");
            }
            sb.append(")");
            appendTimeClause(sb, request, false);
        } else {
            if (request.hasStart() || (request.hasStop())) {
                sb.append("WHERE ");
                appendTimeClause(sb, request, true);
            }
        }
        if (request.hasReverse() && request.getReverse()) {
            sb.append(" ORDER DESC");
        }
        return sb.toString();
    }

    @Override
    public ReplayPacket transform(Tuple tuple) {
        long recTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
        byte[] pbody = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        long genTime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
        int seqNum = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
        String pname = (String) tuple.getColumn(XtceTmRecorder.PNAME_COLUMN);
        return new ReplayPacket(pname, recTime, genTime, seqNum, pbody);
    }

    static void appendTimeClause(StringBuilder sb, ReplayRequest request, boolean firstRestriction) {
        if (request.hasStart() || (request.hasStop())) {
            if (!firstRestriction) {
                sb.append(" and ");
            }
            if (request.hasStart()) {
                sb.append("gentime>=" + request.getStart());
                if (request.hasStop()) {
                    sb.append(" and gentime<" + request.getStop());
                }
            } else {
                sb.append("gentime<" + request.getStop());
            }
        }
    }

    @Override
    public void reset() {
    }

    public static class ReplayPacket {
        final String pname;
        final long recTime;
        final long genTime;
        final int seqNum;
        final byte[] packet;

        public ReplayPacket(String pname, long recTime, long genTime, int seqNum, byte[] packet) {
            this.pname = pname;
            this.recTime = recTime;
            this.genTime = genTime;
            this.seqNum = seqNum;
            this.packet = packet;
        }

        public long getGenerationTime() {
            return genTime;
        }

        public long getReceptionTime() {
            return recTime;
        }

        public int getSequenceNumber() {
            return seqNum;
        }

        public byte[] getPacket() {
            return packet;
        }
        /**
         * 
         * @return the name used when recording the packet
         */
        public String getQualifiedName() {
            return pname;
        }
    }
}
