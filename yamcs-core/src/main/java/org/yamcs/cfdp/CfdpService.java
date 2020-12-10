package org.yamcs.cfdp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.OngoingCfdpTransfer.FaultHandlingAction;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import static org.yamcs.cfdp.CompletedTransfer.TDEF;

/**
 * Implements CCSDS File Delivery Protocol (CFDP) in Yamcs.
 * <p>
 * The standard is specified in <a href="https://public.ccsds.org/Pubs/727x0b4.pdf"> CCSDS 727.0-B-4 </a>
 * 
 * @author nm
 *
 */
public class CfdpService extends AbstractYamcsService implements StreamSubscriber, TransferMonitor {
    public static final String DEFAULT_SRCDST = "default";

    static final String ETYPE_UNEXPECTED_CFDP_PACKET = "UNEXPECTED_CFDP_PACKET";
    static final String ETYPE_TRANSFER_STARTED = "TRANSFER_STARTED";
    static final String ETYPE_TRANSFER_META = "TRANSFER_METADATA";
    static final String ETYPE_TRANSFER_FINISHED = "TRANSFER_FINISHED";
    static final String ETYPE_TX_LIMIT_REACHED = "TX_LIMIT_REACHED";
    static final String ETYPE_EOF_LIMIT_REACHED = "EOF_LIMIT_REACHED";
    static final String ETYPE_FIN_LIMIT_REACHED = "FIN_LIMIT_REACHED";
    static final String ETYPE_NO_LARGE_FILE = "LARGE_FILES_NOT_SUPPORTED";

    static final String TABLE_NAME = "cfdp";
    static final String SEQUENCE_NAME = "cfdp";

    Map<CfdpTransactionId, OngoingCfdpTransfer> pendingTransfers = new HashMap<>();
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    Map<ConditionCode, FaultHandlingAction> receiverFaultHandlers;
    Map<ConditionCode, FaultHandlingAction> senderFaultHandlers;
    Stream cfdpIn;
    Stream cfdpOut;
    Bucket incomingBucket;

    EventProducer eventProducer;

    private Set<TransferMonitor> transferListeners = new CopyOnWriteArraySet<>();
    private Map<String, EntityConf> localEntities = new LinkedHashMap<>();
    private Map<String, EntityConf> remoteEntities = new LinkedHashMap<>();

    boolean nakMetadata;
    int maxNumPendingTransactions;
    int archiveRetrievalLimit;
    int pendingAfterCompletion;

    private Stream dbStream;

    Sequence idSeq;
    static final Map<String, ConditionCode> VALID_CODES = new HashMap<>();

