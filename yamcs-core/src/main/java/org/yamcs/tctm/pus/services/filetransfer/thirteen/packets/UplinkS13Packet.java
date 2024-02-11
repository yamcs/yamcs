package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;

public abstract class UplinkS13Packet extends FileTransferPacket {
    protected String fullyQualifiedCmdName;

    UplinkS13Packet(S13TransactionId transactionId, String fullyQualifiedCmdName) {
        super(transactionId);
        this.fullyQualifiedCmdName = fullyQualifiedCmdName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedCmdName;
    }

    public abstract PreparedCommand generatePreparedCommand(OngoingS13Transfer trans) throws BadRequestException, InternalServerErrorException;
}
