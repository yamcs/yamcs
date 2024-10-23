package org.yamcs.cfdp;

import static org.yamcs.cfdp.CompletedTransfer.COL_CREATION_TIME;
import static org.yamcs.cfdp.CompletedTransfer.COL_DIRECTION;
import static org.yamcs.cfdp.CompletedTransfer.COL_TRANSFER_STATE;
import static org.yamcs.cfdp.CompletedTransfer.TDEF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.OngoingCfdpTransfer.FaultHandlingAction;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.DirectoryListingRequest;
import org.yamcs.cfdp.pdu.DirectoryListingResponse.ListingResponseCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.MessageToUser;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.PduDecodingException;
import org.yamcs.cfdp.pdu.ProxyClosureRequest;
import org.yamcs.cfdp.pdu.ProxyPutRequest;
import org.yamcs.cfdp.pdu.ProxyTransmissionMode;
import org.yamcs.cfdp.pdu.TLV;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.filetransfer.AbstractFileTransferService;
import org.yamcs.filetransfer.BasicListingParser;
import org.yamcs.filetransfer.FileListingParser;
import org.yamcs.filetransfer.FileListingService;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.filetransfer.FileTransferFilter;
import org.yamcs.filetransfer.InvalidRequestException;
import org.yamcs.filetransfer.RemoteFileListMonitor;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.filetransfer.TransferOptions;
import org.yamcs.protobuf.EntityInfo;
import org.yamcs.protobuf.FileTransferCapabilities;
import org.yamcs.protobuf.FileTransferOption;
import org.yamcs.protobuf.ListFilesResponse;
import org.yamcs.protobuf.RemoteFile;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import com.google.common.collect.Streams;

/**
 * Implements CCSDS File Delivery Protocol (CFDP) in Yamcs.
 * <p>
 * The standard is specified in <a href="https://public.ccsds.org/Pubs/727x0b5.pdf"> CCSDS 727.0-B-5 </a>
 * 
 * @author nm
 *
 */
public class CfdpService extends AbstractFileTransferService implements StreamSubscriber, TransferMonitor {

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

    // FileTransferOption name literals
    private final String OVERWRITE_OPTION = "overwrite";
    private final String RELIABLE_OPTION = "reliable";
    private final String CLOSURE_OPTION = "closureRequested";
    private final String CREATE_PATH_OPTION = "createPath";
    private final String PDU_DELAY_OPTION = "pduDelay";
    private final String PDU_SIZE_OPTION = "pduSize";

    Map<CfdpTransactionId, OngoingCfdpTransfer> pendingTransfers = new ConcurrentHashMap<>();
    Queue<QueuedCfdpOutgoingTransfer> queuedTransfers = new ConcurrentLinkedQueue<>();

    FileDownloadRequests fileDownloadRequests = new FileDownloadRequests();
    Map<CfdpTransactionId, List<String>> directoryListingRequests = new ConcurrentHashMap<>();

    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    Map<ConditionCode, FaultHandlingAction> receiverFaultHandlers;
    Map<ConditionCode, FaultHandlingAction> senderFaultHandlers;
    Stream cfdpIn;
    Stream cfdpOut;
    Bucket defaultIncomingBucket;

    EventProducer eventProducer;

    private Set<TransferMonitor> transferListeners = new CopyOnWriteArraySet<>();
    private Set<RemoteFileListMonitor> remoteFileListMonitors = new CopyOnWriteArraySet<>();
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
    private boolean hasDownloadCapability;

    private boolean hasFileListingCapability;
    private FileListingService fileListingService;
    private FileListingParser fileListingParser;
    private boolean automaticDirectoryListingReloads;

    private boolean canChangePduSize;
    private List<Integer> pduSizePredefinedValues;
    private boolean canChangePduDelay;
    private List<Integer> pduDelayPredefinedValues;

    private Stream dbStream;

    private Stream fileListStream;

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

    String FILELIST_TABLE_NAME = "cfdp_filelist";
    static TupleDefinition FILELIST_TDEF = new TupleDefinition();
    static String COL_DESTINATION = "destination";
    static String COL_REMOTE_PATH = "remotePath";
    static String COL_LIST_TIME = "listTime";
    static String COL_LIST_FILES_RESPONSE = "listFilesResponse";