    static {
        VALID_CODES.put("AckLimitReached", ConditionCode.ACK_LIMIT_REACHED);
        VALID_CODES.put("KeepAliveLimitReached", ConditionCode.KEEP_ALIVE_LIMIT_REACHED);
        VALID_CODES.put("InvalidTransmissionMode", ConditionCode.INVALID_TRANSMISSION_MODE);
        VALID_CODES.put("FilestoreRejection", ConditionCode.FILESTORE_REJECTION);
        VALID_CODES.put("FileChecksumFailure", ConditionCode.FILE_CHECKSUM_FAILURE);
        VALID_CODES.put("FileSizeError", ConditionCode.FILE_SIZE_ERROR);
        VALID_CODES.put("NakLimitReached", ConditionCode.NAK_LIMIT_REACHED);
        VALID_CODES.put("InactivityDetected", ConditionCode.INACTIVITY_DETECTED);
        VALID_CODES.put("InvalidFileStructure", ConditionCode.INVALID_FILE_STRUCTURE);
        VALID_CODES.put("CheckLimitReached", ConditionCode.CHECK_LIMIT_REACHED);
        VALID_CODES.put("UnsupportedChecksum", ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
    }

    @Override
    public Spec getSpec() {
        Spec entitySpec = new Spec();
        entitySpec.addOption("name", OptionType.STRING);
        entitySpec.addOption("id", OptionType.INTEGER);
        entitySpec.addOption("bucket", OptionType.STRING).withDefault(null);

        Spec spec = new Spec();
        spec.addOption("inStream", OptionType.STRING).withDefault("cfdp_in");
        spec.addOption("outStream", OptionType.STRING).withDefault("cfdp_out");
        spec.addOption("sourceId", OptionType.INTEGER)
                .withDeprecationMessage("please use the localEntities");
        spec.addOption("destinationId", OptionType.INTEGER)
                .withDeprecationMessage("please use the remoteEntities");
        spec.addOption("incomingBucket", OptionType.STRING).withDefault("cfdpDown");
        spec.addOption("entityIdLength", OptionType.INTEGER).withDefault(2);
        spec.addOption("sequenceNrLength", OptionType.INTEGER).withDefault(4);
        spec.addOption("maxPduSize", OptionType.INTEGER).withDefault(512);
        spec.addOption("eofAckTimeout", OptionType.INTEGER).withDefault(5000);
        spec.addOption("eofAckLimit", OptionType.INTEGER).withDefault(5);
        spec.addOption("finAckTimeout", OptionType.INTEGER).withDefault(5000);
        spec.addOption("finAckLimit", OptionType.INTEGER).withDefault(5);
        spec.addOption("sleepBetweenPdus", OptionType.INTEGER).withDefault(500);
        spec.addOption("localEntities", OptionType.LIST).withElementType(OptionType.MAP).withSpec(entitySpec);
        spec.addOption("remoteEntities", OptionType.LIST).withElementType(OptionType.MAP).withSpec(entitySpec);
        spec.addOption("nakLimit", OptionType.INTEGER).withDefault(-1);
        spec.addOption("nakTimeout", OptionType.INTEGER).withDefault(5000);
        spec.addOption("immediateNak", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("archiveRetrievalLimit", OptionType.INTEGER).withDefault(100);
        spec.addOption("receiverFaultHandlers", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("senderFaultHandlers", OptionType.MAP).withSpec(Spec.ANY);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        String inStream = config.getString("inStream");
        String outStream = config.getString("outStream");

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

        incomingBucket = getBucket(config.getString("incomingBucket"), true);
        maxNumPendingTransactions = config.getInt("maxNumPendingTransactions", 100);
        archiveRetrievalLimit = config.getInt("archiveRetrievalLimit", 100);
        pendingAfterCompletion = config.getInt("ackAfterCompletion", 20000);

        initSrcDst(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "CfdpService", 10000);
        idSeq = ydb.getSequence(SEQUENCE_NAME);
        if (config.containsKey("senderFaultHandlers")) {
            senderFaultHandlers = readFaultHandlers(config.getMap("senderFaultHandlers"));
        } else {
            senderFaultHandlers = Collections.emptyMap();
        }

        if (config.containsKey("receiverFaultHandlers")) {
            receiverFaultHandlers = readFaultHandlers(config.getMap("receiverFaultHandlers"));
        } else {
            receiverFaultHandlers = Collections.emptyMap();
        }
        setupRecording(ydb);
    }

    private Map<ConditionCode, FaultHandlingAction> readFaultHandlers(Map<String, String> map) {
        Map<ConditionCode, FaultHandlingAction> m = new HashMap<>();
        for (Map.Entry<String, String> me : map.entrySet()) {
            ConditionCode code = VALID_CODES.get(me.getKey());
            if (code == null) {
                throw new ConfigurationException(
                        "Unknown condition code " + me.getKey() + ". Valid codes: " + VALID_CODES.keySet());
            }
            FaultHandlingAction action = FaultHandlingAction.fromString(me.getValue());
            if (action == null) {
                throw new ConfigurationException(
                        "Unknown action " + me.getKey() + ". Valid actions: " + FaultHandlingAction.actions());
            }
            m.put(code, action);
        }
        return m;
    }

    private void initSrcDst(YConfiguration config) throws InitException {
        if (config.containsKey("sourceId")) {
            localEntities.put("default", new EntityConf(config.getLong("sourceId"), "default", null));
        }

        if (config.containsKey("destinationId")) {
            remoteEntities.put("default", new EntityConf(config.getLong("destinationId"), "default", null));
        }
        if (config.containsKey("localEntities")) {
            for (YConfiguration c : config.getConfigList("localEntities")) {
                long id = c.getLong("id");
                String name = c.getString("name");
                if (localEntities.containsKey(name)) {
                    throw new ConfigurationException("A local entity named '" + name + "' has already been configured");
                }
                Bucket bucket = null;
                if (c.containsKey("bucket")) {
                    bucket = getBucket(c.getString("bucket"), c.getBoolean("global", true));
                }
                EntityConf ent = new EntityConf(id, name, bucket);
                localEntities.put(name, ent);
            }
        }

        if (config.containsKey("remoteEntities")) {
            for (YConfiguration c : config.getConfigList("remoteEntities")) {
                long id = c.getLong("id");
                String name = c.getString("name");
                if (remoteEntities.containsKey(name)) {
                    throw new ConfigurationException("A local entity named '" + name + "' has already been configured");
                }
                Bucket bucket = null;
                if (c.containsKey("bucket")) {
                    bucket = getBucket(c.getString("bucket"), c.getBoolean("global", true));
                }
                EntityConf ent = new EntityConf(id, name, bucket);
                remoteEntities.put(name, ent);
            }
        }

        if (localEntities.isEmpty()) {
            throw new ConfigurationException("No local entity specified");
        }
        if (remoteEntities.isEmpty()) {
            throw new InitException("No remote entity specified");
        }
    }

    private Bucket getBucket(String bucketName, boolean global) throws InitException {
        YarchDatabaseInstance ydb = global ? YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE)
                : YarchDatabase.getInstance(yamcsInstance);
        try {
            Bucket bucket = ydb.getBucket(bucketName);
            if (bucket == null) {
                bucket = ydb.createBucket(bucketName);
            }
            return bucket;
        } catch (IOException e) {
            throw new InitException(e);
        }
    }

    private void setupRecording(YarchDatabaseInstance ydb) throws InitException {
        try {
            if (ydb.getTable(TABLE_NAME) == null) {
                String query = "create table " + TABLE_NAME + "(" + TDEF.getStringDefinition1()
                        + ", primary key(id, serverId))";
                ydb.execute(query);
            }
            String streamName = TABLE_NAME + "table_in";
            if (ydb.getStream(streamName) == null) {
                ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
            }
            ydb.execute("upsert_append into " + TABLE_NAME + " select * from " + streamName);
            dbStream = ydb.getStream(streamName);
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }
    }

    private void addAll(Map<String, Long> m1, Map<String, Long> m2) throws InitException {
        for (Map.Entry<String, Long> me : m2.entrySet()) {
            if (m1.containsKey(me.getKey())) {
                throw new InitException("Duplicate name " + me.getKey());
            }
            Number n = (Number) me.getValue();
            m1.put(me.getKey(), n.longValue());
        }
    }

    public OngoingCfdpTransfer getCfdpTransfer(CfdpTransactionId transferId) {
        return pendingTransfers.get(transferId);
    }

    public CfdpTransfer getCfdpTransfer(long id) {
        Optional<OngoingCfdpTransfer> r = pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny();
        if (r.isPresent()) {
            return r.get();
        } else {
            return searchInArchive(id);
        }
    }

    private CfdpTransfer searchInArchive(long id) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            StreamSqlResult res = ydb.execute("select * from " + TABLE_NAME + " where id=?", id);
            CfdpTransfer r = null;
            if (res.hasNext()) {
                r = new CompletedTransfer(res.next());
            }
            res.close();
            return r;

        } catch (Exception e) {
            log.error("Error executing query", e);
            return null;
        }
    }

