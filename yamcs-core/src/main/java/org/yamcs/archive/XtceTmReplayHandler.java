package org.yamcs.archive;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.ProtoDataType;

/**
 * Provides replay of the telemetry recorded by XtceTmRecorder
 * 
 * @author nm
 *
 */
public class XtceTmReplayHandler implements ReplayHandler {
    Set<String> partitions;
    final Mdb mdb;
    static Logger log = LoggerFactory.getLogger(XtceTmReplayHandler.class);
    ReplayOptions request;

    public XtceTmReplayHandler(Mdb mdb) {
        this.mdb = mdb;
    }

    @Override
    public void setRequest(ReplayOptions newRequest) throws YamcsException {
        this.request = newRequest;
        if (newRequest.getPacketRequest().getNameFilterList().isEmpty()) {
            partitions = null; // retrieve all
            return;
        }
        partitions = new HashSet<>();
        addPartitions(newRequest.getPacketRequest().getNameFilterList());
    }

    private void addPartitions(List<NamedObjectId> pnois) throws YamcsException {
        for (NamedObjectId pnoi : pnois) {
            SequenceContainer sc = mdb.getSequenceContainer(pnoi);
            if (sc == null) {
                throw new YamcsException("Cannot find any sequence container for " + pnoi);
            }

            // go up in the XTCE hierarchy to find a container with the useAsArchivePartition flag set
            while (sc != null) {
                if (sc.useAsArchivePartition() || sc.getBaseContainer() == null) {
                    partitions.add(sc.getQualifiedName());
                    break;
                }
                sc = sc.getBaseContainer();
            }
        }
    }

    @Override
    public SqlBuilder getSelectCmd() {
        SqlBuilder sqlb = ReplayHandler.init(XtceTmRecorder.TABLE_NAME, ProtoDataType.TM_PACKET, request);

        if (partitions != null) {
            if (partitions.isEmpty()) {
                return null;
            }
            sqlb.whereColIn("pname", partitions);
        }

        if (request.getPacketRequest().getTmLinksCount() > 0) {
            sqlb.whereColIn("link", request.getPacketRequest().getTmLinksList());
        }

        return sqlb;
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
