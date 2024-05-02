package org.yamcs.replication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.replication.protobuf.ColumnInfo;
import org.yamcs.replication.protobuf.Request;
import org.yamcs.replication.protobuf.StreamInfo;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.TextFormat;

import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * 
 * Implements the master part of the replication. At any moment there is one current file where the replication data is
 * written.
 * 
 * @author nm
 *
 */
public class ReplicationMaster extends AbstractYamcsService {

    ConcurrentSkipListMap<Long, ReplFileAccess> replFiles = new ConcurrentSkipListMap<>();
    volatile ReplicationFile currentFile = null;

    List<ReplFileAccess> toDeleteList = new ArrayList<>();
    int port;
    List<String> streamNames;
    List<StreamToFile> translators = new ArrayList<>();
    long expiration;
    Path replicationDir;
    int pageSize;
    int maxPages;
    int maxFileSize;
    TcpRole tcpRole;
    List<SlaveServer> slaves;
    long reconnectionInterval;
    int instanceId;
    SslContext sslCtx = null;
    // files not accessed longer than this will be closed
    private long fileCloseTime;

    // how often to run the sync on the current file
    private long fileSyncTime;
    Pattern filePattern;
    int maxTupleSize;
    long timeMsgFreqMillis;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        instanceId = YamcsServer.getServer().getInstance(yamcsInstance).getInstanceId();
        tcpRole = config.getEnum("tcpRole", TcpRole.class, TcpRole.SERVER);
        port = config.getInt("port", -1);
        expiration = (long) (config.getDouble("expirationDays", 7.0) * 24 * 3600 * 1000);
        streamNames = config.getList("streams");
        pageSize = config.getInt("pageSize", 500);
        maxPages = config.getInt("maxPages", 500);
        maxFileSize = 1024 * config.getInt("maxFileSizeKB", 100 * 1024);
        this.maxTupleSize = config.getInt("maxTupleSize");
        this.timeMsgFreqMillis = config.getLong("timeMsgFreqSec") * 1000;

        int hdrSize = ReplicationFile.headerSize(pageSize, maxPages);
        if (maxFileSize < hdrSize) {
            throw new InitException(
                    "maxFileSize has to be higher than header size which for maxPages=" + maxPages + " is " + hdrSize);
        }
        this.filePattern = Pattern.compile(Pattern.quote(serviceName) + "_([0-9A-Fa-f]{16})\\.dat");

        fileCloseTime = config.getLong("fileCloseTimeSec", 300) * 1000;
        YamcsServer.getServer().getThreadPoolExecutor().scheduleAtFixedRate(() -> closeUnusedFiles(), fileCloseTime,
                fileCloseTime, TimeUnit.MILLISECONDS);

        YamcsServer.getServer().getThreadPoolExecutor().scheduleAtFixedRate(() -> deleteExpiredFiles(), fileCloseTime,
                fileCloseTime, TimeUnit.MILLISECONDS);

        fileSyncTime = config.getLong("fileSyncTime", 10) * 1000;
        YamcsServer.getServer().getThreadPoolExecutor().scheduleAtFixedRate(() -> syncCurrentFile(), fileSyncTime,
                fileSyncTime, TimeUnit.MILLISECONDS);

        if (tcpRole == TcpRole.SERVER) {
            List<ReplicationServer> servers = YamcsServer.getServer().getGlobalServices(ReplicationServer.class);
            if (servers.isEmpty()) {
                throw new InitException(
                        "ReplicationMaster is defined with the role Server; that requires the ReplicationServer global service (yamcs.yaml) to be defined");
            } else if (servers.size() > 1) {
                log.warn("There are {} ReplicationServer services defined. Registering to the first one.",
                        servers.size());
            }
            ReplicationServer server = servers.get(0);
            server.registerMaster(this);
        } else {
            reconnectionInterval = 1000 * config.getLong("reconnectionIntervalSec", 30);
            List<YConfiguration> clist = config.getConfigList("slaves");
            slaves = new ArrayList<>(clist.size());
            for (YConfiguration yc : clist) {
                slaves.add(new SlaveServer(yc.getString("host"), yc.getInt("port"), yc.getString("instance"),
                        yc.getBoolean("enableTls", false)));
            }
            boolean enableTls = slaves.stream().map(s -> s.enableTls).filter(b -> b).findAny().isPresent();

            if (enableTls) {
                try {
                    sslCtx = SslContextBuilder.forClient().build();
                } catch (SSLException e) {
                    throw new InitException("Failed to initialize the TLS: " + e.toString());
                }
            }

        }
        String dataDir = YarchDatabase.getDataDir();
        replicationDir = Paths.get(dataDir).resolve(yamcsInstance).resolve("replication");
        try {
            Files.createDirectories(replicationDir);
        } catch (IOException e) {
            throw new InitException("Cannot create the directory where replication files are stored " + replicationDir
                    + ": " + e.getMessage());
        }
        renameOldReplicationFiles();

