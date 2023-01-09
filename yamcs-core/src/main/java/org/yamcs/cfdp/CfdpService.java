package org.yamcs.cfdp;

import static org.yamcs.cfdp.CompletedTransfer.TDEF;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
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
import org.yamcs.cfdp.pdu.*;
import org.yamcs.cfdp.pdu.DirectoryListingResponse.ListingResponseCode;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.filetransfer.RemoteFileListMonitor;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.filetransfer.TransferOptions;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.FileTransferCapabilities;
import org.yamcs.protobuf.RemoteFile;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.protobuf.ListFilesResponse;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimestampUtil;
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
    static final String ETYPE_UNEXPECTED_CFDP_PDU = "UNEXPECTED_CFDP_PDU";
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
    Queue<QueuedCfdpOutgoingTransfer> queuedTransfers = new ConcurrentLinkedQueue<>();

    FileDownloadRequests fileDownloadRequests = new FileDownloadRequests();
    Map<CfdpTransactionId, DirectoryListingRequest> directoryListingRequests = new ConcurrentHashMap<>();
    Map<List<String>, ListFilesResponse> fileLists = new ConcurrentHashMap<>();

    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    Map<ConditionCode, FaultHandlingAction> receiverFaultHandlers;
    Map<ConditionCode, FaultHandlingAction> senderFaultHandlers;
    Stream cfdpIn;
    Stream cfdpOut;
    Bucket defaultIncomingBucket;

    EventProducer eventProducer;

    private Set<TransferMonitor> transferListeners = new CopyOnWriteArraySet<>();
    private Set<RemoteFileListMonitor> fileListListeners = new CopyOnWriteArraySet<>();
    private Map<String, EntityConf> localEntities = new LinkedHashMap<>();
    private Map<String, EntityConf> remoteEntities = new LinkedHashMap<>();


    private boolean allowRemoteProvidedBucket;
    private boolean allowRemoteProvidedSubdirectory;

    private boolean allowDownloadOverwrites;
    private int maxExistingFileRenames;

    boolean nakMetadata;
    int maxNumPendingDownloads;
    int maxNumPendingUploads;
    int archiveRetrievalLimit;
    int pendingAfterCompletion;

    boolean queueConcurrentUploads;
    boolean allowConcurrentFileOverwrites;
    List<String> directoryTerminators;
    private String directoryListingFileTemplate;

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
        VALID_CODES.put("CancelRequestReceived", ConditionCode.CANCEL_REQUEST_RECEIVED);
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
        spec.addOption("allowRemoteProvidedBucket", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("allowRemoteProvidedSubdirectory", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("allowDownloadOverwrites", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("maxExistingFileRenames", OptionType.INTEGER).withDefault(1000);
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
        spec.addOption("pendingAfterCompletion", OptionType.INTEGER).withDefault(600000);
        spec.addOption("directoryListingFileTemplate", OptionType.STRING).withDefault(".dirlist-{TIMESTAMP}.tmp");

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

        defaultIncomingBucket = getBucket(config.getString("incomingBucket"), true);
        // TODO: duplicate default values as specified in getSpec?
        allowRemoteProvidedBucket = config.getBoolean("allowRemoteProvidedBucket", false);
        allowRemoteProvidedSubdirectory = config.getBoolean("allowRemoteProvidedSubdirectory", false);
        allowDownloadOverwrites = config.getBoolean("allowDownloadOverwrites", false);
        maxExistingFileRenames = config.getInt("maxExistingFileRenames", 1000);
        maxNumPendingDownloads = config.getInt("maxNumPendingDownloads");
        maxNumPendingUploads = config.getInt("maxNumPendingUploads");
        archiveRetrievalLimit = config.getInt("archiveRetrievalLimit", 100);
        pendingAfterCompletion = config.getInt("pendingAfterCompletion", 600000);
        queueConcurrentUploads = config.getBoolean("queueConcurrentUploads");
        allowConcurrentFileOverwrites = config.getBoolean("allowConcurrentFileOverwrites");
        directoryTerminators = config.getList("directoryTerminators");
        directoryListingFileTemplate = config.getString("directoryListingFileTemplate");


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
        Map<ConditionCode, FaultHandlingAction> m = new EnumMap<>(ConditionCode.class);
        for (Map.Entry<String, String> me : map.entrySet()) {
            ConditionCode code = VALID_CODES.get(me.getKey());
            if (code == null) {
                throw new ConfigurationException(
                        "Unknown condition code " + me.getKey() + ". Valid codes: " + VALID_CODES.keySet());
            }
            FaultHandlingAction action = FaultHandlingAction.fromString(me.getValue());
            if (action == null) {
                throw new ConfigurationException(
                        "Unknown action " + me.getValue() + ". Valid actions: " + FaultHandlingAction.actions());
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
                    throw new ConfigurationException("Duplicate local entity '" + name + "'.");
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
                    throw new ConfigurationException("Duplicate remote entity '" + name + "'.");
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
            throw new ConfigurationException("No remote entity specified");
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
        List<FileTransfer> toReturn = new ArrayList<>();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        pendingTransfers.values().stream().filter(CfdpService::isRunning).forEach(toReturn::add);
        toReturn.addAll(queuedTransfers);

        try {
            StreamSqlResult res = ydb
                    .execute("select * from " + TABLE_NAME
                            + " where transferState='COMPLETED' or transferState='FAILED' "
                            + " order desc limit " + archiveRetrievalLimit);
            while (res.hasNext()) {
                Tuple t = res.next();
                toReturn.add(new CompletedTransfer(t));
            }
            res.close();
            return toReturn;

        } catch (Exception e) {
            log.error("Error executing query", e);
            return Collections.emptyList();
        }
    }

    private CfdpFileTransfer processPutRequest(long initiatorEntityId, long seqNum, long creationTime, PutRequest request,
            Bucket bucket) {
        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(yamcsInstance, initiatorEntityId, seqNum, creationTime,
                executor, request, cfdpOut, config, bucket, eventProducer, this, senderFaultHandlers);

        dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));

        stateChanged(transfer);
        pendingTransfers.put(transfer.getTransactionId(), transfer);

        if(request.getFileLength() > 0) {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP upload TXID[" + transfer.getTransactionId() + "] " + transfer.getObjectName()
                            + " -> " + transfer.getRemotePath());
        } else {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP upload TXID[" + transfer.getTransactionId() + "] Fileless transfer (metadata options: \n"
                            + (request.getMetadata() != null ? request.getMetadata().getOptions().stream().map(TLV::toString).collect(Collectors.joining(",\n")) : "") + "\n)");
        }
        transfer.start();
        return transfer;
    }

    // called when queueConcurrentUploads = true, will start a queued transfer if no other transfer is running
    private void tryStartQueuedTransfer() {
        if (numPendingUploads() >= maxNumPendingUploads) {
            return;
        }

        QueuedCfdpOutgoingTransfer trsf = queuedTransfers.poll();
        if (trsf != null) {
            processPutRequest(trsf.getInitiatorEntityId(), trsf.getId(), trsf.getCreationTime(), trsf.getPutRequest(), trsf.getBucket());
        }
    }

    private long numPendingUploads() {
        return pendingTransfers.values().stream()
                .filter(trsf -> isRunning(trsf) && trsf.getDirection() == TransferDirection.UPLOAD)
                .count();
    }

    private long numPendingDownloads() {
        return pendingTransfers.values().stream()
                .filter(trsf -> isRunning(trsf) && trsf.getDirection() == TransferDirection.DOWNLOAD)
                .count();
    }

    static boolean isRunning(OngoingCfdpTransfer trsf) {
        return trsf.state == TransferState.RUNNING || trsf.state == TransferState.PAUSED
                || trsf.state == TransferState.CANCELLING;
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
            if (packet == null) {// not supported PDU, ignored
                return;
            }
        } catch (PduDecodingException e) {
            log.warn("Error decoding PDU: {}, packet: {}", e.toString(),
                    StringConverter.arrayToHexString(e.getData(), true));
            eventProducer.sendWarning(ETYPE_PDU_DECODING_ERROR, "Error decoding CFDP PDU; " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Unexpected error decoding pdu tuple", e);
            return;
        }

        CfdpTransactionId id = packet.getTransactionId();

        OngoingCfdpTransfer transfer = null;
        if (pendingTransfers.containsKey(id)) {
            transfer = pendingTransfers.get(id);
        } else {
            if (!isTransferInitiator(packet)) {
                eventProducer.sendWarning(ETYPE_UNEXPECTED_CFDP_PDU,
                        "Unexpected CFDP PDU received; " + packet.getHeader() + ": " + packet);
                return;
            }
            // the communication partner has initiated a transfer

            if (numPendingDownloads() >= maxNumPendingDownloads) {
                eventProducer.sendWarning(ETYPE_TX_LIMIT_REACHED, "Maximum number of pending downloads "
                        + maxNumPendingDownloads + " reached. Dropping packet " + packet);
            } else {
                transfer = instantiateIncomingTransaction(packet);
                if (transfer != null) {
                    pendingTransfers.put(transfer.getTransactionId(), transfer);
                    OngoingCfdpTransfer t1 = transfer;
                    executor.submit(() -> dbStream.emitTuple(CompletedTransfer.toInitialTuple(t1)));
                }
            }
        }

        if (transfer != null) {
            transfer.processPacket(packet);
            if (packet instanceof MetadataPacket) {
                OngoingCfdpTransfer t1 = transfer;
                executor.submit(() -> dbStream.emitTuple(CompletedTransfer.toInitialTuple(t1)));
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
            eventProducer.sendWarning(ETYPE_NO_LARGE_FILE, "Large files not supported; " + txId + ": " + packet);
            return null;
        }

        EntityConf remoteEntity = getRemoteEntity(txId.getInitiatorEntity());
        if (remoteEntity == null) {
            eventProducer.sendWarning(ETYPE_UNEXPECTED_CFDP_PDU,
                    "Received a transaction start for an unknown remote entity Id " + txId.getInitiatorEntity());
            return null;
        }

        EntityConf localEntity = getLocalEntity(packet.getHeader().getDestinationId());
        if (localEntity == null) {
            eventProducer.sendWarning(ETYPE_UNEXPECTED_CFDP_PDU,
                    "Received a transaction start for an unknown local entity Id "
                            + packet.getHeader().getDestinationId());
            return null;
        }

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP downlink TXID[" + txId + "] " + remoteEntity + " -> " + localEntity);

        Bucket bucket = defaultIncomingBucket;

        if (localEntity.bucket != null) {
            bucket = localEntity.bucket;
        } else if (remoteEntity.bucket != null) {
            bucket = remoteEntity.bucket;
        }

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        final FileSaveHandler fileSaveHandler = new FileSaveHandler(yamcsInstance, bucket, fileDownloadRequests,
                allowRemoteProvidedBucket, allowRemoteProvidedSubdirectory, allowDownloadOverwrites, maxExistingFileRenames);

        return new CfdpIncomingTransfer(yamcsInstance, idSeq.next(), creationTime, executor, config, packet.getHeader(),
                cfdpOut, fileSaveHandler, eventProducer, this, receiverFaultHandlers
        );
    }

    public EntityConf getRemoteEntity(long entityId) {
        return remoteEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().id == entityId)
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);
    }

    public EntityConf getLocalEntity(long entityId) {
        return localEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().id == entityId)
                .map(Map.Entry::getValue)
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
    public void registerRemoteFileListMonitor(RemoteFileListMonitor listener) {
        log.debug("Registering file list listener");
        fileListListeners.add(listener);
    }

    @Override
    public void unregisterRemoteFileListMonitor(RemoteFileListMonitor listener) {
        log.debug("Un-registering file list listener");
        fileListListeners.remove(listener);
    }

    @Override
    protected void doStart() {
        cfdpIn.addSubscriber(this);
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
        cfdpIn.removeSubscriber(this);
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
                executor.schedule(() -> pendingTransfers.remove(cfdpTransfer.getTransactionId()),
                        pendingAfterCompletion, TimeUnit.MILLISECONDS);
            }
            executor.submit(this::tryStartQueuedTransfer);
        }

        if(cfdpTransfer.getTransferState() == TransferState.COMPLETED && cfdpTransfer instanceof CfdpIncomingTransfer) {
            CfdpIncomingTransfer incomingTransfer = (CfdpIncomingTransfer) cfdpTransfer;
            if(incomingTransfer.getDirectoryListingResponse() != null) {
                notifyFileListListeners(incomingTransfer);
            }
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
                    .filter(CfdpService::isRunning)
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

        FilePutRequest request = new FilePutRequest(sourceId, destinationId, objectName, absoluteDestinationPath,
                options.isOverwrite(), options.isReliable(), options.isClosureRequested(), options.isCreatePath(),
                bucket, objData);
        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        if (numPendingUploads() < maxNumPendingUploads) {
            return processPutRequest(sourceId, idSeq.next(), creationTime, request, bucket);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(sourceId, idSeq.next(), creationTime,
                    request, bucket);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
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
        if (directoryTerminators.stream().anyMatch(destinationPath::endsWith)) {
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
        } else if (transfer instanceof QueuedCfdpOutgoingTransfer) {
            QueuedCfdpOutgoingTransfer trsf = (QueuedCfdpOutgoingTransfer) transfer;
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

        long sourceId, destinationId;

        if(destinationEntity == null) {
            destinationId = localEntities.values().iterator().next().id;
        } else {
            if (!localEntities.containsKey(destinationEntity)) {
                throw new InvalidRequestException("Invalid destination '" + destinationEntity + "'");
            }
            destinationId = localEntities.get(destinationEntity).id;
        }

        if (sourceEntity == null) {
            sourceId = remoteEntities.values().iterator().next().id;
        } else {
            if (!remoteEntities.containsKey(sourceEntity)) {
                throw new InvalidRequestException("Invalid source '" + sourceEntity + "'");
            }
            sourceId = remoteEntities.get(sourceEntity).id;
        }

        if(objectName.strip().equals("")) {
            String[] splitPath = sourcePath.split("[\\\\/]");
            objectName = splitPath[splitPath.length - 1];
        }

        // Prepare request
        ArrayList<MessageToUser> messagesToUser = new ArrayList<>(
                List.of(new ProxyPutRequest(destinationId, sourcePath, objectName)));
        if(options.isReliableSet()) {
            messagesToUser.add(new ProxyTransmissionMode(options.isReliable() ? CfdpPacket.TransmissionMode.ACKNOWLEDGED : CfdpPacket.TransmissionMode.UNACKNOWLEDGED));
        }
        if(options.isClosureRequestedSet()) {
            messagesToUser.add(new ProxyClosureRequest(options.isClosureRequested()));
        }

        PutRequest request = new PutRequest(sourceId,
                options.isReliable() ? CfdpPacket.TransmissionMode.ACKNOWLEDGED : CfdpPacket.TransmissionMode.UNACKNOWLEDGED,
                messagesToUser
        );
        CfdpTransactionId transactionId = request.process(destinationId, idSeq.next(), ChecksumType.MODULAR, config);

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        fileDownloadRequests.addTransfer(transactionId, bucket.getName());
        if (numPendingUploads() < maxNumPendingUploads) {
            return processPutRequest(destinationId, transactionId.getSequenceNumber(), creationTime, request, bucket);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(destinationId, transactionId.getSequenceNumber(),
                    creationTime, request, bucket);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
            return transfer;
        }
    }

    @Override
    public FileTransferCapabilities getCapabilities() {
        return FileTransferCapabilities
                .newBuilder()
                .setDownload(true)
                .setUpload(true)
                .setReliability(true)
                .setRemotePath(true)
                .setFileList(true)
                .build();
    }

    @Override
    public void requestFileList(String destination, String remotePath) {
        String ROOT = "/"; // TODO param
        // Start upload of Directory Listing Request
        long destinationId;

        if(destination == null) {
            destinationId = remoteEntities.values().iterator().next().id;
        } else {
            if (!remoteEntities.containsKey(destination)) {
                throw new InvalidRequestException("Invalid destination '" + destination + "'");
            }
            destinationId = remoteEntities.get(destination).id;
        }

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        DirectoryListingRequest directoryListingRequest = new DirectoryListingRequest(ROOT + remotePath, directoryListingFileTemplate.replace("{TIMESTAMP}",
                Instant.ofEpochMilli(creationTime).toString()));
        ArrayList<MessageToUser> messagesToUser = new ArrayList<>(List.of(directoryListingRequest));

        // TODO: reliability
        PutRequest request = new PutRequest(destinationId, CfdpPacket.TransmissionMode.ACKNOWLEDGED, messagesToUser);
        // TODO: initiatingEntityId
        EntityConf localEntity = localEntities.values().iterator().next();
        long sourceId = localEntity.id;
        CfdpTransactionId transactionId = request.process(sourceId, idSeq.next(), ChecksumType.MODULAR, config);

        // TODO: bucket?
        Bucket bucket = localEntity.bucket;

        fileDownloadRequests.addTransfer(transactionId, bucket.getName());
        directoryListingRequests.put(transactionId, directoryListingRequest);
        if (numPendingUploads() < maxNumPendingUploads) {
            processPutRequest(sourceId, transactionId.getSequenceNumber(), creationTime, request, bucket);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(sourceId, transactionId.getSequenceNumber(),
                    creationTime, request, bucket);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
        }
    }

    @Override
    public ListFilesResponse getFileList(String destination, String remotePath) {
        // TODO: to param
        boolean autoReload = true;
        if (autoReload) {
            requestFileList(destination, remotePath);
        }

        return fileLists.get(Arrays.asList(destination, remotePath));
    }

    private void notifyFileListListeners(CfdpIncomingTransfer incomingTransfer) {
        CfdpTransactionId originatingTransactionId = incomingTransfer.getOriginatingTransactionId();
        DirectoryListingRequest request = directoryListingRequests.remove(originatingTransactionId);
        fileDownloadRequests.removeTransfer(originatingTransactionId);
        if(request == null) {
            eventProducer.sendWarning("Received CFDP Directory Listing Response but with no matching Directory Listing Request");
            return;
        }

        if(incomingTransfer.getDirectoryListingResponse().getListingResponseCode() != ListingResponseCode.SUCCESSFUL) {
            eventProducer.sendWarning("Directory Listing Response was " + incomingTransfer.getDirectoryListingResponse().getListingResponseCode() + ". Associated request: " + request);
            return;
        }

        EntityConf remoteEntity = remoteEntities.values().stream().filter(entity -> entity.id == incomingTransfer.cfdpTransactionId.getInitiatorEntity()).findFirst().orElse(null);
        if (remoteEntity == null) {
            eventProducer.sendWarning("Directory Listing Response coming from an unknown remote entity: id=" + incomingTransfer.cfdpTransactionId.getInitiatorEntity());
            return;
        }

        List<RemoteFile> files;

        String ROOT = "/"; // TODO param
        String parser = "DEFAULT"; // TODO: param


        String remotePath = request.getDirectoryName().startsWith(ROOT) ? request.getDirectoryName().split(ROOT, 2)[1] : request.getDirectoryName();

        if(parser.equals("DEFAULT")) {
            files = Arrays.stream(new String(incomingTransfer.getFileData()).replace("\r", "").split("\\n")).map(
                    fileName -> {
                        if(fileName.startsWith(remotePath)) {
                            fileName = fileName.substring(remotePath.length());
                        } else if (fileName.startsWith(ROOT + remotePath)) {
                            fileName = fileName.substring((ROOT + remotePath).length());
                        }
                        String terminator = directoryTerminators.stream().filter(fileName::endsWith).max(Comparator.comparingInt(String::length)).orElse(null);
                        if(terminator != null) {
                            return RemoteFile.newBuilder().setName(fileName.substring(0, fileName.length() - terminator.length())).setSize(0).build();
                        }
                        return RemoteFile.newBuilder().setName(fileName).build();
                    }
            ).collect(Collectors.toList());
        } else {
            return;
        }

        files.sort((file1, file2) -> { // Sort by filename placing directories first
            int typeCmp = - Boolean.compare(file1.hasSize() && file1.getSize() == 0, file2.hasSize() && file2.getSize() == 0);
            return typeCmp != 0 ? typeCmp : file1.getName().compareToIgnoreCase(file2.getName());
        });

        ListFilesResponse listFilesResponse = ListFilesResponse.newBuilder()
                .addAllFiles(files)
                .setDestination(remoteEntity.getName())
                .setRemotePath(remotePath)
                .setListTime(TimestampUtil.currentTimestamp())
                .build();

        fileLists.put(Arrays.asList(remoteEntity.getName(), remotePath), listFilesResponse);

        log.debug("Notifying {} file list listeners with {} files for destination={} path={}", fileListListeners.size(), files.size(), remoteEntity.getName(), ROOT + remotePath);
        fileListListeners.forEach(l -> l.receivedFileList(listFilesResponse));
    }

    ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }

    public FaultHandlingAction getSenderFaultHandler(ConditionCode code) {
        return senderFaultHandlers.get(code);
    }

    public FaultHandlingAction getReceiverFaultHandler(ConditionCode code) {
        return receiverFaultHandlers.get(code);
    }

    /**
     * Called from unit tests to abort all transactions
     */
    void abortAll() {
        pendingTransfers.clear();
        queuedTransfers.clear();
    }
}
