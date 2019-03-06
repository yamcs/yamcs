package org.yamcs.cfdp;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CfdpDatabaseInstance {
    static Logger log = LoggerFactory.getLogger(CfdpDatabaseInstance.class.getName());

    Map<Long, CfdpTransfer> transfers = new HashMap<Long, CfdpTransfer>();

    private String instanceName;
    private CfdpHandler handler;

    CfdpDatabaseInstance(String instanceName) throws YarchException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instanceName);
        handler = new CfdpHandler(ydb.getStream("cfdp_in"), ydb.getStream("cfdp_out"));
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
                : this.transfers.values().stream().filter(transfer -> transfer.isOngoing())
                        .collect(Collectors.toList());
    }

    public Collection<CfdpTransfer> getCfdpTransfers(List<Long> transferIds) {
        return this.transfers.values().stream().filter(transfer -> transferIds.contains(transfer.getId()))
                .collect(Collectors.toList());
    }

    public long initiateUploadCfdpTransfer(byte[] data, String target, boolean overwrite, boolean createPath) {
        // TODO, the '2' in the line below should be a true destinationId
        PutRequest putRequest = new PutRequest(CfdpDatabase.mySourceId, 2, target, data);
        CfdpTransfer transfer = (CfdpTransfer) handler.processRequest(putRequest);
        transfers.put(transfer.getId(), transfer);
        return transfer.getId();

        // CfdpPacket fdp = new FileDataPacket(filedata, 0).init();
        // cfdpOut.emitTuple(fdp.toTuple(1001));
    }
}
