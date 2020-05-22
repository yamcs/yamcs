package org.yamcs.replication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
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
/**
 * 
 * Provides the master part of the replication. At any moment there is one current file where the replication data is written. 
 * <p>
 * TODO: remove old replication files
 * 
 * @author nm
 *
 */
public class ReplicationMaster extends AbstractYamcsService {
    private static final Pattern FILE_PATTERN = Pattern.compile("RPL_([0-9A-Fa-f]{16})\\.dat");

    ConcurrentSkipListMap<Long, ReplFileAccess> replFiles = new ConcurrentSkipListMap<>();
    volatile ReplicationFile currentFile = null;

    int port;
    List<String> streamNames;
    List<StreamToFile> translators = new ArrayList<>();
    long expiration;
    Path replicationDir;
    int pageSize;
    int maxPages;
    int maxFileSize;
    ReplicationServer replicationTcpServer;
    TcpRole tcpRole;
    List<SlaveServer> slaves;
    long reconnectionInterval;
    int serverId;
    
    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);
        serverId = YamcsServer.getServer().getInstance(yamcsInstance).getInstanceId();
        tcpRole = config.getEnum("tcpRole", TcpRole.class, TcpRole.Server);
        port = config.getInt("port", -1);
        expiration = (long) (config.getDouble("expirationDays", 7.0) * 24 * 3600 * 1000);
        streamNames = config.getList("streams");
        pageSize = config.getInt("pageSize", 500);
        maxPages = config.getInt("maxPages", 500);
        maxFileSize = 1024 * config.getInt("maxFileSizeKB", 100 * 1024);

        if (tcpRole == TcpRole.Server) {
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
            reconnectionInterval = config.getLong("reconnectionInterval", 5000);
            List<YConfiguration> clist = config.getConfigList("slaves");
            slaves = new ArrayList<>(clist.size());
            for (YConfiguration yc : clist) {
                slaves.add(new SlaveServer(yc.getString("host"), yc.getInt("port"), yc.getString("instance")));
            }
        }
        String dataDir = YarchDatabase.getDataDir();
        replicationDir = Paths.get(dataDir).resolve(yamcsInstance).resolve("replication");
        replicationDir.toFile().mkdirs(); // we don't check the result because scanFiles will throw an exception if the
                                          // directory
        // doesn't exist
        scanFiles();

        if (replFiles.isEmpty()) {
            openNewFile(null);
        } else { // open last file
            Map.Entry<Long, ReplFileAccess> e = replFiles.lastEntry();
            long firstTxId = e.getKey();
            ReplFileAccess rfa = e.getValue();
            rfa.file = currentFile = ReplicationFile.openReadWrite(serverId, yamcsInstance, replicationDir.toString(), firstTxId,
                    maxFileSize);
            if (currentFile.isFull()) {
                openNewFile(currentFile);
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
        if (tcpRole == TcpRole.Client) {
            // connect to all slaves
            for (SlaveServer sa : slaves) {
                sa.client = new TcpClient(yamcsInstance, sa.host, sa.port, reconnectionInterval,
                        () -> {
                            return new MasterChannelHandler(this, sa);
                        });
                sa.client.start();
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (ReplFileAccess rf : replFiles.values()) {
            if (rf.file != null) {
                rf.file.close();
            }
        }
        notifyStopped();
    }

    private synchronized void openNewFile(ReplicationFile rf) {
        if (rf != currentFile) {// some other thread has already open a new file
            return;
        }
        long firstTxId = (currentFile == null) ? 0 : currentFile.getNextTxId();
        try {
            currentFile = ReplicationFile.newFile(yamcsInstance, replicationDir.toString(), firstTxId, pageSize,
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
        } catch (IOException e) {
            log.error("Failed to open a replication file  ", e);
            notifyFailed(e);
        }
    }

    private void scanFiles() throws InitException {
        log.debug("Scanning for replication files in {}", replicationDir);

        try (java.util.stream.Stream<Path> stream = Files.list(replicationDir)) {
            List<Path> files = stream.collect(Collectors.toList());
            for (Path file : files) {
                String name = file.getFileName().toString();
                Matcher m = FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    long txId = Long.parseLong(m.group(1), 16);
                    replFiles.put(txId, new ReplFileAccess());
                    log.debug("Found file starting with txId {}", txId);
                }
            }
        } catch (IOException e) {
            throw new InitException(e);
        }

    }

    private void writeToFile(Transaction tx) {
        ReplicationFile cf = currentFile;
        long txId = cf.writeData(tx);
        if (txId == -1) {// file full
            openNewFile(cf);
            txId = currentFile.writeData(tx);
            if (txId == -1) {
                log.error("File full when writing transaction to a file despite opening a new one");
            }
        }
    }
    
    private Transaction getProtoTransaction(MessageLite msg) {
        Transaction tx = new Transaction() {
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
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public int getInstanceId() {
                return serverId;
            }
        };
        return tx;
    }


    public ChannelHandler newChannelHandler(Request req) {
        return new MasterChannelHandler(this, req);
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
                        ColumnDefinition cd = tdef.getColumn(i);
                        int cidx = completeTuple.getColumnIndex(cd.getName());

                        Object v = tuple.getColumn(i);
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
                public int getInstanceId() {//in the future we may put this as part of the tuple
                    return serverId;
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
                valueSerializers[cidx] = ColumnSerializerFactory.getColumnSerializer(cd);
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
            if (rfa.file == null) {
                rfa.file = ReplicationFile.openReadOnly(serverId, yamcsInstance, replicationDir.toString(), e.getKey());
                rfa.lastAccess = System.currentTimeMillis();
            }
        }
        ReplicationFile rf = rfa.file;
        long nextTxId = rf.getNextTxId();
        if (nextTxId < startTxId) {
            Long k = replFiles.ceilingKey(nextTxId);
            if (k != null) {
                log.error("There is a gap in the replication files, transactions {} to {} are missing", nextTxId,
                        k - 1);
                return getFile(k);
            }
            return null;
        }

        return rf;
    }

    static class ReplFileAccess {
        long lastAccess;
        ReplicationFile file;

        public ReplFileAccess(ReplicationFile file) {
            this.lastAccess = System.currentTimeMillis();
            this.file = file;
        }

        public ReplFileAccess() {
            this.lastAccess = -1;
            this.file = null;
        }
    };

    static class SlaveServer {
        String host;
        int port;
        TcpClient client;
        String instance;

        public SlaveServer(String host, int port, String instance) {
            this.host = host;
            this.port = port;
            this.instance = instance;
        }

    }
}
