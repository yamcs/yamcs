package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;

public abstract class UplinkS13Packet extends FileTransferPacket {
    protected String fullyQualifiedCmdName;
    protected boolean skipAcknowledgement;

    UplinkS13Packet(S13TransactionId transactionId, String fullyQualifiedCmdName, boolean skipAcknowledgement) {
        super(transactionId.getUniquenessId());
        this.fullyQualifiedCmdName = fullyQualifiedCmdName;
        this.skipAcknowledgement = skipAcknowledgement;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedCmdName;
    }

    public boolean getSkipAcknowledgement() {
        return skipAcknowledgement;
    }

    public abstract PreparedCommand generatePreparedCommand(OngoingS13Transfer trans) throws BadRequestException, InternalServerErrorException;
}
