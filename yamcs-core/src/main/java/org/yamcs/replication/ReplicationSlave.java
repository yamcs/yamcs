package org.yamcs.replication;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.Spec.OptionType;
import org.yamcs.replication.Message.TransactionMessage;
import org.yamcs.replication.protobuf.ColumnInfo;
import org.yamcs.replication.protobuf.Request;
import org.yamcs.replication.protobuf.Response;
import org.yamcs.replication.protobuf.StreamInfo;
import org.yamcs.replication.protobuf.TimeMessage;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.DecodingException;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.TextFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.ScheduledFuture;

public class ReplicationSlave extends AbstractYamcsService {
    private TcpRole tcpRole;
    int port;
    String host;
    ReplicationClient tcpClient;
    long reconnectionInterval;
    String masterInstance;
    long lastTxId;
    SlaveChannelHandler slaveChannelHandler;
    // remote (master) stream name -> local stream name
    Map<String, String> streamNames = new HashMap<>();
    RandomAccessFile lastTxFile;

    Path txtfilePath;
    int localInstanceId;
    SslContext sslCtx = null;
    int maxTupleSize;
    long timeoutMillis;
    SimulationTimeService simTimeService = null;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        YamcsServerInstance ysi =YamcsServer.getServer().getInstance(yamcsInstance); 
        this.localInstanceId = ysi.getInstanceId();
        boolean updateSimTime = config.getBoolean("updateSimTime");
        if (updateSimTime) {
            TimeService srv = ysi.getTimeService();
            if (srv instanceof SimulationTimeService) {
                simTimeService = (SimulationTimeService) srv;
                simTimeService.setTime0(0);
            } else {
                throw new ConfigurationException(
                        "Cannot use updateSimTime unless the simulated time service is configured");
            }
        }
        List<String> streams = config.getList("streams");
        for (String s : streams) {
            String[] a = s.split("\\s*\\-\\>\\s*");
            if(a.length == 1) {
                streamNames.put(a[0], a[0]);
            } else if (a.length == 2) {
                streamNames.put(a[0], a[1]);
            } else {
                throw new ConfigurationException("Invalid stream spec '" + s + "'");
            }
        }
        tcpRole = config.getEnum("tcpRole", TcpRole.class, TcpRole.CLIENT);
        if (tcpRole == TcpRole.CLIENT) {
            host = config.getString("masterHost");
            port = config.getInt("masterPort");
            reconnectionInterval = 1000 * config.getLong("reconnectionIntervalSec", 30);
            boolean enableTls = config.getBoolean("enableTls", false);

            if (enableTls) {
                try {
                    sslCtx = SslContextBuilder.forClient().build();
                } catch (SSLException e) {
                    throw new InitException("Failed to initialize the TLS: " + e.toString());
                }
            }

        } else {
            ReplicationServer server = getReplicationServer();
            server.registerSlave(this);
        }
        masterInstance = config.getString("masterInstance", yamcsInstance);// by default we ask the same instance from
        String dataDir = YarchDatabase.getDataDir();
        Path replicationDir = Paths.get(dataDir).resolve(yamcsInstance).resolve("replication");
        replicationDir.toFile().mkdirs();
        String lastTxFilename = config.getString("lastTxFile", serviceName + "-lastid.txt");
        this.maxTupleSize = config.getInt("maxTupleSize");
        this.timeoutMillis = (long) (config.getDouble("timeoutSec") * 1000);