    static {
        FILELIST_TDEF.addColumn(COL_LIST_TIME, DataType.TIMESTAMP);
        FILELIST_TDEF.addColumn(COL_DESTINATION, DataType.STRING);
        FILELIST_TDEF.addColumn(COL_REMOTE_PATH, DataType.STRING);
        FILELIST_TDEF.addColumn(COL_LIST_FILES_RESPONSE, DataType.protobuf("org.yamcs.protobuf.ListFilesResponse"));
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
        spec.addOption("canChangePduSize", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("pduSizePredefinedValues", OptionType.LIST).withDefault(Collections.emptyList())
                .withElementType(OptionType.INTEGER);
        spec.addOption("canChangePduDelay", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("pduDelayPredefinedValues", OptionType.LIST).withDefault(Collections.emptyList())
                .withElementType(OptionType.INTEGER);
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
        spec.addOption("hasDownloadCapability", OptionType.BOOLEAN).withDefault(true);

        spec.addOption("hasFileListingCapability", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("fileListingServiceClassName", OptionType.STRING).withDefault("org.yamcs.cfdp.CfdpService");
        spec.addOption("fileListingServiceArgs", OptionType.MAP).withSpec(Spec.ANY)
                .withDefault(new HashMap<>());
        spec.addOption("fileListingParserClassName", OptionType.STRING)
                .withDefault("org.yamcs.filetransfer.BasicListingParser");
        spec.addOption("fileListingParserArgs", OptionType.MAP).withSpec(Spec.ANY)
                .withDefault(new HashMap<>());
        spec.addOption("automaticDirectoryListingReloads", OptionType.BOOLEAN).withDefault(false);

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
        canChangePduSize = config.getBoolean("canChangePduSize");
        pduSizePredefinedValues = config.getList("pduSizePredefinedValues");
        canChangePduDelay = config.getBoolean("canChangePduDelay");
        pduDelayPredefinedValues = config.getList("pduDelayPredefinedValues");
        hasDownloadCapability = config.getBoolean("hasDownloadCapability");
        hasFileListingCapability = config.getBoolean("hasFileListingCapability");

        String fileListingServiceClassName = config.getString("fileListingServiceClassName");
        YConfiguration fileListingServiceConfig = config.getConfig("fileListingServiceArgs");
        if (Objects.equals(fileListingServiceClassName, this.getClass().getName())) {
            fileListingService = this;

            String fileListingParserClassName;
            try {
                fileListingParserClassName = fileListingServiceConfig.getString("fileListingParserClassName");
            } catch (ConfigurationException e) {
                fileListingParserClassName = config.getString("fileListingParserClassName");
            }
            fileListingParser = YObjectLoader.loadObject(fileListingParserClassName);
            if (fileListingParser instanceof BasicListingParser) {
                // directoryTerminators will be overwritten by the specific fileListingParserArgs if existing
                ((BasicListingParser) fileListingParser).setDirectoryTerminators(directoryTerminators);
            }

            try {
                Spec spec = fileListingParser.getSpec();
                YConfiguration fileListingParserConfig;
                try {
                    fileListingParserConfig = fileListingServiceConfig.getConfig("fileListingParserArgs");
                } catch (ConfigurationException e) {
                    fileListingParserConfig = config.getConfig("fileListingParserArgs");
                }
                fileListingParser.init(yamcsInstance,
                        spec != null ? spec.validate(fileListingParserConfig) : fileListingParserConfig);
            } catch (ValidationException e) {
                throw new InitException("Failed to validate FileListingParser config", e);
            }
        } else {
            fileListingService = YObjectLoader.loadObject(fileListingServiceClassName);
            fileListingService.init(yamcsInstance, serviceName + "_" + fileListingServiceClassName,
                    fileListingServiceConfig);
        }

        automaticDirectoryListingReloads = config.getBoolean("automaticDirectoryListingReloads");

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
        setupFileListTable(ydb);
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

    private void setupFileListTable(YarchDatabaseInstance ydb) throws InitException {
        try {
            if (ydb.getTable(FILELIST_TABLE_NAME) == null) {
                String query = "create table " + FILELIST_TABLE_NAME + "(" + FILELIST_TDEF.getStringDefinition1()
                        + ", primary key(" + COL_LIST_TIME + ", " + COL_DESTINATION + ", " + COL_REMOTE_PATH + "))";
                ydb.execute(query);
            }
            String streamName = FILELIST_TABLE_NAME + "_stream";
            if (ydb.getStream(streamName) == null) {
                ydb.execute("create stream " + streamName + FILELIST_TDEF.getStringDefinition());
            }
            ydb.execute("upsert_append into " + FILELIST_TABLE_NAME + " select * from " + streamName);
            fileListStream = ydb.getStream(streamName);
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
    public List<FileTransfer> getTransfers(FileTransferFilter filter) {
        List<FileTransfer> toReturn = new ArrayList<>();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        pendingTransfers.values().stream()
                .filter(CfdpService::isRunning)
                .forEach(toReturn::add);
        toReturn.addAll(queuedTransfers);

        toReturn.removeIf(transfer -> {
            if (filter.start != TimeEncoding.INVALID_INSTANT) {
                if (transfer.getCreationTime() < filter.start) {
                    return true;
                }
            }
            if (filter.stop != TimeEncoding.INVALID_INSTANT) {
                if (transfer.getCreationTime() >= filter.stop) {
                    return true;
                }
            }
            if (!filter.states.isEmpty() && !filter.states.contains(transfer.getTransferState())) {
                return true;
            }
            if (filter.direction != null && !Objects.equals(filter.direction, transfer.getDirection())) {
                return true;
            }
            if (filter.localEntityId != null && !Objects.equals(filter.localEntityId, transfer.getLocalEntityId())) {
                return true;
            }
            if (filter.remoteEntityId != null && !Objects.equals(filter.remoteEntityId, transfer.getRemoteEntityId())) {
                return true;
            }

            return false;
        });

        if (toReturn.size() >= filter.limit) {
            return toReturn;
        }

        // Query only for COMPLETED or FAILED, while respecting the incoming requested states
        // (want to avoid duplicates with the in-memory data structure)
        if (filter.states.isEmpty() || filter.states.contains(TransferState.COMPLETED)
                || filter.states.contains(TransferState.FAILED)) {

            var sqlb = new SqlBuilder(TABLE_NAME);

            if (filter.start != TimeEncoding.INVALID_INSTANT) {
                sqlb.whereColAfterOrEqual(COL_CREATION_TIME, filter.start);
            }
            if (filter.stop != TimeEncoding.INVALID_INSTANT) {
                sqlb.whereColBefore(COL_CREATION_TIME, filter.stop);
            }

            if (filter.states.isEmpty()) {
                sqlb.whereColIn(COL_TRANSFER_STATE,
                        Arrays.asList(TransferState.COMPLETED.name(), TransferState.FAILED.name()));
            } else {
                var queryStates = new ArrayList<>(filter.states);
                queryStates.removeIf(state -> {
                    return state != TransferState.COMPLETED && state != TransferState.FAILED;
                });

                var stringStates = queryStates.stream().map(TransferState::name).toList();
                sqlb.whereColIn(COL_TRANSFER_STATE, stringStates);

            }
            if (filter.direction != null) {
                sqlb.where(COL_DIRECTION + " = ?", filter.direction.name());
            }
            if (filter.localEntityId != null) {
                // The 1=1 clause is a trick because Yarch is being difficult about multiple lparens
                sqlb.where("""
                        (1=1 and
                          (direction = 'UPLOAD' and sourceId = ?) or
                          (direction = 'DOWNLOAD' and destinationId = ?)
                        )
                        """, filter.localEntityId, filter.localEntityId);
            }
            if (filter.remoteEntityId != null) {
                // The 1=1 clause is a trick because Yarch is being difficult about multiple lparens
                sqlb.where("""
                        (1=1 and
                          (direction = 'UPLOAD' and destinationId = ?) or
                          (direction = 'DOWNLOAD' and sourceId = ?)
                        )
                        """, filter.remoteEntityId, filter.remoteEntityId);
            }

            sqlb.descend(filter.descending);
            sqlb.limit(filter.limit - toReturn.size());

            try {
                var res = ydb.execute(sqlb.toString(), sqlb.getQueryArgumentsArray());
                while (res.hasNext()) {
                    Tuple t = res.next();
                    toReturn.add(new CompletedTransfer(t));
                }
                res.close();
            } catch (ParseException | StreamSqlException e) {
                log.error("Error executing query", e);
            }
        }

        Collections.sort(toReturn, (a, b) -> {
            var rc = Long.compare(a.getCreationTime(), b.getCreationTime());
            return filter.descending ? -rc : rc;
        });
        return toReturn;
    }

    private CfdpFileTransfer processPutRequest(long initiatorEntityId, long seqNum, long creationTime,
            PutRequest request,
            Bucket bucket, Integer customPduSize, Integer customPduDelay) {
        CfdpOutgoingTransfer transfer = new CfdpOutgoingTransfer(yamcsInstance, initiatorEntityId, seqNum, creationTime,
                executor, request, cfdpOut, config, bucket, customPduSize, customPduDelay, eventProducer, this,
                senderFaultHandlers);

        dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));

        stateChanged(transfer);
        pendingTransfers.put(transfer.getTransactionId(), transfer);

        if (request.getFileLength() > 0) {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP upload TXID[" + transfer.getTransactionId() + "] " + transfer.getObjectName()
                            + " -> " + transfer.getRemotePath());
        } else {
            eventProducer.sendInfo(ETYPE_TRANSFER_STARTED,
                    "Starting new CFDP upload TXID[" + transfer.getTransactionId()
                            + "] Fileless transfer (metadata options: \n"
                            + (request.getMetadata() != null ? request.getMetadata().getOptions().stream()
                                    .map(TLV::toString).collect(Collectors.joining(",\n")) : "")
                            + "\n)");
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
            processPutRequest(trsf.getInitiatorEntityId(), trsf.getId(), trsf.getCreationTime(), trsf.getPutRequest(),
                    trsf.getBucket(), trsf.getCustomPduSize(), trsf.getCustomPduDelay());
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
                allowRemoteProvidedBucket, allowRemoteProvidedSubdirectory, allowDownloadOverwrites,
                maxExistingFileRenames);

        return new CfdpIncomingTransfer(yamcsInstance, idSeq.next(), creationTime, executor, config, packet.getHeader(),
                cfdpOut, fileSaveHandler, eventProducer, this, receiverFaultHandlers);
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
    public void registerRemoteFileListMonitor(RemoteFileListMonitor monitor) {
        if (fileListingService != this) {
            fileListingService.registerRemoteFileListMonitor(monitor);
            return;
        }
        log.debug("Registering file list monitor");
        remoteFileListMonitors.add(monitor);
    }

    @Override
    public void unregisterRemoteFileListMonitor(RemoteFileListMonitor monitor) {
        if (fileListingService != this) {
            fileListingService.unregisterRemoteFileListMonitor(monitor);
            return;
        }
        log.debug("Un-registering file list monitor");
        remoteFileListMonitors.remove(monitor);
    }

    @Override
    public void notifyRemoteFileListMonitors(ListFilesResponse listFilesResponse) {
        if (fileListingService != this) {
            fileListingService.notifyRemoteFileListMonitors(listFilesResponse);
            return;
        }
        remoteFileListMonitors.forEach(l -> l.receivedFileList(listFilesResponse));
    }

    @Override
    public Set<RemoteFileListMonitor> getRemoteFileListMonitors() {
        if (fileListingService != this) {
            return fileListingService.getRemoteFileListMonitors();
        }
        return remoteFileListMonitors;
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

                if (cfdpTransfer instanceof CfdpIncomingTransfer) {
                    CfdpIncomingTransfer incomingTransfer = (CfdpIncomingTransfer) cfdpTransfer;
                    CfdpTransactionId originatingTransactionId = incomingTransfer.getOriginatingTransactionId();
                    if (originatingTransactionId != null) {
                        List<String> request = directoryListingRequests.remove(originatingTransactionId);
                        if (request != null || incomingTransfer.getDirectoryListingResponse() != null) {
                            processDirectoryListingResponse(incomingTransfer, request);
                        }
                    }
                }
            }
            executor.submit(this::tryStartQueuedTransfer);
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

        long sourceId = getEntityFromName(source, localEntities).id;
        long destinationId = getEntityFromName(destination, remoteEntities).id;

        // For backwards compatibility
        var booleanOptions = new HashMap<>(Map.of(
                OVERWRITE_OPTION, options.isOverwrite(),
                RELIABLE_OPTION, options.isReliable(),
                CLOSURE_OPTION, options.isClosureRequested(),
                CREATE_PATH_OPTION, options.isCreatePath()));

        OptionValues optionValues = getOptionValues(options.getExtraOptions());

        booleanOptions.putAll(optionValues.booleanOptions);

        FilePutRequest request = new FilePutRequest(sourceId, destinationId, objectName, absoluteDestinationPath,
                booleanOptions.get(OVERWRITE_OPTION), booleanOptions.get(RELIABLE_OPTION),
                booleanOptions.get(CLOSURE_OPTION), booleanOptions.get(CREATE_PATH_OPTION), bucket, objData);
        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        Double pduSize = optionValues.doubleOptions.get(PDU_SIZE_OPTION);
        Double pduDelay = optionValues.doubleOptions.get(PDU_DELAY_OPTION);

        if (numPendingUploads() < maxNumPendingUploads) {
            return processPutRequest(sourceId, idSeq.next(), creationTime, request, bucket,
                    pduSize != null ? pduSize.intValue() : null, pduDelay != null ? pduDelay.intValue() : null);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(sourceId, idSeq.next(), creationTime,
                    request, bucket, pduSize != null ? pduSize.intValue() : null,
                    pduDelay != null ? pduDelay.intValue() : null);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
            return transfer;
        }

    }

    @Override
    public FileTransfer startDownload(String sourceEntity, String sourcePath, String destinationEntity, Bucket bucket,
            String objectName, TransferOptions options) throws InvalidRequestException {
        if (!hasDownloadCapability) {
            throw new InvalidRequestException("Downloading is not enabled on this CFDP service");
        }

        long destinationId = getEntityFromName(destinationEntity, localEntities).id;
        long sourceId = getEntityFromName(sourceEntity, remoteEntities).id;

        if (objectName.isBlank()) {
            String[] splitPath = sourcePath.split("[\\\\/]");
            objectName = splitPath[splitPath.length - 1];
        }

        // For backwards compatibility
        var booleanOptions = new HashMap<>(Map.of(
                OVERWRITE_OPTION, options.isOverwrite(),
                RELIABLE_OPTION, options.isReliable(),
                CLOSURE_OPTION, options.isClosureRequested(),
                CREATE_PATH_OPTION, options.isCreatePath()));

        OptionValues optionValues = getOptionValues(options.getExtraOptions());

        booleanOptions.putAll(optionValues.booleanOptions);

        // Prepare request
        int entityIdLength = config.getInt("entityIdLength");
        ArrayList<MessageToUser> messagesToUser = new ArrayList<>(
                List.of(new ProxyPutRequest(destinationId, sourcePath, objectName, entityIdLength)));

        CfdpPacket.TransmissionMode transmissionMode = CfdpPacket.TransmissionMode.UNACKNOWLEDGED;
        if (Boolean.TRUE.equals(booleanOptions.get(RELIABLE_OPTION))) {
            transmissionMode = CfdpPacket.TransmissionMode.ACKNOWLEDGED;
        }

        if (options.isReliableSet() || options.getExtraOptions().containsKey(RELIABLE_OPTION)) {
            messagesToUser.add(new ProxyTransmissionMode(transmissionMode));
        }
        if (options.isClosureRequestedSet() || options.getExtraOptions().containsKey(CLOSURE_OPTION)) {
            messagesToUser.add(new ProxyClosureRequest(booleanOptions.get(CLOSURE_OPTION)));
        }

        Double pduSize = optionValues.doubleOptions.get(PDU_SIZE_OPTION);
        Double pduDelay = optionValues.doubleOptions.get(PDU_DELAY_OPTION);

        PutRequest request = new PutRequest(sourceId, transmissionMode, messagesToUser);
        CfdpTransactionId transactionId = request.process(destinationId, idSeq.next(), ChecksumType.MODULAR, config);

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        fileDownloadRequests.addTransfer(transactionId, bucket.getName());
        if (numPendingUploads() < maxNumPendingUploads) {
            return processPutRequest(destinationId, transactionId.getSequenceNumber(), creationTime, request, bucket,
                    pduSize != null ? pduSize.intValue() : null, pduDelay != null ? pduDelay.intValue() : null);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(destinationId,
                    transactionId.getSequenceNumber(),
                    creationTime, request, bucket, pduSize != null ? pduSize.intValue() : null,
                    pduDelay != null ? pduDelay.intValue() : null);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
            return transfer;
        }
    }

    @Override
    public void fetchFileList(String source, String destination, String remotePath, Map<String, Object> options) {
        if (!hasFileListingCapability) {
            throw new InvalidRequestException("File listing is not enabled on this CFDP service");
        }

        EntityConf sourceEntity = getEntityFromName(source, localEntities);
        EntityConf destinationEntity = getEntityFromName(destination, remoteEntities);

        if (fileListingService != this) {
            fileListingService.fetchFileList(sourceEntity.getName(), destinationEntity.getName(), remotePath, options);
            return;
        }

        // Start upload of Directory Listing Request
        String dirPath = remotePath.replaceFirst("/*$", "");

        long creationTime = YamcsServer.getTimeService(yamcsInstance).getMissionTime();

        DirectoryListingRequest directoryListingRequest = new DirectoryListingRequest(dirPath, ".dirlist.notsaved");
        ArrayList<MessageToUser> messagesToUser = new ArrayList<>(List.of(directoryListingRequest));

        PutRequest request = new PutRequest(
                destinationEntity.id,
                Boolean.TRUE.equals(options.get(RELIABLE_OPTION)) ? CfdpPacket.TransmissionMode.ACKNOWLEDGED
                        : CfdpPacket.TransmissionMode.UNACKNOWLEDGED,
                messagesToUser);
        CfdpTransactionId transactionId = request.process(sourceEntity.id, idSeq.next(), ChecksumType.MODULAR, config);

        OptionValues optionValues = getOptionValues(options);

        Double pduSize = optionValues.doubleOptions.get(PDU_SIZE_OPTION);
        Double pduDelay = optionValues.doubleOptions.get(PDU_DELAY_OPTION);

        directoryListingRequests.put(transactionId, Arrays.asList(destinationEntity.getName(), dirPath));
        if (numPendingUploads() < maxNumPendingUploads) {
            processPutRequest(sourceEntity.id, transactionId.getSequenceNumber(), creationTime, request, null,
                    pduSize != null ? pduSize.intValue() : null, pduDelay != null ? pduDelay.intValue() : null);
        } else {
            QueuedCfdpOutgoingTransfer transfer = new QueuedCfdpOutgoingTransfer(sourceEntity.id,
                    transactionId.getSequenceNumber(),
                    creationTime, request, null, pduSize != null ? pduSize.intValue() : null,
                    pduDelay != null ? pduDelay.intValue() : null);
            dbStream.emitTuple(CompletedTransfer.toInitialTuple(transfer));
            queuedTransfers.add(transfer);
            transferListeners.forEach(l -> l.stateChanged(transfer));

            executor.submit(this::tryStartQueuedTransfer);
        }
    }

    @Override
    public ListFilesResponse getFileList(String source, String destination, String remotePath,
            Map<String, Object> options) {
        EntityConf sourceEntity = getEntityFromName(source, localEntities);
        EntityConf destinationEntity = getEntityFromName(destination, remoteEntities);

        if (fileListingService != this) {
            return fileListingService.getFileList(sourceEntity.getName(), destinationEntity.getName(), remotePath,
                    options);
        }

        String dirPath = remotePath.replaceFirst("/*$", "");
        if (automaticDirectoryListingReloads && directoryListingRequests.values().stream()
                .noneMatch(request -> request.equals(Arrays.asList(destinationEntity.getName(), dirPath)))) {
            fetchFileList(sourceEntity.getName(), destinationEntity.getName(), dirPath, options);
        }

        try {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            StreamSqlResult res = ydb.execute("select * from " + FILELIST_TABLE_NAME + " where " + COL_DESTINATION
                    + "=? and " + COL_REMOTE_PATH + "=? ORDER DESC LIMIT 1", destinationEntity.getName(), dirPath);
            if (res.hasNext()) {
                ListFilesResponse response = res.next().getColumn(COL_LIST_FILES_RESPONSE);
                res.close();
                return response;
            } else {
                res.close();
                log.info("No saved file lists found for destination: " + destination + " and remote path: "
                        + remotePath);
            }
        } catch (Exception e) {
            log.error("Failed to query database for previous file listings", e);
        }

        return null;
    }

    private void processDirectoryListingResponse(CfdpIncomingTransfer incomingTransfer, List<String> request) {
        if (incomingTransfer.getTransferState() != TransferState.COMPLETED) {
            return;
        }
        if (request == null) {
            eventProducer.sendWarning(
                    "Received CFDP Directory Listing Response but with no matching Directory Listing Request");
            return;
        }

        if (incomingTransfer.getDirectoryListingResponse().getListingResponseCode() != ListingResponseCode.SUCCESSFUL) {
            eventProducer.sendWarning("Directory Listing Response was "
                    + incomingTransfer.getDirectoryListingResponse().getListingResponseCode() + ". Associated request: "
                    + request);
            return;
        }

        EntityConf remoteEntity = remoteEntities.values().stream()
                .filter(entity -> entity.id == incomingTransfer.cfdpTransactionId.getInitiatorEntity()).findFirst()
                .orElse(null);
        if (remoteEntity == null) {
            eventProducer.sendWarning("Directory Listing Response coming from an unknown remote entity: id="
                    + incomingTransfer.cfdpTransactionId.getInitiatorEntity());
            return;
        }

        String remotePath = request.get(1);

        List<RemoteFile> files = fileListingParser.parse(remotePath, incomingTransfer.getFileData());

        ListFilesResponse listFilesResponse = ListFilesResponse.newBuilder()
                .addAllFiles(files)
                .setDestination(request.get(0))
                .setRemotePath(remotePath)
                .setListTime(TimeEncoding.toProtobufTimestamp(incomingTransfer.getStartTime()))
                .build();

        saveFileList(listFilesResponse);

        log.debug("Notifying {} file list listeners with {} files for destination={} path={}",
                fileListingService.getRemoteFileListMonitors().size(), files.size(), remoteEntity.getName(),
                remotePath);
        notifyRemoteFileListMonitors(listFilesResponse);
    }

    @Override
    public void saveFileList(ListFilesResponse listFilesResponse) {
        if (fileListingService != this) {
            fileListingService.saveFileList(listFilesResponse);
            return;
        }

        Tuple t = new Tuple();
        t.addTimestampColumn(COL_LIST_TIME, TimeEncoding.fromProtobufTimestamp(listFilesResponse.getListTime()));
        t.addColumn(COL_DESTINATION, listFilesResponse.getDestination());
        t.addColumn(COL_REMOTE_PATH, listFilesResponse.getRemotePath());
        t.addColumn(COL_LIST_FILES_RESPONSE, DataType.protobuf("org.yamcs.protobuf.ListFilesResponse"),
                listFilesResponse);
        fileListStream.emitTuple(t);
    }

    private EntityConf getEntityFromName(String entityName, Map<String, EntityConf> entities) {
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
                case PDU_DELAY_OPTION:
                case PDU_SIZE_OPTION:
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
    public List<FileTransferOption> getFileTransferOptions() {
        var options = new ArrayList<FileTransferOption>();
        options.add(FileTransferOption.newBuilder()
                .setName(RELIABLE_OPTION)
                .setType(FileTransferOption.Type.BOOLEAN)
                .setTitle("Reliability")
                .setDescription("Acknowledged or unacknowledged transmission mode")
                .setAssociatedText("Reliable")
                .setDefault("true")
                .build());

        if (canChangePduDelay) {
            options.add(FileTransferOption.newBuilder()
                    .setName(PDU_DELAY_OPTION)
                    .setType(FileTransferOption.Type.DOUBLE)
                    .setTitle("PDU delay")
                    .setDefault(Integer.toString(config.getInt("sleepBetweenPdus")))
                    .addAllValues(pduDelayPredefinedValues.stream()
                            .map(value -> FileTransferOption.Value.newBuilder().setValue(value.toString()).build())
                            .collect(Collectors.toList()))
                    .setAllowCustomOption(true)
                    .build());
        }

        if (canChangePduSize) {
            options.add(FileTransferOption.newBuilder()
                    .setName(PDU_SIZE_OPTION)
                    .setType(FileTransferOption.Type.DOUBLE)
                    .setTitle("PDU size")
                    .setDefault(Integer.toString(config.getInt("maxPduSize")))
                    .addAllValues(pduSizePredefinedValues.stream()
                            .map(value -> FileTransferOption.Value.newBuilder().setValue(value.toString()).build())
                            .collect(Collectors.toList()))
                    .setAllowCustomOption(true)
                    .build());
        }

        return options;
    }

    @Override
    protected void addCapabilities(FileTransferCapabilities.Builder builder) {
        builder.setDownload(hasDownloadCapability)
                .setUpload(true)
                .setRemotePath(true)
                .setFileList(hasFileListingCapability)
                .setPauseResume(true)
                .setHasTransferType(true);
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
