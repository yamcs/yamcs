package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import static org.yamcs.tctm.pus.services.filetransfer.thirteen.CompletedTransfer.TDEF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Processor;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.cfdp.EntityConf;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.commanding.CommandingManager;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.FileDownloadRequests;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.FileTransferService;
import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.filetransfer.RemoteFileListMonitor;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.filetransfer.TransferOptions;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.FileTransferCapabilities;
import org.yamcs.protobuf.FileTransferOption;
import org.yamcs.protobuf.ListFilesResponse;
import org.yamcs.protobuf.TransferState;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.security.Directory;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer.FaultHandlingAction;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13FileTransfer.PredefinedTransferTypes;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.DownlinkS13Packet;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.DownlinkS13Packet.PacketType;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.CancelRequest;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.FilePutRequest;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.PauseRequest;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.PutRequest;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.ResumeRequest;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;


public class ServiceThirteen extends AbstractYamcsService
        implements FileTransferService, StreamSubscriber, TransferMonitor {
    static final String ETYPE_UNEXPECTED_S13_PACKET = "UNEXPECTED_S13_PACKET";
    static final String ETYPE_TRANSFER_STARTED = "TRANSFER_STARTED";
    static final String ETYPE_TRANSFER_FINISHED = "TRANSFER_FINISHED";
    static final String ETYPE_TRANSFER_SUSPENDED = "TRANSFER_SUSPENDED";
    static final String ETYPE_TRANSFER_RESUMED = "TRANSFER_RESUMED";
    static final String ETYPE_TRANSFER_PACKET_ERRROR = "TRANSFER_PACKET_ERROR";

    static final String BUCKET_OPT = "bucket";

    static final String TABLE_NAME = "s13";
    static final String SEQUENCE_NAME = "s13";

    // FileTransferOption name literals
    private final String OVERWRITE_OPTION = "overwrite";
    private final String RELIABLE_OPTION = "reliable";
    private final String CLOSURE_OPTION = "closureRequested";
    private final String CREATE_PATH_OPTION = "createPath";
    private final String PACKET_DELAY_OPTION = "packetDelay";
    private final String PACKET_SIZE_OPTION = "packetSize";

    private Stream dbStream;

    static final Map<String, ConditionCode> VALID_CODES = new HashMap<>();
    static final Map<Integer, String> FAILURE_CODES = new HashMap<>();

    static {
        VALID_CODES.put("CancelRequestReceived", ConditionCode.CANCEL_REQUEST_RECEIVED);
        VALID_CODES.put("FilestoreRejection", ConditionCode.FILESTORE_REJECTION);
        VALID_CODES.put("InactivityDetected", ConditionCode.INACTIVITY_DETECTED);
        VALID_CODES.put("FileChecksumFailure", ConditionCode.FILE_CHECKSUM_FAILURE);
        VALID_CODES.put("CheckLimitReached", ConditionCode.CHECK_LIMIT_REACHED);
        VALID_CODES.put("PreparedCommandNotFomed", ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
    }

    static {
        FAILURE_CODES.put(4, "FILESTORE_REJECTION_ONBOARD");
        FAILURE_CODES.put(5, "FILE_CHECKSUM_ERROR_ONBOARD");
        FAILURE_CODES.put(6, "FILE_SIZE_ERROR_ONBOARD");
        FAILURE_CODES.put(8, "INACTIVITY_DETECTED_ONBOARD");
        FAILURE_CODES.put(9, "INVALID_FILE_STRUCTURE_RECEIVED_ONBOARD");
    }

    Map<S13TransactionId, OngoingS13Transfer> pendingTransfers = new ConcurrentHashMap<>();
    FileDownloadRequests fileDownloadRequests = new FileDownloadRequests();

    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    Map<ConditionCode, FaultHandlingAction> receiverFaultHandlers;
    Map<ConditionCode, FaultHandlingAction> senderFaultHandlers;

    Stream s13In;

    Bucket defaultIncomingBucket;
    EventProducer eventProducer;

    static String spaceSystem;
    static Map<Long, String> spaceSubSystems = new HashMap<>();
    static Map<Long, String> contentTypeMap = new HashMap<>();
    static String origin;

    private Set<TransferMonitor> transferListeners = new CopyOnWriteArraySet<>();
    
    protected static Map<String, EntityConf> localEntities = new LinkedHashMap<>();
    protected static Map<String, EntityConf> remoteEntities = new LinkedHashMap<>();

    private int maxExistingFileRenames;

    int pendingAfterCompletion;
    int archiveRetrievalLimit;

    boolean allowConcurrentFileOverwrites;

    private boolean hasDownloadCapability;
    private boolean hasFileListingCapability;

    private static Processor processor;
    private static Directory directory;
    private static CommandingManager commandingManager;

    public static String commandReleaseUser;
    public static String startDownlinkCmdName;

    @Override
    public Spec getSpec() {
        Spec entitySpec = new Spec();
        entitySpec.addOption("name", OptionType.STRING);
        entitySpec.addOption("id", OptionType.INTEGER);
        entitySpec.addOption(BUCKET_OPT, OptionType.STRING).withDefault(null);
        entitySpec.addOption("subSystem", OptionType.STRING);
        entitySpec.addOption("contentType", OptionType.STRING);

        Spec spec = new Spec();
        spec.addOption("inStream", OptionType.STRING).withDefault("cfdp_in");
        spec.addOption("outStream", OptionType.STRING).withDefault("cfdp_out");
        spec.addOption("incomingBucket", OptionType.STRING).withDefault("cfdpDown");
        spec.addOption("maxExistingFileRenames", OptionType.INTEGER).withDefault(1000);
        spec.addOption("localEntities", OptionType.LIST).withElementType(OptionType.MAP).withSpec(entitySpec);
        spec.addOption("remoteEntities", OptionType.LIST).withElementType(OptionType.MAP).withSpec(entitySpec);
        spec.addOption("archiveRetrievalLimit", OptionType.INTEGER).withDefault(100);
        spec.addOption("receiverFaultHandlers", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("senderFaultHandlers", OptionType.MAP).withSpec(Spec.ANY);
        spec.addOption("allowConcurrentFileOverwrites", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("pendingAfterCompletion", OptionType.INTEGER).withDefault(600000);
        spec.addOption("hasDownloadCapability", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("spaceSystem", OptionType.STRING).withDefault("FF");
        spec.addOption("commandReleaseUser", OptionType.STRING).withDefault("administrator");
        spec.addOption("startDownlinkCmdName", OptionType.STRING).withDefault("StartLargePacketDownload");

        spec.addOption("checkAckTimeout", OptionType.INTEGER).withDefault(10000l);
        spec.addOption("checkAckLimit", OptionType.INTEGER).withDefault(5);
        spec.addOption("inactivityTimeout", OptionType.INTEGER).withDefault(10000);

        spec.addOption("maxPacketSize", OptionType.INTEGER).withDefault(512);
        spec.addOption("sleepBetweenPackets", OptionType.INTEGER).withDefault(500);
        spec.addOption("useCop1Bypass", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("firstPacketCmdName", OptionType.STRING).withDefault("FirstUplinkPart");
        spec.addOption("intermediatePacketCmdName", OptionType.STRING).withDefault("IntermediateUplinkPart");
        spec.addOption("lastPacketCmdName", OptionType.STRING).withDefault("LastUplinkPart");

        spec.addOption("hasFileListingCapability", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        String inStream = config.getString("inStream");
        String outStream = config.getString("outStream");

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        s13In = ydb.getStream(inStream);
        if (s13In == null) {
            throw new ConfigurationException("cannot find stream " + inStream);
        }

        defaultIncomingBucket = getBucket(config.getString("incomingBucket"), true);
        archiveRetrievalLimit = config.getInt("archiveRetrievalLimit", 100);
        pendingAfterCompletion = config.getInt("pendingAfterCompletion", 600000);
        allowConcurrentFileOverwrites = config.getBoolean("allowConcurrentFileOverwrites");
        hasDownloadCapability = config.getBoolean("hasDownloadCapability");
        hasFileListingCapability = config.getBoolean("hasFileListingCapability", false);
        spaceSystem = config.getString("spaceSystem");
        maxExistingFileRenames = config.getInt("maxExistingFileRenames", 1000);
        commandReleaseUser = config.getString("", "administrator");
        startDownlinkCmdName = config.getString("startDownlinkCmdName", "StartLargePacketDownload");

        initSrcDst(config);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, "PusService-13", 10000);
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

        processor = YamcsServer.getServer().getInstance(yamcsInstance).getProcessor("realtime");
        directory = YamcsServer.getServer().getSecurityStore().getDirectory();
        commandingManager = processor.getCommandingManager();
    }

    public static Processor getProcessor() {
        return processor;
    }

    public static Directory getUserDirectory() {
        return directory;
    }

    public static CommandingManager getCommandingManager() {
        return commandingManager;
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

    private void initSrcDst(YConfiguration config) throws InitException {
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
                
                String subSystem = c.getString("subSystem");
                spaceSubSystems.put(id, subSystem);

                String contentType = c.getString("contentType");
                contentTypeMap.put(id, contentType);

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

    public OngoingS13Transfer getS13Transfer(S13TransactionId transferId) {
        return pendingTransfers.get(transferId);
    }

    public static String getCmdSubsystem(long remoteEntityId) {
        return "/" + spaceSystem + "/" + spaceSubSystems.get(remoteEntityId);

    }

    public static String constructFullyQualifiedCmdName(String cmdName, long remoteEntityId) {
        return ServiceThirteen.getCmdSubsystem(remoteEntityId) + "/" + cmdName;
    }

    @Override
    public FileTransfer getFileTransfer(long id) {
        Optional<OngoingS13Transfer> r = pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny();
        if (r.isPresent()) {
            return r.get();

        } else {
            return searchInArchive(id);
        }
    }

    public FileTransfer getOngoingUploadFileTransfer(long largePacketTransactionId) {
        Optional<OngoingS13Transfer> r = pendingTransfers.values().stream()
                .filter(c -> c.getTransactionId().getLargePacketTransactionId() == largePacketTransactionId)
                .filter(c -> c.getTransactionId().getTransferDirection() == TransferDirection.UPLOAD).findAny();
        if (r.isPresent()) {
            return r.get();
        }
        return null;
    }

    public FileTransfer getOngoingDownloadFileTransfer(long largePacketTransactionId) {
        Optional<OngoingS13Transfer> r = pendingTransfers.values().stream()
                .filter(c -> c.getTransactionId().getLargePacketTransactionId() == largePacketTransactionId)
                .filter(c -> c.getTransactionId().getTransferDirection() == TransferDirection.DOWNLOAD).findAny();
        if (r.isPresent()) {
            return r.get();
        }
        return null;
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
        pendingTransfers.values().stream().filter(ServiceThirteen::isRunning).forEach(toReturn::add);

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

    private S13FileTransfer processPutRequest(long initiatorEntityId, long transferId, long largePacketTransactionId, long creationTime,
            PutRequest request, Bucket bucket, String transferType, Integer customPacketSize, Integer customPacketDelay) {
        S13OutgoingTransfer transfer = new S13OutgoingTransfer(yamcsInstance, initiatorEntityId, transferId, largePacketTransactionId, creationTime,
                executor, request, config, bucket, customPacketSize, customPacketDelay, eventProducer, this, transferType, senderFaultHandlers);

        dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));

        stateChanged(transfer);
        pendingTransfers.put(transfer.getTransactionId(), transfer);

        if(request.getFileLength() > 0) {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new S13 upload TXID[" + transfer.getTransactionId() + "] " + transfer.getObjectName()
                            + " -> " + transfer.getRemotePath());
        } else {
            String remoteEntityName = getEntityFromId(largePacketTransactionId, remoteEntities).getName();
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new S13 upload TXID[" + transfer.getTransactionId() + "] \n"
                            + "Fileless transfer: \n"
                            + "     Remote Source Entity Line: " + remoteEntityName + "\n"
                            + "     Large Packet Transaction ID: " + largePacketTransactionId + "\n");
        }

        transfer.start();
        return transfer;
    }

    static boolean isRunning(OngoingS13Transfer trsf) {
        return trsf.state == TransferState.RUNNING || trsf.state == TransferState.PAUSED
                || trsf.state == TransferState.CANCELLING;
    }

    private OngoingS13Transfer processPauseRequest(PauseRequest request) {
        OngoingS13Transfer transfer = request.getTransfer();
        transfer.pauseTransfer();
        return transfer;
    }

    private OngoingS13Transfer processResumeRequest(ResumeRequest request) {
        OngoingS13Transfer transfer = request.getTransfer();
        transfer.resumeTransfer();
        return transfer;
    }

    private OngoingS13Transfer processCancelRequest(CancelRequest request) {
        OngoingS13Transfer transfer = request.getTransfer();
        transfer.cancelTransfer();
        return transfer;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        DownlinkS13Packet packet = DownlinkS13Packet.fromTuple(tuple);

        // Check if it is an Uplink abortion packet
        if (packet.getPacketType() == PacketType.ABORTION) {
            FileTransfer filetransfer = getOngoingUploadFileTransfer(packet.getTransactionId().getTransferId());
            if (filetransfer != null) {
                S13OutgoingTransfer outgoingTransfer = (S13OutgoingTransfer) filetransfer;
                outgoingTransfer.cancel(ConditionCode.readConditionCode((byte) packet.getFailureCode().byteValue()));

            } else {
                log.warn("Erroneous Uplink abortion request received");
            }
            return;
        }

        S13TransactionId id = packet.getTransactionId();

        OngoingS13Transfer transfer = null;
        if (pendingTransfers.containsKey(id)) {
            transfer = pendingTransfers.get(id);

        } else {
            // the communication partner has initiated a transfer
            transfer = instantiateIncomingTransaction(packet);
            if (transfer != null) {
                pendingTransfers.put(transfer.getTransactionId(), transfer);
                OngoingS13Transfer t1 = transfer;
                executor.submit(() -> dbStream.emitTuple(CompletedTransfer.toInitialTuple(t1)));
            }
        }

        if (transfer != null) {
            transfer.processPacket(packet);
        }
    }

    private OngoingS13Transfer instantiateIncomingTransaction(DownlinkS13Packet packet) {
        S13TransactionId txId = packet.getTransactionId();

        EntityConf remoteEntity = getRemoteEntity(txId.getInitiatorEntityId());
        if (remoteEntity == null) {
            eventProducer.sendWarning(ETYPE_UNEXPECTED_S13_PACKET,
                    "Received a transaction start for an unknown remote entity Id " + txId.getInitiatorEntityId());
            return null;
        }

        EntityConf localEntity = getLocalEntity(txId.getInitiatorEntityId());
        if (localEntity == null) {
            eventProducer.sendWarning(ETYPE_UNEXPECTED_S13_PACKET,
                    "Received a transaction start for an unknown local entity Id "
                            + txId.getInitiatorEntityId());
            return null;
        }

        eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                "Starting new CFDP downlink TXID[" + txId + "] " + remoteEntity + " -> " + localEntity);

        Bucket bucket = defaultIncomingBucket;

        if (localEntity.getBucket() != null) {
            bucket = localEntity.getBucket();
        } else if (remoteEntity.getBucket() != null) {
            bucket = remoteEntity.getBucket();
        }

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();
        final FileSaveHandler fileSaveHandler = new FileSaveHandler(yamcsInstance, bucket, fileDownloadRequests,
                false, false, false, maxExistingFileRenames);

        return new S13IncomingTransfer(yamcsInstance, txId.getLargePacketTransactionId(), creationTime, executor, config,
                packet.getTransactionId(), packet.getTransactionId().getInitiatorEntityId(),
                remoteEntity.getName(), fileSaveHandler, eventProducer, this, PredefinedTransferTypes.DOWNLOAD_LARGE_FILE_TRANSFER.toString(),
                contentTypeMap.get(remoteEntity.getId()), receiverFaultHandlers);
    }

    public EntityConf getRemoteEntity(long entityId) {
        return remoteEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().getId() == entityId)
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);
    }

    public EntityConf getLocalEntity(long entityId) {
        return localEntities.entrySet()
                .stream()
                .filter(me -> me.getValue().getId() == entityId)
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
    public void registerRemoteFileListMonitor(RemoteFileListMonitor monitor) {
        return;
    }

    @Override
    public void unregisterRemoteFileListMonitor(RemoteFileListMonitor monitor) {
        return;
    }

    @Override
    public void notifyRemoteFileListMonitors(ListFilesResponse listFilesResponse) {
        return;
    }

    @Override
    public Set<RemoteFileListMonitor> getRemoteFileListMonitors() {
        return null;
    }

    @Override
    public void stateChanged(FileTransfer ft) {
        S13FileTransfer cfdpTransfer = (S13FileTransfer) ft;
        dbStream.emitTuple(CompletedTransfer.toUpdateTuple(cfdpTransfer));

        // Notify downstream listeners
        transferListeners.forEach(l -> l.stateChanged(cfdpTransfer));

        if (cfdpTransfer.getTransferState() == TransferState.COMPLETED
                || cfdpTransfer.getTransferState() == TransferState.FAILED) {

            if (cfdpTransfer instanceof OngoingS13Transfer) {
                // keep it in pending for a while such that PDUs from remote entity can still be answered
                executor.schedule(() -> pendingTransfers.remove(cfdpTransfer.getTransactionId()),
                        pendingAfterCompletion, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public List<EntityInfo> getLocalEntities() {
        return localEntities.values().stream()
                .map(c -> EntityInfo.newBuilder().setName(c.getName()).setId(c.getId()).build())
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityInfo> getRemoteEntities() {
        return remoteEntities.values().stream()
                .map(c -> EntityInfo.newBuilder().setName(c.getName()).setId(c.getId()).build())
                .collect(Collectors.toList());
    }

    public OngoingS13Transfer getOngoingCfdpTransfer(long id) {
        return pendingTransfers.values().stream().filter(c -> c.getId() == id).findAny().orElse(null);
    }

    @Override
    public List<FileTransferOption> getFileTransferOptions() {
        var options = new ArrayList<FileTransferOption>();
        options.add(FileTransferOption.newBuilder()
                .setName(RELIABLE_OPTION)
                .setType(FileTransferOption.Type.BOOLEAN)
                .setTitle("Reliability")
                .setDescription("Acknowledged or unacknowledged transmission mode")
                .setAssociatedText("Reliable")
                .setDefault("false")
                .build());

        return options;
    }

    @Override
    public FileTransferCapabilities getCapabilities() {
        return FileTransferCapabilities
                .newBuilder()
                .setDownload(hasDownloadCapability)
                .setUpload(true)
                .setReliability(false) // Reliability DEPRECATED: use FileTransferOption
                .setRemotePath(false)
                .setFileList(hasFileListingCapability)
                .setHasTransferType(false)
                .build();
    }

    private static class OptionValues {
        HashMap<String, Boolean> booleanOptions = new HashMap<>();
        HashMap<String, Double> doubleOptions = new HashMap<>();
    }

    private OptionValues getOptionValues(Map<String, Object> extraOptions) {
        var optionValues = new OptionValues();

        for (Map.Entry<String, Object> option : extraOptions.entrySet()) {
            try {
                switch (option.getKey()) {
                    case OVERWRITE_OPTION:
                    case RELIABLE_OPTION:
                    case CLOSURE_OPTION:
                    case CREATE_PATH_OPTION:
                        optionValues.booleanOptions.put(option.getKey(), (boolean) option.getValue());
                        break;
                    case PACKET_DELAY_OPTION:
                    case PACKET_SIZE_OPTION:
                        optionValues.doubleOptions.put(option.getKey(), (double) option.getValue());
                        break;
                    default:
                        log.warn("Unknown file transfer option: {} (value: {})", option.getKey(), option.getValue());
                }
            } catch (ClassCastException e) {
                log.warn("Failed to cast option '{}' to its correct type (value: {})", option.getKey(),
                        option.getValue());
            }
        }

        return optionValues;
    }

    private String getAbsoluteDestinationPath(String destinationPath, String localObjectName) {
        if (localObjectName == null) {
            throw new NullPointerException("local object name cannot be null");
        }
        if (destinationPath == null) {
            return localObjectName;
        }
        return destinationPath;
    }

    @Override
    public FileTransfer startUpload(String source, Bucket bucket, String objectName, String destination,
            String destinationPath, TransferOptions options) throws IOException {
        byte[] objData;
        objData = bucket.getObject(objectName);

        if (objData == null) {
            throw new InvalidRequestException("No object named '" + objectName + "' in bucket " + bucket.getName());
        }

        long sourceId = getEntityFromName(source, localEntities).getId();
        long destinationId = getEntityFromName(destination, remoteEntities).getId();

        String absoluteDestinationPath = getAbsoluteDestinationPath(destinationPath, objectName);
        if (!allowConcurrentFileOverwrites) {
            if (pendingTransfers.values().stream()
                    .filter(ServiceThirteen::isRunning)
                    .anyMatch(trsf -> getEntityFromId(trsf.getTransactionId()
                            .getLargePacketTransactionId(), remoteEntities).getId() == destinationId)) {
                throw new InvalidRequestException(
                        "There is already a transfer ongoing to '" + getEntityFromName(destination, remoteEntities).getId() + " - " + getEntityFromName(
                                destination, remoteEntities).getName() + "'");
            }
        }

        // For backwards compatibility
        var booleanOptions = new HashMap<>(Map.of(
            CREATE_PATH_OPTION, options.isCreatePath()
        ));

        OptionValues optionValues = getOptionValues(options.getExtraOptions());
        booleanOptions.putAll(optionValues.booleanOptions);

        FilePutRequest request = new FilePutRequest(sourceId, destinationId, objectName, absoluteDestinationPath, booleanOptions.get(CREATE_PATH_OPTION), bucket, objData);
        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        Double packetSize = optionValues.doubleOptions.get(PACKET_SIZE_OPTION);
        Double pduDelay = optionValues.doubleOptions.get(PACKET_DELAY_OPTION);

        return processPutRequest(sourceId, destinationId, destinationId, creationTime, request, bucket, PredefinedTransferTypes.UPLOAD_LARGE_FILE_TRANSFER.toString(),
                packetSize != null ? packetSize.intValue() : null, pduDelay != null ? pduDelay.intValue() : null);
    }

    @Override
    public FileTransfer startDownload(String sourceEntity, String sourcePath, String destinationEntity, Bucket bucket,
            String objectName, TransferOptions options) throws IOException, InvalidRequestException {
        if(!hasDownloadCapability) {
            throw new InvalidRequestException("Downloading is not enabled on this CFDP service");
        }

        long destinationId = getEntityFromName(destinationEntity, localEntities).getId();
        long sourceId = getEntityFromName(sourceEntity, remoteEntities).getId();

        // For backwards compatibility
        var booleanOptions = new HashMap<>(Map.of(
                OVERWRITE_OPTION, options.isOverwrite(),
                RELIABLE_OPTION, options.isReliable(),
                CLOSURE_OPTION, options.isClosureRequested(),
                CREATE_PATH_OPTION, options.isCreatePath()
        ));

        OptionValues optionValues = getOptionValues(options.getExtraOptions());

        booleanOptions.putAll(optionValues.booleanOptions);

        Double packetSize = optionValues.doubleOptions.get(PACKET_SIZE_OPTION);
        Double packetDelay = optionValues.doubleOptions.get(PACKET_DELAY_OPTION);

        PutRequest request = new PutRequest(sourceId);
        S13TransactionId transactionId = request.process(sourceId, sourceId, sourceId, config);

        if (getOngoingUploadFileTransfer(transactionId.getLargePacketTransactionId()) != null) {
            throw new InvalidRequestException("Downloading and Uploading from the same largePacketTransactionId: " + transactionId.getLargePacketTransactionId() + " is not allowed");
        }

        if (getOngoingDownloadFileTransfer(transactionId.getLargePacketTransactionId()) != null) {
            throw new InvalidRequestException("Downloading for the largePacketTransactionId: " + transactionId.getLargePacketTransactionId() + " is already underway. Simultaneous downloads of the same largePacketTransactionId not permitted");
        }

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        fileDownloadRequests.addTransfer(transactionId, bucket.getName());
        return processPutRequest(destinationId, transactionId.getTransferId(), sourceId, creationTime, request, bucket, PredefinedTransferTypes.DOWNLOAD_REQUEST.toString(),
                packetSize != null ? packetSize.intValue() : null, packetDelay != null ? packetDelay.intValue() : null);
    }

    @Override
    public ListFilesResponse getFileList(String source, String destination, String remotePath, Map<String, Object> options) {
        throw new InvalidRequestException("File listing is not enabled on this S13 service");
    }

    protected static EntityConf getEntityFromId(long entityId, Map<String, EntityConf> entities) {
        for(Map.Entry<String, EntityConf> entityMap: entities.entrySet()) {
            EntityConf entity = entityMap.getValue();

            if (entity.getId() == entityId)
                return entity;
        }
        return null;
    }

    protected EntityConf getEntityFromName(String entityName, Map<String, EntityConf> entities) {
        if (entityName == null || entityName.isBlank()) {
            return entities.values().iterator().next();
        } else {
            if (!entities.containsKey(entityName)) {
                throw new InvalidRequestException(
                        "Invalid entity '" + entityName + "' (should be one of " + entities + "");
            }
            return entities.get(entityName);
        }
    }

    @Override
    public void fetchFileList(String source, String destination, String remotePath, Map<String, Object> options) {
        throw new InvalidRequestException("File listing is not enabled on this S13 service");
    }

    @Override
    public void saveFileList(ListFilesResponse listFilesResponse) {
        return;
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
        if (transfer instanceof OngoingS13Transfer) {
            processCancelRequest(new CancelRequest(transfer));
        } else {
            throw new InvalidRequestException("Unknown transfer type " + transfer);
        }
    }

    @Override
    protected void doStart() {
        s13In.addSubscriber(this);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (OngoingS13Transfer trsf : pendingTransfers.values()) {
            if (trsf.state == TransferState.RUNNING || trsf.state == TransferState.PAUSED) {
                trsf.failTransfer("service shutdown");
            }
        }
        executor.shutdown();
        s13In.removeSubscriber(this);
        notifyStopped();
    }

    @Override
    public void streamClosed(Stream stream) {
        if (isRunning()) {
            log.debug("Stream {} closed", stream.getName());
            notifyFailed(new Exception("Stream " + stream.getName() + " cloased"));
        }
    }


}
