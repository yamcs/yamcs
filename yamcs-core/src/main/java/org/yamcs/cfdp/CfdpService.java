package org.yamcs.cfdp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.TransferState;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Implements CCSDS File Delivery Protocol (CFDP) in Yamcs.
 * 
 * 
 * @author nm
 *
 */
public class CfdpService extends AbstractYamcsService implements StreamSubscriber, TransferMonitor {

    static final String ETYPE_UNEXPECTED_CFDP_PACKET = "UNEXPECTED_CFDP_PACKET";
    static final String ETYPE_TRANSFER_STARTED = "TRANSFER_STARTED";
    static final String ETYPE_TRANSFER_FINISHED = "TRANSFER_FINISHED";
    static final String ETYPE_EOF_LIMIT_REACHED = "EOF_LIMIT_REACHED";

    Map<CfdpTransactionId, CfdpTransfer> pendingTransfers = new HashMap<>();
    List<CfdpTransfer> completedTransfers = new ArrayList<>();
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    Stream cfdpIn;
    Stream cfdpOut;
    Bucket incomingBucket;
    long mySourceId;
    long destinationId;

    EventProducer eventProducer;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("inStream", OptionType.STRING).withDefault("cfdp_in");
        spec.addOption("outStream", OptionType.STRING).withDefault("cfdp_out");
        spec.addOption("sourceId", OptionType.INTEGER).withRequired(true);
        spec.addOption("destinationId", OptionType.INTEGER).withRequired(true);
        spec.addOption("incomingBucket", OptionType.STRING).withDefault("cfdpDown");
        spec.addOption("entityIdLength", OptionType.INTEGER).withDefault(2);
        spec.addOption("sequenceNrLength", OptionType.INTEGER).withDefault(4);
        spec.addOption("maxPduSize", OptionType.INTEGER).withDefault(512);
        spec.addOption("eofAckTimeout", OptionType.INTEGER).withDefault(3000);
        spec.addOption("maxEofResendAttempts", OptionType.INTEGER).withDefault(5);
        spec.addOption("sleepBetweenPdus", OptionType.INTEGER).withDefault(500);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        String inStream = config.getString("inStream");
        String outStream = config.getString("outStream");

        mySourceId = config.getLong("sourceId");
        destinationId = config.getLong("destinationId");

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        cfdpIn = ydb.getStream(inStream);
        if (cfdpIn == null) {
            throw new ConfigurationException("cannot find stream " + inStream);
        }
        cfdpOut = ydb.getStream(outStream);
        if (cfdpOut == null) {
            throw new ConfigurationException("cannot find stream " + outStream);
        }

        cfdpIn.addSubscriber(this);

        String bucketName = config.getString("incomingBucket");
        YarchDatabaseInstance globalYdb = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        try {
            incomingBucket = globalYdb.getBucket(bucketName);
            if (incomingBucket == null) {
                incomingBucket = globalYdb.createBucket(bucketName);
            }
        } catch (IOException e) {
            throw new InitException(e);
        }
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "CfdpService", 10000);
    }

    public CfdpTransfer getCfdpTransfer(CfdpTransactionId transferId) {
        return pendingTransfers.get(transferId);
    }

    public CfdpTransfer getCfdpTransfer(long id) {
        Optional<CfdpTransfer> r = pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny();
        if (r.isPresent()) {
            return r.get();
        } else {
            return completedTransfers.stream().filter(c -> c.getId() == id).findAny().orElse(null);
        }
    }

    public Collection<CfdpTransfer> getCfdpTransfers(boolean all) {
        if (all) {
            List<CfdpTransfer> r = new ArrayList<>();
            r.addAll(pendingTransfers.values());
            r.addAll(completedTransfers);
            return r;
        } else {
            return pendingTransfers.values();
        }
    }

    public Collection<CfdpTransfer> getCfdpTransfers(List<Long> transferIds) {
        List<CfdpTransactionId> transactionIds = transferIds.stream()
                .map(x -> new CfdpTransactionId(mySourceId, x)).collect(Collectors.toList());
        return this.pendingTransfers.values().stream().filter(transfer -> transactionIds.contains(transfer.getId()))
                .collect(Collectors.toList());
    }

    public CfdpTransfer processRequest(CfdpRequest request) {
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

        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(yamcsInstance, executor, request, cfdpOut, config,
                eventProducer);
        transfer.setMonitor(this);

        pendingTransfers.put(transfer.getTransactionId(), transfer);

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP upload (" + transfer.getTransactionId() + ")" + request.getObjectName() + " -> "
                        + request.getTargetPath());
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
        CfdpTransactionId id = packet.getTransactionId();
        CfdpTransfer transaction = null;
        if (pendingTransfers.containsKey(id)) {
            transaction = pendingTransfers.get(id);
        } else {
            // the communication partner has initiated a transfer
            transaction = instantiateTransaction(packet);
            if (transaction != null) {
                pendingTransfers.put(transaction.getTransactionId(), transaction);
            }
        }

        if (transaction != null) {
            transaction.processPacket(packet);
        }
    }

    private CfdpTransfer instantiateTransaction(CfdpPacket packet) {
        if (packet.getHeader().isFileDirective()
                && ((FileDirective) packet).getFileDirectiveCode() == FileDirectiveCode.Metadata) {
            MetadataPacket mpkt = (MetadataPacket) packet;
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP downlink (" + mpkt.getHeader().getTransactionId() + ")"
                            + mpkt.getSourceFilename() + " -> " + mpkt.getDestinationFilename());
            CfdpTransfer transfer = new CfdpIncomingTransfer(yamcsInstance, executor, config, mpkt, cfdpOut,
                    incomingBucket, eventProducer);
            transfer.setMonitor(this);
            return transfer;
        } else {
            eventProducer.sendInfo(ETYPE_UNEXPECTED_CFDP_PACKET,
                    "Unexpected CFDP packet received; " + packet);
            return null;
            // throw new IllegalArgumentException("Rogue CFDP packet received");
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        executor.shutdown();
        notifyStopped();
    }

    @Override
    public void streamClosed(Stream stream) {
    }

    public CfdpOutgoingTransfer upload(String objName, String target, boolean overwrite, boolean acknowledged,
            boolean createpath, Bucket b, byte[] objData) {

        return processPutRequest(
                new PutRequest(mySourceId, destinationId, objName, target, overwrite, acknowledged, createpath, b,
                        objData));
    }

    @Override
    public void stateChanged(CfdpTransfer cfdpTransfer) {
        if (cfdpTransfer.getTransferState() == TransferState.COMPLETED
                || cfdpTransfer.getTransferState() == TransferState.FAILED) {
            pendingTransfers.remove(cfdpTransfer.getId());
            completedTransfers.add(cfdpTransfer);
        }
    }
}
