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

    Map<CfdpTransactionId, CfdpTransfer> transfers = new HashMap<CfdpTransactionId, CfdpTransfer>();

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

    public void addCfdpTransfer(CfdpTransfer transfer) {
        transfers.put(transfer.getTransactionId(), transfer);
    }

    public CfdpTransfer getCfdpTransfer(CfdpTransactionId transferId) {
        return transfers.get(transferId);
    }

    public Collection<CfdpTransfer> getCfdpTransfers(boolean all) {
        return all
                ? this.transfers.values()
                : this.transfers.values().stream().filter(transfer -> transfer.isOngoing())
                        .collect(Collectors.toList());
    }

    public Collection<CfdpTransfer> getCfdpTransfers(List<Long> transferIds) {
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

    private CfdpTransfer processPutRequest(PutRequest request) {
        CfdpTransfer transfer = new CfdpTransfer(request, this.cfdpOut);
        transfers.put(transfer.getTransactionId(), transfer);
        transfer.start();
        return transfer;
    }

    private CfdpTransfer processPauseRequest(PauseRequest request) {
        CfdpTransfer transfer = request.getTransfer();
        transfer.pause();
        return transfer;
    }

    private CfdpTransfer processResumeRequest(ResumeRequest request) {
        CfdpTransfer transfer = request.getTransfer();
        transfer.resumeTransfer();
        return transfer;
    }

    private CfdpTransfer processCancelRequest(CancelRequest request) {
        CfdpTransfer transfer = request.getTransfer();
        transfer.cancelTransfer();
        return transfer;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        CfdpPacket packet = CfdpPacket.fromTuple(tuple);
        // log.error(packet.toString());

        CfdpTransactionId id = packet.getTransactionId();

        CfdpTransaction transaction = null;
        if (transfers.containsKey(id)) {
            transaction = transfers.get(id);
        } else {
            transaction = instantiateTransaction(packet);
        }

        if (transaction != null) {
            transaction.processPacket(packet);
        }
    }

    private CfdpTransaction instantiateTransaction(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()
                && ((FileDirective) packet).getFileDirectiveCode() == FileDirectiveCode.Metadata) {
            // TODO, probably an exception is better
            log.error("Only CFDP transactions that are initiated by YAMCS are supported.");
            return null;
        } else {
            // TODO, probably an exception is better
            log.error("Rogue CFDP packet received.");
            return null;
        }
    }

    @Override
    public void streamClosed(Stream stream) {
        // TODO Auto-generated method stub

    }
}
