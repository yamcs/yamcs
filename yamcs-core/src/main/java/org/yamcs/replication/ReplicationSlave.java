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

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.replication.protobuf.ColumnInfo;
import org.yamcs.replication.protobuf.Request;
import org.yamcs.replication.protobuf.Response;
import org.yamcs.replication.protobuf.StreamInfo;
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

public class ReplicationSlave extends AbstractYamcsService {
    private TcpRole tcpRole;
    int port;
    String host;
    ReplicationClient tcpClient;
    long reconnectionInterval;
    String masterInstance;
    long lastTxId;
    SlaveChannelHandler slaveChannelHandler;
    List<String> streamNames;
    RandomAccessFile lastTxFile;
    Path txtfilePath;
    int localInstanceId;

    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);
        this.localInstanceId = YamcsServer.getServer().getInstance(yamcsInstance).getInstanceId();
        streamNames = config.getList("streams");
        tcpRole = config.getEnum("tcpRole", TcpRole.class, TcpRole.Client);
        if (tcpRole == TcpRole.Client) {
            host = config.getString("masterHost");
            port = config.getInt("masterPort");
            reconnectionInterval = config.getLong("reconnectionInterval", 30000);
        } else {
            List<ReplicationServer> servers = YamcsServer.getServer().getGlobalServices(ReplicationServer.class);
            if (servers.isEmpty()) {
                throw new InitException(
                        "ReplicationSlave is defined with the role Server; that requires the ReplicationServer global service (yamcs.yaml) to be defined");
            } else if (servers.size() > 1) {
                log.warn("There are {} ReplicationServer services defined. Registering to the first one.",
                        servers.size());
            }
            ReplicationServer server = servers.get(0);
            server.registerSlave(this);
        }
        masterInstance = config.getString("masterInstance", yamcsInstance);// by default we ask the same instance from
        String dataDir = YarchDatabase.getDataDir();
        Path replicationDir = Paths.get(dataDir).resolve(yamcsInstance).resolve("replication");
        replicationDir.toFile().mkdirs();
        txtfilePath = replicationDir.resolve("slave-lastid.txt");
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
    protected void doStart() {
        if (tcpRole == TcpRole.Client) {
            tcpClient = new ReplicationClient(yamcsInstance, host, port, reconnectionInterval, () -> {
                return new SlaveChannelHandler(this);
            });
            tcpClient.start();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        if (tcpClient != null) {
            tcpClient.stop();
        }
        notifyStopped();
    }

    private void failService(String errMsg) {
        log.warn("Replication failed: {}", errMsg);
        if (slaveChannelHandler != null) {
            log.warn("Closing connection to master");
            slaveChannelHandler.shutdown();
        }
        notifyFailed(new Exception(errMsg));
    }

    private void updateLastTxFile() {
        try {
            lastTxFile.seek(0);
            lastTxFile.writeBytes(Long.toString(lastTxId) + "\n");
        } catch (IOException e) {
            log.warn("Failed to update the last tx file " + txtfilePath, e);
        }

    }

    /**
     * Called when the tcpRole = Server and a new client connects to {@link ReplicationServer}
     * 
     * @throws YamcsException
     */
    public ChannelHandler newChannelHandler() throws YamcsException {
        if (slaveChannelHandler != null) {
            throw new YamcsException("There is already a connection open to this slave");
        }
        slaveChannelHandler = new SlaveChannelHandler(this);
        return slaveChannelHandler;
    }

    class SlaveChannelHandler extends ChannelInboundHandlerAdapter {
        ReplicationSlave replSlave;
        private ChannelHandlerContext channelHandlerContext;
        Map<Integer, ByteBufToStream> streamWriters = new HashMap<>();

        public SlaveChannelHandler(ReplicationSlave slave) {
            this.replSlave = slave;
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
                if (tmsg.txId > lastTxId) { //we expect to receive previous stream info transactions
                    checkMissing(tmsg); 
                }

                StreamInfo streamInfo = (StreamInfo) msg.protoMsg;
                if (!streamInfo.hasName() || !streamInfo.hasId()) {
                    failService("TX" + tmsg.txId + ": received invalid stream info: " + streamInfo);
                    return;
                }
                log.debug("TX{}: received stream info {}", tmsg.txId, TextFormat.shortDebugString(streamInfo));
                String streamName = streamInfo.getName();
                if (!streamNames.contains(streamName)) {
                    log.debug("TX{}Ignoring stream {} because it is not in the list configured", tmsg.txId, streamName);
                    return;
                }
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
                Stream stream = ydb.getStream(streamName);
                if (stream == null) {
                    log.warn("TX{}Received data for stream {} which does not exist", tmsg.txId, streamName);
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
            } else {
                failService("Unexpected message type " + msg.type + " received from the master");
                return;
            }
        }

        
        
        private void checkMissing(TransactionMessage tmsg) {
            if(tmsg.txId != lastTxId + 1) {
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
            if (tcpRole == TcpRole.Client) {
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
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            this.channelHandlerContext = ctx;
        }

        public void shutdown() {
            channelHandlerContext.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            log.warn("Caught exception {}", cause.getMessage());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("Connection {} closed", ctx.channel().remoteAddress());
            super.channelInactive(ctx);
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
                    serializers[i] = ColumnSerializerFactory.getColumnSerializer(cd);
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
                                    txId,
                                    stream.getName(),
                                    cidx);
                            return;
                        }
                        int typeId = id >>> 24;
                        ColumnDefinition cd = completeTuple.getColumn(cidx);
                        ColumnSerializer cs = serializers[cidx];

                        if (cd.getType().getTypeId() != typeId) {
                            log.warn(
                                    "TX{}: when deserializing data for stream {}: type id for index {} is {}; expected {}",
                                    txId, stream.getName(), cidx, typeId, cd.getType().getTypeId());
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
