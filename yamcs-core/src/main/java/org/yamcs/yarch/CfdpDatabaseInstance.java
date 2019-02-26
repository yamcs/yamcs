package org.yamcs.yarch;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Stream cfdpIn = ydb.getStream("cfdp_in");
        Stream cfdpOut = ydb.getStream("cfdp_out");

        return 0;
    }
}
