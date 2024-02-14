package org.yamcs.tctm.pus.services.tm.thirteen;

import static org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.DownlinkS13Packet.S13_TM;

import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public class SubServiceSixteen implements PusSubService {
    String yamcsInstance;

    public SubServiceSixteen(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pkt = new PusTmCcsdsPacket(tmPacket.getPacket());

        byte[] dataField = pkt.getDataField();
        
        int largePacketTransactionId = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceThirteen.largePacketTransactionIdSize);
        int failureReason = (int) ByteArrayUtils.decodeCustomInteger(dataField, ServiceThirteen.largePacketTransactionIdSize, ServiceThirteen.failureReasonSize);

        TupleDefinition td = S13_TM.copy();
        ServiceThirteen.s13In.emitTuple(new Tuple(td, new Object[] {
            largePacketTransactionId,
            null,
            null,
            null,
            null,
            failureReason
        }));

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);

        return pPkts;
    }
}