        scanFiles();
        try {
            initCurrentFile();
        } catch (IOException e) {
            throw new InitException("Error opening/creating a replication file: " + e.getMessage());
        }
    }

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();

        Spec slaveSpec = new Spec();
        slaveSpec.addOption("host", OptionType.STRING);
        slaveSpec.addOption("port", OptionType.INTEGER);
        slaveSpec.addOption("instance", OptionType.STRING);
        slaveSpec.addOption("enableTls", OptionType.BOOLEAN);

        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING).withRequired(true);
        spec.addOption("tcpRole", OptionType.STRING);
        spec.addOption("port", OptionType.INTEGER);
        spec.addOption("expirationDays", OptionType.FLOAT);
        spec.addOption("pageSize", OptionType.INTEGER);
        spec.addOption("maxPages", OptionType.INTEGER);
        spec.addOption("maxFileSizeKB", OptionType.INTEGER);
        spec.addOption("fileCloseTimeSec", OptionType.INTEGER);
        spec.addOption("reconnectionIntervalSec", OptionType.INTEGER);
        spec.addOption("slaves", OptionType.LIST).withElementType(OptionType.MAP).withSpec(slaveSpec);
        spec.addOption("maxTupleSize", OptionType.INTEGER).withDefault(65536)
                .withDescription("Maximum size of the serialized tuple");
        spec.addOption("timeMsgFreqSec", OptionType.INTEGER).withDefault(10)
                .withDescription("How often (in seconds) to send the time message to the slaves");

        return spec;
    }

    private void initCurrentFile() throws IOException, InitException {
        if (replFiles.isEmpty()) {
            openNewFile(null);
        } else { // open last file
            Map.Entry<Long, ReplFileAccess> e = replFiles.lastEntry();
            long firstTxId = e.getKey();
            ReplFileAccess rfa = e.getValue();
            Path path = getPath(firstTxId);
            if (Files.size(path) > maxFileSize) {
                // the last file is greater that maxFileSize (probably maxFileSize has been changed)
                // we have to open it read only to find out last transaction, then open a new file
                rfa.rf = currentFile = ReplicationFile.openReadOnly(yamcsInstance, path, firstTxId);
                if (currentFile.numTx() == 0) {
                    throw new InitException("file " + path
                            + " has zero transactions inside but is bigger that currently defined maxiFileSize. Maybe maxFileSize is too small? Please consider the header size: "
                            + ReplicationFile.headerSize(pageSize, maxPages) + " bytes");
                }
                openNewFile(currentFile);
            } else {
                rfa.rf = currentFile = ReplicationFile.openReadWrite(yamcsInstance, path,
                        firstTxId, maxFileSize);
                if (currentFile.isFull()) {
                    openNewFile(currentFile);
                }
            }
        }
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance db = YarchDatabase.getInstance(yamcsInstance);
        for (int i = 0; i < streamNames.size(); i++) {
            String sn = streamNames.get(i);
            Stream s = db.getStream(sn);
            if (s == null) {
                notifyFailed(new ConfigurationException("Cannot find stream '" + sn + "'"));
                return;
            }
            translators.add(new StreamToFile(s, i));
        }
        if (tcpRole == TcpRole.CLIENT) {
            // connect to all slaves
            for (SlaveServer sa : slaves) {
                sa.client = new ReplicationClient(yamcsInstance, sa.host, sa.port,
                        sa.enableTls ? sslCtx : null, reconnectionInterval, maxTupleSize,
                        () -> {
                            return new MasterChannelHandler(YamcsServer.getTimeService(yamcsInstance), this, sa);
                        });
                sa.client.start();
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (StreamToFile stf : translators) {
            stf.quit();
        }

        for (ReplFileAccess rf : replFiles.values()) {
            if (rf.rf != null) {
                rf.rf.close();
            }
        }
        if (tcpRole == TcpRole.CLIENT) {
            for (SlaveServer sa : slaves) {
                sa.client.stop();
            }
        }
        notifyStopped();
    }

    private synchronized void openNewFile(ReplicationFile rf) {
        if (rf != currentFile) {// some other thread has already open a new file
            return;
        }
        long firstTxId = 0;

        if (currentFile != null) {
            firstTxId = currentFile.getNextTxId();
            currentFile.setSyncRequired(true);
        }

        try {
            currentFile = ReplicationFile.newFile(yamcsInstance, getPath(firstTxId), firstTxId, pageSize,
                    maxPages,
                    maxFileSize);
            replFiles.put(firstTxId, new ReplFileAccess(currentFile));
            // send a StreamInfo for all streams
            for (StreamToFile stf : translators) {
                if (currentFile.writeData(getProtoTransaction(stf.getStreamInfo())) == -1) {
                    throw new IOException(
                            "Failed to write stream info at the beginning of the replication file. Is the file too small??");
                }
            }
        } catch (IOException | UncheckedIOException e) {
            log.error("Failed to open a replication file", e);
            abort(e.getMessage());
        }
    }

    private void scanFiles() throws InitException {
        log.debug("Scanning for replication files in {}", replicationDir);

        try (java.util.stream.Stream<Path> stream = Files.list(replicationDir)) {
            List<Path> files = stream.collect(Collectors.toList());
            for (Path file : files) {
                String name = file.getFileName().toString();
                Matcher m = filePattern.matcher(name);
                if (m.matches()) {
                    long txId = Long.parseLong(m.group(1), 16);
                    replFiles.put(txId, new ReplFileAccess(file));
                    log.debug("Found file starting with txId {}", txId);
                }
            }
        } catch (IOException e) {
            throw new InitException(e);
        }

    }

    /**
     * returns the id of the last transaction
     * <p>
     * If there is no transaction, returns -1
     */
    public long getTxId() {
        return (currentFile) == null ? -1 : currentFile.getNextTxId() - 1;
    }

    private void writeToFile(Transaction tx) {
        ReplicationFile cf = currentFile;
        try {
            long txId = cf.writeData(tx);
            if (txId == -1) {// file full
                openNewFile(cf);
                cf = currentFile;
                txId = cf.writeData(tx);
                if (txId == -1) {
                    log.error(
                            "New file cannot accomodate a single transaction. Please increase the maxFileSize. Consider the header size "
                                    + ReplicationFile.headerSize(pageSize, maxPages));
                    abort("maxFileSize too small; cannot accomodate a single transaction");
                }
            }
        } catch (UncheckedIOException e) {
            log.error("Got exception when writing transaction to file, forcefully opening a new replication file", e);
            replFiles.remove(cf.getFirstId());
            openNewFile(cf);
        }
    }

    private void abort(String msg) {
        log.error("Aborting the replication master");
        for (StreamToFile stf : translators) {
            stf.quit();
        }

        notifyFailed(new Exception(msg));
    }

    private Transaction getProtoTransaction(MessageLite msg) {
        return new Transaction() {
            @Override
            public byte getType() {
                return Message.STREAM_INFO;
            }

            @Override
            public void marshall(ByteBuffer buf) {
                try {
                    CodedOutputStream cos = CodedOutputStream.newInstance(buf);
                    msg.writeTo(cos);
                    buf.position(buf.position() + cos.getTotalBytesWritten());
                } catch (CodedOutputStream.OutOfSpaceException e) {
                    throw new BufferOverflowException();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public int getInstanceId() {
                return instanceId;
            }
        };
    }

    public ChannelHandler newChannelHandler(Request req) {
        return new MasterChannelHandler(YamcsServer.getTimeService(yamcsInstance), this, req);
    }

    public List<String> getStreamNames() {
        return streamNames;
    }

    public boolean isTcpClient() {
        return tcpRole == TcpRole.CLIENT;
    }

    public List<SlaveServer> getSlaveServers() {
        return slaves;
    }

    class StreamToFile implements StreamSubscriber {
        TupleDefinition completeTuple = new TupleDefinition();
        final Stream stream;
        private volatile ColumnSerializer<?>[] valueSerializers = new ColumnSerializer<?>[10];
        final int streamId;

        StreamToFile(Stream s, int streamId) {
            this.stream = s;
            stream.addSubscriber(this);
            this.streamId = streamId;
        }

        @Override
        public void onTuple(Stream s, Tuple tuple) {
            ensureIndices(tuple.getDefinition());

            Transaction tx = new Transaction() {
                @Override
                public void marshall(ByteBuffer buf) {

                    buf.putInt(streamId);
                    TupleDefinition tdef = tuple.getDefinition();
                    for (int i = 0; i < tdef.size(); i++) {
                        Object v = tuple.getColumn(i);
                        if (v == null) { // since Yamcs 5.3.1 we allow nulls in the tuple values
                            continue;
                        }
                        ColumnDefinition cd = tdef.getColumn(i);
                        int cidx = completeTuple.getColumnIndex(cd.getName());

                        ColumnSerializer tcs = valueSerializers[cidx];
                        int x = (cd.getType().getTypeId() << 24) | cidx;
                        buf.putInt(x);
                        tcs.serialize(buf, v);
                    }
                    // add a final -1 eof marker
                    buf.putInt(-1);
                }

                @Override
                public byte getType() {
                    return Message.DATA;
                }

                @Override
                public int getInstanceId() {// in the future we may put this as part of the tuple
                    return instanceId;
                }
            };
            writeToFile(tx);
        }

        private synchronized void ensureIndices(TupleDefinition tdef) {
            boolean addedColumns = false;
            for (int i = 0; i < tdef.size(); i++) {
                ColumnDefinition cd = tdef.getColumn(i);
                int colId = completeTuple.getColumnIndex(cd.getName());
                if (colId == -1) {
                    completeTuple.addColumn(cd);
                    addedColumns = true;
                }
            }

            for (int i = tdef.size() - 1; i >= 0; i--) { // we go backwards because columns with higher ids are likely
                // added at the end
                ColumnDefinition cd = tdef.getColumn(i);
                int cidx = completeTuple.getColumnIndex(cd.getName());
                assert (cidx != -1);
                if (cidx >= valueSerializers.length) {
                    valueSerializers = Arrays.copyOf(valueSerializers, cidx + 1);
                }
                valueSerializers[cidx] = ColumnSerializerFactory.getColumnSerializerForReplication(cd);
            }

            if (addedColumns) {
                StreamInfo strinfo = getStreamInfo();
                log.debug("Writing stream info transaction {}", TextFormat.shortDebugString(strinfo));
                Transaction tx = getProtoTransaction(strinfo);
                ReplicationFile cf = currentFile;
                long txId = cf.writeData(tx);
                if (txId == -1) {
                    // file full - open a new one and don't write the metadata transaction anymore, it will be written
                    // in the openNewFile for all streams
                    openNewFile(cf);
                }
            }
        }

        private StreamInfo getStreamInfo() {
            StreamInfo.Builder sib = StreamInfo.newBuilder();
            sib.setId(streamId).setName(stream.getName());
            for (int i = 0; i < completeTuple.size(); i++) {
                ColumnDefinition cd = completeTuple.getColumn(i);
                sib.addColumns(ColumnInfo.newBuilder().setId(i).setName(cd.getName())
                        .setType(cd.getType().toString()).build());
            }
            return sib.build();
        }

        void quit() {
            stream.removeSubscriber(this);
        }
    }

    /**
     * Get the file where startTxId transaction is or the earliest file available if the transaction is in the past
     * <p>
     * Return null if the transaction is in the future. If there is a file which does not contain the startTxId but it's
     * just the next one to come, then return that file.
     * 
     * 
     * @param startTxId
     * @return
     */
    public ReplicationFile getFile(long startTxId) {
        Map.Entry<Long, ReplFileAccess> e = replFiles.floorEntry(startTxId);
        if (e == null) { // transaction is in the past not available
            e = replFiles.firstEntry();
        }
        ReplFileAccess rfa = e.getValue();
        synchronized (rfa) {
            if (rfa.rf == null) {
                long firstTxId = e.getKey();
                rfa.rf = ReplicationFile.openReadOnly(yamcsInstance, getPath(firstTxId),
                        firstTxId);
            }
            rfa.lastAccess = System.currentTimeMillis();
        }
        ReplicationFile rf = rfa.rf;
        long nextTxId = rf.getNextTxId();
        if (nextTxId < startTxId) {
            Long k = replFiles.ceilingKey(nextTxId);
            if (k != null && k != nextTxId) {
                log.error("There is a gap in the replication files, transactions {} to {} are missing", nextTxId,
                        k - 1);
                return getFile(k);
            }
            return null;
        }

        return rf;
    }

    Path getPath(long firstTxId) {
        return replicationDir.resolve(String.format("%s_%016x.dat", serviceName, firstTxId));
    }

    /**
     * closes files not accessed in a while
     */
    private void closeUnusedFiles() {
        long t = System.currentTimeMillis() - fileCloseTime;
        try {
            for (ReplFileAccess rfa : replFiles.values()) {
                synchronized (rfa) {
                    if (rfa.rf != currentFile && rfa.rf != null && rfa.lastAccess < t) {
                        log.debug("Closing {} because it has not been accessed since {}", rfa.path,
                                Instant.ofEpochMilli(rfa.lastAccess));
                        rfa.rf.close();
                        rfa.rf = null;
                    } else if (rfa.rf.isSyncRequired()) {
                        // the file has just been rotated by the data thread
                        rfa.rf.sync();
                        currentFile.setSyncRequired(false);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Caught exception when closing or syncing files", e);
        }

        try {
            for (ReplFileAccess rfa : toDeleteList) {
                synchronized (rfa) {
                    if (rfa.rf != null && rfa.lastAccess < t) {
                        log.debug("Closing and removing {} because it has not been accessed since {} "
                                + "and it is on the list for deletion.",
                                rfa.path, Instant.ofEpochMilli(rfa.lastAccess));
                        rfa.rf.close();
                        Files.delete(rfa.path);
                        rfa.rf = null;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Caught exception when looking for files to remove ", e);
        }
    }

    private void deleteExpiredFiles() {
        try (java.util.stream.Stream<Path> stream = Files.list(replicationDir)) {
            List<Path> files = stream.collect(Collectors.toList());
            for (Path file : files) {
                String name = file.getFileName().toString();
                Matcher m = filePattern.matcher(name);
                if (m.matches()) {
                    long txId = Long.parseLong(m.group(1), 16);
                    if (txId != currentFile.getFirstId()) {// never remove the current file
                        checkForRemoval(file, txId);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Caught exception when looking for files to remove ", e);
        }
    }

    private void syncCurrentFile() {
        try {
            log.trace("Syncing current replication file {}", currentFile.path);
            currentFile.sync();
        } catch (Exception e) {
            log.error("Error syncing current replication file", e);
        }
    }

    void checkForRemoval(Path file, long firstTxId) throws IOException {
        long t = System.currentTimeMillis() - expiration;
        BasicFileAttributes bfa = Files.readAttributes(file, BasicFileAttributes.class);
        if (bfa.creationTime().toMillis() > t) {
            return;
        }
        ReplFileAccess rfa = replFiles.remove(firstTxId);
        if (rfa == null) {// probably one that is on the toDeleteList
            return;
        }
        synchronized (rfa) {
            if (rfa.rf == null) {
                log.debug("Deleting file {} created {}", file, bfa.creationTime());
                Files.delete(file);
            } else {
                // file is open for replay, put it on the list to be removed when it is closed
                toDeleteList.add(rfa);
            }
        }
    }

    // this is called at init starting with yamcs 5.4.2 when the replication files have been renamed to contain the
    // service name.
    // it renames the old replication files to the new names
    // to be removed after a while
    private void renameOldReplicationFiles() throws InitException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(replicationDir)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    String name = path.getFileName().toString();
                    if (name.matches("RPL_[0-9a-fA-F]{16}\\.dat")) {
                        Path newPath = replicationDir.resolve(name.replace("RPL_", serviceName + "_"));
                        log.info("Renaming {} to {}", path, newPath);
                        Files.move(path, newPath, StandardCopyOption.ATOMIC_MOVE);
                    }
                }
            }
        } catch (IOException e) {
            throw new InitException(e);
        }
    }

    static class ReplFileAccess {
        long lastAccess;
        ReplicationFile rf;
        Path path;

        public ReplFileAccess(ReplicationFile file) {
            this.lastAccess = System.currentTimeMillis();
            this.rf = file;
        }

        public ReplFileAccess(Path path) {
            this.lastAccess = -1;
            this.rf = null;
            this.path = path;
        }
    }

    public static class SlaveServer {
        String host;
        int port;
        ReplicationClient client;
        String instance;
        boolean enableTls = false;

        public SlaveServer(String host, int port, String instance, boolean enableTls) {
            this.host = host;
            this.port = port;
            this.instance = instance;
            this.enableTls = enableTls;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getInstance() {
            return instance;
        }

        public ReplicationClient getTcpClient() {
            return client;
        }
    }
}