    public Collection<CfdpTransfer> getCfdpTransfers() {
        List<CfdpTransfer> r = new ArrayList<>();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        r.addAll(pendingTransfers.values());

        try {
            StreamSqlResult res = ydb
                    .execute("select * from " + TABLE_NAME + " order desc limit " + archiveRetrievalLimit);
            while (res.hasNext()) {
                r.add(new CompletedTransfer(res.next()));
            }
            res.close();
            return r;

        } catch (Exception e) {
            log.error("Error executing query", e);
        }
        return r;
    }

    public OngoingCfdpTransfer processRequest(CfdpRequest request) {
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
        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(yamcsInstance, idSeq.next(), executor, request,
                cfdpOut, config, eventProducer, this, senderFaultHandlers);
        dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));

        stateChanged(transfer);
        pendingTransfers.put(transfer.getTransactionId(), transfer);

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP upload (" + transfer.getTransactionId() + ")" + request.getObjectName() + " -> "
                        + request.getTargetPath());
        transfer.start();

        return transfer;
    }

    private OngoingCfdpTransfer processPauseRequest(PauseRequest request) {
        OngoingCfdpTransfer transfer = request.getTransfer();
        transfer.pauseTransfer();
        return transfer;
    }

    private OngoingCfdpTransfer processResumeRequest(ResumeRequest request) {
        OngoingCfdpTransfer transfer = request.getTransfer();
        transfer.resumeTransfer();
        return transfer;
    }

    private OngoingCfdpTransfer processCancelRequest(CancelRequest request) {
        OngoingCfdpTransfer transfer = request.getTransfer();
        transfer.cancelTransfer();
        return transfer;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        CfdpPacket packet = CfdpPacket.fromTuple(tuple);
        CfdpTransactionId id = packet.getTransactionId();

        OngoingCfdpTransfer transaction = null;
        if (pendingTransfers.containsKey(id)) {
            transaction = pendingTransfers.get(id);
        } else {

            // the communication partner has initiated a transfer
            if (pendingTransfers.size() >= maxNumPendingTransactions) {
                eventProducer.sendInfo(ETYPE_TX_LIMIT_REACHED, "Maximum number of pending transfers "
                        + maxNumPendingTransactions + " reached. Dropping packet " + packet);
            } else {
                transaction = instantiateIncomingTransaction(packet);
                if (transaction != null) {
                    pendingTransfers.put(transaction.getTransactionId(), transaction);
                }
            }
        }

        if (transaction != null) {
            transaction.processPacket(packet);
        }
    }

    private OngoingCfdpTransfer instantiateIncomingTransaction(CfdpPacket packet) {
        CfdpTransactionId txId = packet.getTransactionId();

        if (packet instanceof MetadataPacket) {
            MetadataPacket mpkt = (MetadataPacket) packet;
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP downlink (" + txId + ")"
                            + mpkt.getSourceFilename() + " -> " + mpkt.getDestinationFilename());

        } else if (packet instanceof FileDataPacket || packet instanceof EofPacket) {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP downlink (" + txId
                            + "); the metadata PDU has not yet been received");

        } else {
            eventProducer.sendInfo(ETYPE_UNEXPECTED_CFDP_PACKET,
                    "Unexpected CFDP packet received; " + txId + ": " + packet);
            return null;
        }

        if (packet.getHeader().isLargeFile()) {
            eventProducer.sendInfo(ETYPE_NO_LARGE_FILE,
                    "Large files not supported; " + txId + ": " + packet);
            return null;
        }

        EntityConf remoteEntity = getRemoteEntity(txId.getInitiatorEntity());
        if (remoteEntity == null) {
            eventProducer.sendInfo(ETYPE_UNEXPECTED_CFDP_PACKET,
                    "Received a transaction start for an unknown remote entity Id " + txId.getInitiatorEntity());
            return null;
        }

        EntityConf localEntity = getLocalEntity(packet.getHeader().getDestinationId());
        if (localEntity == null) {
            eventProducer.sendInfo(ETYPE_UNEXPECTED_CFDP_PACKET,
                    "Received a transaction start for an unknown local entity Id "
                            + packet.getHeader().getDestinationId());
            return null;
        }
        Bucket bucket = localEntity.bucket != null ? localEntity.bucket
                : remoteEntity.bucket != null ? remoteEntity.bucket
                        : incomingBucket;

        OngoingCfdpTransfer transfer = new CfdpIncomingTransfer(yamcsInstance, idSeq.next(), executor, config,
                packet.getHeader(), cfdpOut, bucket, eventProducer, this, receiverFaultHandlers);
        return transfer;
    }

    public EntityConf getRemoteEntity(long entityId) {
        return remoteEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().id == entityId)
                .map(me -> me.getValue())
                .findAny()
                .orElse(null);
    }

    public EntityConf getLocalEntity(long entityId) {
        return localEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().id == entityId)
                .map(me -> me.getValue())
                .findAny()
                .orElse(null);
    }

    public void addTransferListener(TransferMonitor listener) {
        transferListeners.add(listener);
    }

    public void removeTransferListener(TransferMonitor listener) {
        transferListeners.remove(listener);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (OngoingCfdpTransfer trsf : pendingTransfers.values()) {
            trsf.failTransfer("service shutdown");
        }
        executor.shutdown();
        notifyStopped();
    }

    @Override
    public void streamClosed(Stream stream) {
        if (isRunning()) {
            log.debug("Stream {} closed", stream.getName());
            notifyFailed(new Exception("Stream " + stream.getName() + " cloased"));
        }
    }

    @Override
    public void stateChanged(OngoingCfdpTransfer cfdpTransfer) {
        dbStream.emitTuple(CompletedTransfer.toUpdateTuple(cfdpTransfer));
        // Notify downstream listeners
        transferListeners.forEach(l -> l.stateChanged(cfdpTransfer));

        if (cfdpTransfer.getTransferState() == TransferState.COMPLETED
                || cfdpTransfer.getTransferState() == TransferState.FAILED) {

            // keep it in pending for a while such that PDUs from remote entity can still be answered
            executor.schedule(() -> {
                pendingTransfers.remove(cfdpTransfer.getTransactionId());
            }, pendingAfterCompletion, TimeUnit.MILLISECONDS);
        }
    }

    public Map<String, Long> getLocalEntities() {
        return localEntities.entrySet().stream().collect(Collectors.toMap(me -> me.getKey(), me -> me.getValue().id));
    }

    public Map<String, Long> getRemoteEntities() {
        return remoteEntities.entrySet().stream().collect(Collectors.toMap(me -> me.getKey(), me -> me.getValue().id));
    }

    public OngoingCfdpTransfer getOngoingCfdpTransfer(long id) {
        return pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny().orElse(null);
    }
}
