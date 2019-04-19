package org.yamcs.cfdp;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

public class CfdpDatabaseInstance implements StreamSubscriber {
    static Logger log = LoggerFactory.getLogger(CfdpDatabaseInstance.class.getName());

    Map<CfdpTransactionId, CfdpOutgoingTransfer> transfers = new HashMap<CfdpTransactionId, CfdpOutgoingTransfer>();

    private String instanceName;

    private Stream cfdpIn, cfdpOut;

    CfdpDatabaseInstance(String instanceName) throws YarchException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instanceName);
        cfdpIn = ydb.getStream("cfdp_in");
        cfdpOut = ydb.getStream("cfdp_out");
        this.cfdpIn.addSubscriber(this);
    }

    public String getName() {
        return instanceName;
    }

    public String getYamcsInstance() {
        return instanceName;
    }

    public void addCfdpTransfer(CfdpOutgoingTransfer transfer) {
        transfers.put(transfer.getTransactionId(), transfer);
    }

    public CfdpOutgoingTransfer getCfdpTransfer(CfdpTransactionId transferId) {
        return transfers.get(transferId);
    }

    public Collection<CfdpOutgoingTransfer> getCfdpTransfers(boolean all) {
        return all
                ? this.transfers.values()
                : this.transfers.values().stream().filter(transfer -> transfer.isOngoing())
                        .collect(Collectors.toList());
    }

    public Collection<CfdpOutgoingTransfer> getCfdpTransfers(List<Long> transferIds) {
        List<CfdpTransactionId> transactionIds = transferIds.stream()
                .map(x -> new CfdpTransactionId(CfdpDatabase.mySourceId, x)).collect(Collectors.toList());
        return this.transfers.values().stream().filter(transfer -> transactionIds.contains(transfer.getId()))
                .collect(Collectors.toList());
    }

    public CfdpTransaction processRequest(CfdpRequest request) {
        switch (request.getType()) {
        case PUT:
            return processPutRequest((PutRequest) request);
        case PAUSE:
            return processPauseRequest((PauseRequest) request);
        case RESUME:
            return processResumeRequest((ResumeRequest) request);
        case CANCEL:
            return processCancelRequest((CancelRequest) request);
        default:
            return null;
        }
    }

    private CfdpOutgoingTransfer processPutRequest(PutRequest request) {
        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(request, this.cfdpOut);
        transfers.put(transfer.getTransactionId(), transfer);
        transfer.start();
        return transfer;
    }

    private CfdpOutgoingTransfer processPauseRequest(PauseRequest request) {
        CfdpOutgoingTransfer transfer = request.getTransfer();
        transfer.pause();
        return transfer;
    }

    private CfdpOutgoingTransfer processResumeRequest(ResumeRequest request) {
        CfdpOutgoingTransfer transfer = request.getTransfer();
        transfer.resumeTransfer();
        return transfer;
    }

    private CfdpOutgoingTransfer processCancelRequest(CancelRequest request) {
        CfdpOutgoingTransfer transfer = request.getTransfer();
        transfer.cancelTransfer();
        return transfer;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        CfdpPacket packet = CfdpPacket.fromTuple(tuple);
        CfdpTransactionId id = packet.getTransactionId();
        CfdpTransaction transaction = null;
        if (transfers.containsKey(id)) {
            transaction = transfers.get(id);
        } else {
            // the communication partner has initiated a transfer
            transaction = instantiateTransaction(packet);
        }

        if (transaction != null) {
            transaction.processPacket(packet);
        }
    }

    private CfdpTransaction instantiateTransaction(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()
                && ((FileDirective) packet).getFileDirectiveCode() == FileDirectiveCode.Metadata) {
            log.error("Only CFDP transactions that are initiated by YAMCS are supported.");
            throw new IllegalArgumentException("Only YAMCS-initiated CFDP transactions are supported");
        } else {
            log.error("Rogue CFDP packet received.");
            throw new IllegalArgumentException("Rogue CFDP packet received");
        }
    }

    @Override
    public void streamClosed(Stream stream) {}
}
