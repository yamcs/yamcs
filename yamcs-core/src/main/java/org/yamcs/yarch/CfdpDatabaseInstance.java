package org.yamcs.yarch;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CfdpDatabaseInstance {
    static Logger log = LoggerFactory.getLogger(CfdpDatabaseInstance.class.getName());

    Map<Long, CfdpTransfer> transfers;

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

    // TODO, add more, obviously
}