        txtfilePath = replicationDir.resolve(lastTxFilename);
        try {
            lastTxFile = new RandomAccessFile(txtfilePath.toFile(), "rw");
            String line = lastTxFile.readLine();
            if (line != null) {
                lastTxId = Long.parseLong(line);
            } else {
                lastTxId = -1;
            }
        } catch (IOException e) {
            throw new InitException(e);
        } catch (NumberFormatException e) {
            throw new InitException("Cannot parse number from " + txtfilePath + ": " + e);
        }

    }

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING).withRequired(true);
        spec.addOption("tcpRole", OptionType.STRING);
        spec.addOption("masterHost", OptionType.STRING);
        spec.addOption("masterPort", OptionType.INTEGER);
        spec.addOption("reconnectionIntervalSec", OptionType.INTEGER);
        spec.addOption("enableTls", OptionType.BOOLEAN);
        spec.addOption("masterInstance", OptionType.STRING);
        spec.addOption("lastTxFile", OptionType.STRING);
        spec.addOption("maxTupleSize", OptionType.INTEGER).withDefault(131072)
                .withDescription("Maximum size of the serialized tuple");
        spec.addOption("timeoutSec", OptionType.FLOAT)
                .withDescription(
                        "Timeout in seconds. If no message is received in this time, the connection will be closed")
                .withDefault(30);

        spec.addOption("updateSimTime", OptionType.BOOLEAN).withDefault(false)
                .withDescription("If true, update the simulation time with the time received from the master");
        return spec;
    }

    @Override
    protected void doStart() {
        if (tcpRole == TcpRole.CLIENT) {
            tcpClient = new ReplicationClient(yamcsInstance, host, port, sslCtx, reconnectionInterval, maxTupleSize,
                    () -> new SlaveChannelHandler(this));
            tcpClient.start();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        shutdown();
        notifyStopped();
    }

    private void failService(String errMsg) {
        log.warn("Replication failed: {}", errMsg);
        log.warn("Shutting down the service");
        shutdown();
        notifyFailed(new Exception(errMsg));
    }

    private void shutdown() {
        log.debug("Shutting down the replication slave");
        if (tcpClient != null) {
            tcpClient.stop();
        }
        if (tcpRole == TcpRole.SERVER) {
            try {
                getReplicationServer().unregisterSlave(this);
            } catch (InitException e) {
                // shouldn't happen since we are already started
                throw new RuntimeException(e);
            }
        }
        if (slaveChannelHandler != null) {
            slaveChannelHandler.shutdown();
            slaveChannelHandler = null;
        }

        try {
            lastTxFile.close();
        } catch (IOException e) {
            log.error("Failed to close the last TX id file");
            notifyFailed(e);
        }
    }

    private void updateLastTxFile() {
        try {
            lastTxFile.seek(0);
            lastTxFile.writeBytes(Long.toString(lastTxId) + "\n");
        } catch (IOException e) {
            log.warn("Failed to update the last tx file " + txtfilePath, e);
        }
    }

    private ReplicationServer getReplicationServer() throws InitException {
        List<ReplicationServer> servers = YamcsServer.getServer().getGlobalServices(ReplicationServer.class);
        if (servers.isEmpty()) {
            throw new InitException(
                    "ReplicationSlave is defined with the role Server; that requires the ReplicationServer global service (yamcs.yaml) to be defined");
        } else if (servers.size() > 1) {
            log.warn("There are {} ReplicationServer services defined. Registering to the first one.",
                    servers.size());
        }
        return servers.get(0);
    }


    public List<String> getStreamNames() {
        return streamNames.entrySet().stream().map(e -> {
            if (e.getKey().equals(e.getValue())) {
                return e.getKey();
            } else {
                return e.getKey() + "->" + e.getValue();
            }
        }).collect(Collectors.toList());
    }

    public boolean isTcpClient() {
        return tcpRole == TcpRole.CLIENT;
    }

    public ReplicationClient getTcpClient() {
        return tcpClient;
    }

    public String getMasterHost() {
        return host;
    }

    public int getMasterPort() {
        return port;
    }

    public String getMasterInstance() {
        return masterInstance;
    }

    public long getTxId() {
        return lastTxId;
    }

    /**
     * Called when the tcpRole = Server and a new client connects to {@link ReplicationServer}
     * 
     * @throws YamcsException
     *             if there is already a connection open to this slave
     */
    public ChannelHandler newChannelHandler() throws YamcsException {
        if (slaveChannelHandler != null) {
            throw new YamcsException("There is already a connection open to this slave");
        }
        slaveChannelHandler = new SlaveChannelHandler(this);
        return slaveChannelHandler;
    }

    private void processTimeMessage(TimeMessage timeMsg) {
        if (simTimeService != null) {
            simTimeService.setSimElapsedTime(timeMsg.getLocalTime(), timeMsg.getMissionTime());
            if (timeMsg.hasSpeed()) {
                simTimeService.setSimSpeed(timeMsg.getSpeed());
            }
        }
    }

    public class SlaveChannelHandler extends ChannelInboundHandlerAdapter {
        ReplicationSlave replSlave;
        private ChannelHandlerContext channelHandlerContext;
        Map<Integer, ByteBufToStream> streamWriters = new HashMap<>();
        long lastMsgReceivedTime;
        private ScheduledFuture<?> timeoutFuture;

        public SlaveChannelHandler(ReplicationSlave slave) {
            this.replSlave = slave;
            this.lastMsgReceivedTime = System.currentTimeMillis();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object o) {
            ByteBuf nettybuf = (ByteBuf) o;
            try {
                doChannelRead(ctx, nettybuf);
            } finally {
                nettybuf.release();
            }
        }

        private void doChannelRead(ChannelHandlerContext ctx, ByteBuf nettybuf) {
            ByteBuffer buf = nettybuf.nioBuffer();

            if (state() != State.RUNNING) {
                return;
            }

            Message msg;
            try {
                msg = Message.decode(buf);
            } catch (DecodingException e) {
                log.warn("TX{} Failed to decode message {}; closing connection", lastTxId,
                        ByteBufUtil.hexDump(nettybuf), e);
                ctx.close();
                return;
            }
            lastMsgReceivedTime = System.currentTimeMillis();
            if (msg.type == Message.DATA) {
                TransactionMessage tmsg = (TransactionMessage) msg;

                if (tmsg.txId <= lastTxId) {
                    log.warn("Received data from the past txId={}, lastTxId={}", tmsg.txId, lastTxId);
                } else {
                    checkMissing(tmsg);
                }

                int streamId = tmsg.buf.getInt();

                if (tmsg.instanceId == localInstanceId) {
                    log.trace("Skipping data originating from myself (serverId: {})", tmsg.instanceId);
                    return;
                }
                ByteBufToStream bbs = streamWriters.get(streamId);
                if (bbs == null) {
                    log.trace("Skipping data for unknown stream {}", streamId);
                    return;
                }
                if (log.isTraceEnabled()) {
                    log.trace("TX{} received data for stream {}, length {}", tmsg.txId, bbs.stream.getName(),
                            tmsg.buf.remaining());
                }

                bbs.processData(tmsg.txId, tmsg.buf);
            } else if (msg.type == Message.STREAM_INFO) {
                TransactionMessage tmsg = (TransactionMessage) msg;
                if (tmsg.txId > lastTxId) { // we expect to receive previous stream info transactions
                    checkMissing(tmsg);
                }

                StreamInfo streamInfo = (StreamInfo) msg.protoMsg;
                if (!streamInfo.hasName() || !streamInfo.hasId()) {
                    failService("TX" + tmsg.txId + ": received invalid stream info: " + streamInfo);
                    return;
                }
                log.debug("TX{}: received stream info {}", tmsg.txId, TextFormat.shortDebugString(streamInfo));
                String remoteStreamName = streamInfo.getName();
                if (!streamNames.containsKey(remoteStreamName)) {
                    log.debug("TX{}: Ignoring stream {} because it is not in the list configured", tmsg.txId,
                            remoteStreamName);
                    return;
                }
                String localStreamName = streamNames.get(remoteStreamName);
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
                Stream stream = ydb.getStream(localStreamName);
                if (stream == null) {
                    log.warn("TX{}: Received data for stream {} which does not exist", tmsg.txId, localStreamName);
                    return;
                }
                streamWriters.put(streamInfo.getId(), new ByteBufToStream(stream, streamInfo));
            } else if (msg.type == Message.RESPONSE) {// this is sent by a master when we are slave.
                Response resp = (Response) msg.protoMsg;
                if (resp.getResult() != 0) {
                    failService("Received negative response: " + resp.getErrorMsg());
                    return;
                } else {
                    log.info("Received response {}", resp);
                }
            } else if (msg.type == Message.TIME) {
                TimeMessage timeMsg = (TimeMessage) msg.protoMsg;
                processTimeMessage(timeMsg);
            } else {
                failService("Unexpected message type " + msg.type + " received from the master");
                return;
            }
        }


        private void checkMissing(TransactionMessage tmsg) {
            if (tmsg.txId != lastTxId + 1) {
                log.warn("Transactions {} to {} are missing", lastTxId + 1, tmsg.txId - 1);
            }
            lastTxId = tmsg.txId;
        }

        // called when tcpRole=Client and the connection is open
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            sendRequest();
        }

        // called when tcpRole=Server and this handler is added to the pipeline by the ReplicationServer
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            if (tcpRole == TcpRole.CLIENT) {
                return;
            }
            this.channelHandlerContext = ctx;
            sendRequest();
        }

        private void sendRequest() {
            Request.Builder reqb = Request.newBuilder().setRequestSeq(1).setYamcsInstance(masterInstance);
            if (lastTxId >= 0) {
                reqb.setStartTxId(lastTxId + 1);
            }
            Request req = reqb.build();
            log.debug("Connection {} opened, sending request {}", channelHandlerContext.channel().remoteAddress(),
                    TextFormat.shortDebugString(req));
            ByteBuf buf = Unpooled.wrappedBuffer(Message.get(req).encode());
            channelHandlerContext.writeAndFlush(buf);
            cancelTimeoutFuture();
            timeoutFuture = channelHandlerContext.executor().scheduleAtFixedRate(this::checkTimeout, timeoutMillis,
                    timeoutMillis, TimeUnit.MILLISECONDS);
        }

        void checkTimeout() {
            long now = System.currentTimeMillis();
            if (now - lastMsgReceivedTime > timeoutMillis) {
                log.warn("No message received in the last {} seconds. Closing the connection",
                        (now - lastMsgReceivedTime) / 1000);
                channelHandlerContext.close();
                cancelTimeoutFuture();
            }
        }

        void cancelTimeoutFuture() {
            ScheduledFuture<?> sf = timeoutFuture;
            if (sf != null) {
                sf.cancel(true);
            }
        }
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.channelHandlerContext = ctx;
        }

        public void shutdown() {
            channelHandlerContext.close();
            cancelTimeoutFuture();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Caught exception", cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("Connection {} closed", ctx.channel().remoteAddress());
            super.channelInactive(ctx);
            cancelTimeoutFuture();
            slaveChannelHandler = null;
        }

        class ByteBufToStream {
            TupleDefinition completeTuple;
            ColumnSerializer<?>[] serializers;
            Stream stream;

            public ByteBufToStream(Stream stream, StreamInfo streamInfo) {
                this.stream = stream;

                completeTuple = new TupleDefinition();
                serializers = new ColumnSerializer<?>[streamInfo.getColumnsCount()];
                for (int i = 0; i < serializers.length; i++) {
                    ColumnInfo cinfo = streamInfo.getColumns(i);
                    if (cinfo.getId() != i) {
                        log.warn("Corrupted metadata? c[{}].getId = {} (should be {})", i, cinfo.getId(), i);
                        return;
                    }
                    String cname = cinfo.getName();
                    String ctype = cinfo.getType();
                    DataType type = DataType.byName(ctype);
                    ColumnDefinition cd = new ColumnDefinition(cname, type);
                    completeTuple.addColumn(cd);
                    serializers[i] = ColumnSerializerFactory.getColumnSerializerForReplication(cd);
                }
            }

            @SuppressWarnings("rawtypes")
            public void processData(long txId, ByteBuffer niobuf) {
                TupleDefinition tdef = new TupleDefinition();
                ArrayList<Object> cols = new ArrayList<>();
                // deserialize the value
                try {
                    while (true) {
                        int id = niobuf.getInt(); // column index
                        if (id == -1) {
                            break;
                        }
                        int cidx = id & 0xFFFF;
                        if (cidx >= completeTuple.size()) {
                            log.warn(
                                    "TX{}: when deserializing data for stream {}: reference to unknown column index {}",
                                    txId, stream.getName(), cidx);
                            return;
                        }
                        int typeId = id >>> 24;
                        ColumnDefinition cd = completeTuple.getColumn(cidx);
                        ColumnSerializer cs = serializers[cidx];
                        if (cd.getType().getTypeId() != typeId) {
                            log.warn(
                                    "TX{}: when deserializing data for stream {}: type id for index {} (column {}) is {}; expected {}",
                                    txId, stream.getName(), cidx, cd.getName(), typeId, cd.getType().getTypeId());
                            return;
                        }
                        Object o = cs.deserialize(niobuf, cd);
                        tdef.addColumn(cd);
                        cols.add(o);
                    }
                    Tuple t = new Tuple(tdef, cols);
                    stream.emitTuple(t);
                    updateLastTxFile();

                } catch (Exception e) {
                    log.warn("Cannot deserialize data for stream {}", stream.getName(), e);
                }
            }
        }
    }

}
