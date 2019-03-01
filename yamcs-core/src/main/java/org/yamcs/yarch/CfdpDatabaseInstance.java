package org.yamcs.yarch;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;

public class CfdpDatabaseInstance {
    static Logger log = LoggerFactory.getLogger(CfdpDatabaseInstance.class.getName());

    Map<Long, CfdpTransfer> transfers = new HashMap<Long, CfdpTransfer>();

    private String instanceName;

    CfdpDatabaseInstance(String instanceName) throws YarchException {
        this.instanceName = instanceName;
    }

    public String getName() {
        return instanceName;
    }

    public String getYamcsInstance() {
        return instanceName;
    }

    public void addCfdpTransfer(CfdpTransfer transfer) {
        transfers.put(transfer.getId(), transfer);
    }

    public CfdpTransfer getCfdpTransfer(long transferId) {
        return transfers.get(transferId);
    }

    public Collection<CfdpTransfer> getCfdpTransfers(boolean all) {
        return all
                ? this.transfers.values()
                : this.transfers.values().stream().filter(transfer -> transfer.getState().isOngoing())
                        .collect(Collectors.toList());
    }

    public Collection<CfdpTransfer> getCfdpTransfers(List<Long> transferIds) {
        return this.transfers.values().stream().filter(transfer -> transferIds.contains(transfer.getId()))
                .collect(Collectors.toList());
    }

    public long initiateUploadCfdpTransfer(byte[] data, String target, boolean overwrite, boolean createPath) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instanceName);
        Stream cfdpOut = ydb.getStream("cfdp_out");

        byte[] filedata = { 'T', 'h', 'i', 's', ' ', 'i', 's', ' ', 'a', ' ', 'c', 'f', 'd', 'p', ' ', 't', 'e', 's',
                't', '.' };
        CfdpPacket fdp = new FileDataPacket(filedata, 0).init();
        cfdpOut.emitTuple(fdp.toTuple(1001));

        Stream cfdpIn = ydb.getStream("cfdp_in");
        cfdpIn.addSubscriber(new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                // Log.info("got a fancy CFDP packet");
                int i = 4;
                i = i++;
            }

            @Override
            public void streamClosed(Stream stream) {
                // TODO Auto-generated method stub

            }

        });

        return 0;
    }
}
