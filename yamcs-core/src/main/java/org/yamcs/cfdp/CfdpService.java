package org.yamcs.cfdp;

import static org.yamcs.cfdp.CompletedTransfer.TDEF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import org.yamcs.cfdp.pdu.PduDecodingException;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.filetransfer.TransferOptions;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.FileTransferCapabilities;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
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

import com.google.common.collect.Streams;

/**
 * Implements CCSDS File Delivery Protocol (CFDP) in Yamcs.
 * <p>
 * The standard is specified in <a href="https://public.ccsds.org/Pubs/727x0b4.pdf"> CCSDS 727.0-B-4 </a>
 * 
 * @author nm
 *
 */
public class CfdpService extends AbstractYamcsService
        implements FileTransferService, StreamSubscriber, TransferMonitor {
    static final String ETYPE_UNEXPECTED_CFDP_PACKET = "UNEXPECTED_CFDP_PACKET";
    static final String ETYPE_TRANSFER_STARTED = "TRANSFER_STARTED";
    static final String ETYPE_TRANSFER_META = "TRANSFER_METADATA";
    static final String ETYPE_TRANSFER_FINISHED = "TRANSFER_FINISHED";
    static final String ETYPE_TRANSFER_SUSPENDED = "TRANSFER_SUSPENDED";
    static final String ETYPE_TRANSFER_RESUMED = "TRANSFER_RESUMED";
    static final String ETYPE_TRANSFER_COMPLETED = "TRANSFER_COMPLETED";
    static final String ETYPE_TX_LIMIT_REACHED = "TX_LIMIT_REACHED";
    static final String ETYPE_EOF_LIMIT_REACHED = "EOF_LIMIT_REACHED";
    static final String ETYPE_FIN_LIMIT_REACHED = "FIN_LIMIT_REACHED";
    static final String ETYPE_NO_LARGE_FILE = "LARGE_FILES_NOT_SUPPORTED";
    static final String ETYPE_PDU_DECODING_ERROR = "PDU_DECODING_ERROR";

    static final String BUCKET_OPT = "bucket";

    static final String TABLE_NAME = "cfdp";
    static final String SEQUENCE_NAME = "cfdp";

    Map<CfdpTransactionId, OngoingCfdpTransfer> pendingTransfers = new ConcurrentHashMap<>();
    Queue<QueuedCfdpTransfer> queuedTransfers = new ConcurrentLinkedQueue<>();

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
    int maxNumPendingDownloads;
    int maxNumPendingUploads;
    int archiveRetrievalLimit;
    int pendingAfterCompletion;

    boolean queueConcurrentUploads;
    boolean allowConcurrentFileOverwrites;
    List<String> directoryTerminators;

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
        entitySpec.addOption(BUCKET_OPT, OptionType.STRING).withDefault(null);

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
        spec.addOption("queueConcurrentUploads", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("allowConcurrentFileOverwrites", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("directoryTerminators", OptionType.LIST).withElementType(OptionType.STRING)
                .withDefault(Arrays.asList(":", "/", "\\"));
        spec.addOption("maxNumPendingDownloads", OptionType.INTEGER).withDefault(100);
        spec.addOption("maxNumPendingUploads", OptionType.INTEGER).withDefault(10);
        spec.addOption("inactivityTimeout", OptionType.INTEGER).withDefault(10000);

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
        maxNumPendingDownloads = config.getInt("maxNumPendingDownloads");
        maxNumPendingUploads = config.getInt("maxNumPendingUploads");
        archiveRetrievalLimit = config.getInt("archiveRetrievalLimit", 100);
        pendingAfterCompletion = config.getInt("ackAfterCompletion", 20000);
        queueConcurrentUploads = config.getBoolean("queueConcurrentUploads");
        allowConcurrentFileOverwrites = config.getBoolean("allowConcurrentFileOverwrites");
        directoryTerminators = config.getList("directoryTerminators");

        initSrcDst(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "CfdpService", 10000);
        idSeq = ydb.getSequence(SEQUENCE_NAME, true);
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
                if (c.containsKey(BUCKET_OPT)) {
                    bucket = getBucket(c.getString(BUCKET_OPT), c.getBoolean("global", true));
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
                if (c.containsKey(BUCKET_OPT)) {
                    bucket = getBucket(c.getString(BUCKET_OPT), c.getBoolean("global", true));
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

    public OngoingCfdpTransfer getCfdpTransfer(CfdpTransactionId transferId) {
        return pendingTransfers.get(transferId);
    }

    @Override
    public FileTransfer getFileTransfer(long id) {
        Optional<CfdpFileTransfer> r = Streams.concat(pendingTransfers.values().stream(), queuedTransfers.stream())
                .filter(c -> c.getId() == id).findAny();
        if (r.isPresent()) {
            return r.get();
        } else {
            return searchInArchive(id);
        }
    }

    private FileTransfer searchInArchive(long id) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            StreamSqlResult res = ydb.execute("select * from " + TABLE_NAME + " where id=?", id);
            FileTransfer r = null;
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

    @Override
    public List<FileTransfer> getTransfers() {
        List<FileTransfer> r = new ArrayList<>();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        pendingTransfers.values().stream().filter(trsf -> isRunning(trsf)).forEach(trsf -> r.add(trsf));
        r.addAll(queuedTransfers);

        try {
            StreamSqlResult res = ydb
                    .execute("select * from " + TABLE_NAME
                            + " where transferState='COMPLETED' or transferState='FAILED' "
                            + " order desc limit " + archiveRetrievalLimit);
            while (res.hasNext()) {
                Tuple t = res.next();
                r.add(new CompletedTransfer(t));
            }
            res.close();
            return r;

        } catch (Exception e) {
            log.error("Error executing query", e);
        }

        return r;
    }

    private CfdpFileTransfer processPutRequest(long id, long creationTime, PutRequest request) {
        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(yamcsInstance, id, creationTime, executor, request,
                cfdpOut, config, eventProducer, this, senderFaultHandlers);

        dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));

        stateChanged(transfer);
        pendingTransfers.put(transfer.getTransactionId(), transfer);

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP upload TXID[" + transfer.getTransactionId() + "] " + transfer.getObjectName()
                        + " -> " + transfer.getRemotePath());
        transfer.start();
        return transfer;
    }

    // called when queueConcurrentUploads = true, will start a queued transfer if no other transfer is running
    private void tryStartQueuedTransfer() {
        if (numPendingUploads() >= maxNumPendingUploads) {
            return;
        }

        QueuedCfdpTransfer trsf = queuedTransfers.poll();
        if (trsf != null) {
            processPutRequest(trsf.getId(), trsf.creationTime, trsf.putRequest);
        }
    }

    private long numPendingUploads() {
        return pendingTransfers.values().stream()
                .filter(trsf -> isRunning(trsf) && trsf.getDirection() == TransferDirection.UPLOAD)
                .count();
    }

    static boolean isRunning(OngoingCfdpTransfer trsf) {
        return trsf.state == TransferState.RUNNING || trsf.state == TransferState.PAUSED;
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
        CfdpPacket packet;
        try {
            packet = CfdpPacket.fromTuple(tuple);
        } catch (PduDecodingException e) {
            log.warn("Error decoding PDU: {}, packet: {}", e.toString(),
                    StringConverter.arrayToHexString(e.getData(), true));
            eventProducer.sendInfo(ETYPE_PDU_DECODING_ERROR, "Error decoding CFDP PDU; " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Unexpected errorr decoding pdu tuple", e);
            return;
        }

        CfdpTransactionId id = packet.getTransactionId();

        OngoingCfdpTransfer transfer = null;
        if (pendingTransfers.containsKey(id)) {
            transfer = pendingTransfers.get(id);
        } else {
            if (!isTransferInitiator(packet)) {
                eventProducer.sendInfo(ETYPE_UNEXPECTED_CFDP_PACKET,
                        "Unexpected CFDP packet received; " + packet.getHeader() + ": " + packet);
                return;
            }
            // the communication partner has initiated a transfer
            if (pendingTransfers.size() >= maxNumPendingDownloads) {
                eventProducer.sendInfo(ETYPE_TX_LIMIT_REACHED, "Maximum number of pending downloads "
                        + maxNumPendingDownloads + " reached. Dropping packet " + packet);
            } else {
                transfer = instantiateIncomingTransaction(packet);
                if (transfer != null) {
                    pendingTransfers.put(transfer.getTransactionId(), transfer);
                    OngoingCfdpTransfer t1 = transfer;
                    executor.submit(() -> {
                        dbStream.emitTuple(CompletedTransfer.toInitialTuple(t1));
                    });
                }
            }
        }

        if (transfer != null) {
            transfer.processPacket(packet);
            if (packet instanceof MetadataPacket) {
                OngoingCfdpTransfer t1 = transfer;
                executor.submit(() -> {
                    dbStream.emitTuple(CompletedTransfer.toInitialTuple(t1));
                });
            }
        }
    }

    private boolean isTransferInitiator(CfdpPacket packet) {
        return packet instanceof MetadataPacket
                || packet instanceof FileDataPacket
                || packet instanceof EofPacket;
    }

    private OngoingCfdpTransfer instantiateIncomingTransaction(CfdpPacket packet) {
        CfdpTransactionId txId = packet.getTransactionId();

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

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP downlink TXID[" + txId + "] " + remoteEntity + " -> " + localEntity);

        Bucket bucket = localEntity.bucket != null ? localEntity.bucket
                : remoteEntity.bucket != null ? remoteEntity.bucket
                        : incomingBucket;

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        OngoingCfdpTransfer transfer = new CfdpIncomingTransfer(yamcsInstance, idSeq.next(), creationTime, executor,
                config, packet.getHeader(), cfdpOut, bucket, eventProducer, this, receiverFaultHandlers);
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

    @Override
    public void registerTransferMonitor(TransferMonitor listener) {
        transferListeners.add(listener);
    }

    @Override
    public void unregisterTransferMonitor(TransferMonitor listener) {
        transferListeners.remove(listener);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (OngoingCfdpTransfer trsf : pendingTransfers.values()) {
            if (trsf.state == TransferState.RUNNING || trsf.state == TransferState.PAUSED) {
                trsf.failTransfer("service shutdown");
            }
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
    public void stateChanged(FileTransfer ft) {
        CfdpFileTransfer cfdpTransfer = (CfdpFileTransfer) ft;
        dbStream.emitTuple(CompletedTransfer.toUpdateTuple(cfdpTransfer));
        // Notify downstream listeners
        transferListeners.forEach(l -> l.stateChanged(cfdpTransfer));

        if (cfdpTransfer.getTransferState() == TransferState.COMPLETED
                || cfdpTransfer.getTransferState() == TransferState.FAILED) {

            if (cfdpTransfer instanceof OngoingCfdpTransfer) {
                // keep it in pending for a while such that PDUs from remote entity can still be answered
                executor.schedule(() -> {
                    pendingTransfers.remove(cfdpTransfer.getTransactionId());
                }, pendingAfterCompletion, TimeUnit.MILLISECONDS);
            }
            executor.submit(() -> tryStartQueuedTransfer());
        }
    }

    @Override
    public List<EntityInfo> getLocalEntities() {
        return localEntities.values().stream()
                .map(c -> EntityInfo.newBuilder().setName(c.name).setId(c.id).build())
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityInfo> getRemoteEntities() {
        return remoteEntities.values().stream()
                .map(c -> EntityInfo.newBuilder().setName(c.name).setId(c.id).build())
                .collect(Collectors.toList());
    }

    public OngoingCfdpTransfer getOngoingCfdpTransfer(long id) {
        return pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny().orElse(null);
    }

    @Override
    public synchronized CfdpFileTransfer startUpload(String source, Bucket bucket, String objectName,
            String destination, final String destinationPath, TransferOptions options) throws IOException {
        byte[] objData;
        objData = bucket.getObject(objectName);
        if (objData == null) {
            throw new InvalidRequestException("No object named '" + objectName + "' in bucket " + bucket.getName());
        }
        String absoluteDestinationPath = getAbsoluteDestinationPath(destinationPath, objectName);
        if (!allowConcurrentFileOverwrites) {
            if (pendingTransfers.values().stream()
                    .filter(trsf -> isRunning(trsf))
                    .anyMatch(trsf -> trsf.getRemotePath().equals(absoluteDestinationPath))) {
                throw new InvalidRequestException(
                        "There is already a transfer ongoing to '" + absoluteDestinationPath
                                + "' and allowConcurrentFileOverwrites is false");
            }

            if (queuedTransfers.stream()
                    .anyMatch(trsf -> trsf.getRemotePath().equals(absoluteDestinationPath))) {
                throw new InvalidRequestException(
                        "There is already a transfer queued to '" + absoluteDestinationPath
                                + "' and allowConcurrentFileOverwrites is false");
            }
        }

        long sourceId, destinationId;

        if (source == null) {
            sourceId = localEntities.values().iterator().next().id;
        } else {
            if (!localEntities.containsKey(source)) {
                throw new InvalidRequestException("Invalid source '" + source + "'");
            }
            sourceId = localEntities.get(source).id;
        }

        if (destination == null) {
            destinationId = remoteEntities.values().iterator().next().id;
        } else {
            if (!remoteEntities.containsKey(destination)) {
                throw new InvalidRequestException("Invalid destination '" + destination + "'");
            }
            destinationId = remoteEntities.get(destination).id;
        }

        PutRequest request = new PutRequest(sourceId, destinationId, objectName, absoluteDestinationPath,
                options.isOverwrite(), options.isReliable(), options.isClosureRequested(), options.isCreatePath(),
                bucket, objData);
        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        if (numPendingUploads() < maxNumPendingUploads) {
            return processPutRequest(idSeq.next(), creationTime, request);
        } else {
            QueuedCfdpTransfer transfer = new QueuedCfdpTransfer(idSeq.next(), creationTime, request);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(() -> tryStartQueuedTransfer());
            return transfer;
        }

    }

    private String getAbsoluteDestinationPath(String destinationPath, String localObjectName) {
        if (localObjectName == null) {
            throw new NullPointerException("local object name cannot be null");
        }
        if (destinationPath == null) {
            return localObjectName;
        }
        if (directoryTerminators.stream().anyMatch(dt -> destinationPath.endsWith(dt))) {
            return destinationPath + localObjectName;
        }
        return destinationPath;
    }

    @Override
    public void pause(FileTransfer transfer) {
        processPauseRequest(new PauseRequest(transfer));
    }

    @Override
    public void resume(FileTransfer transfer) {
        processResumeRequest(new ResumeRequest(transfer));
    }

    @Override
    public void cancel(FileTransfer transfer) {
        if (transfer instanceof OngoingCfdpTransfer) {
            processCancelRequest(new CancelRequest(transfer));
        } else if (transfer instanceof QueuedCfdpTransfer) {
            QueuedCfdpTransfer trsf = (QueuedCfdpTransfer) transfer;
            if (queuedTransfers.remove(trsf)) {
                trsf.setTransferState(TransferState.FAILED);
                trsf.setFailureReason("Cancelled while queued");
                stateChanged(trsf);
            }
        } else {
            throw new InvalidRequestException("Unknown transfer type " + transfer);
        }
    }

    @Override
    public FileTransfer startDownload(String sourceEntity, String sourcePath, String destinationEntity, Bucket bucket,
            String objectName, TransferOptions options) throws IOException, InvalidRequestException {
        throw new InvalidRequestException("Download not implemented");
    }

    @Override
    public FileTransferCapabilities getCapabilities() {
        return FileTransferCapabilities
                .newBuilder()
                .setDownload(false)
                .setUpload(true)
                .setReliability(true)
                .setRemotePath(true)
                .build();
    }
}
