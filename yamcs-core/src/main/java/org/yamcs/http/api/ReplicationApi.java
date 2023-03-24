package org.yamcs.http.api;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.protobuf.AbstractReplicationApi;
import org.yamcs.protobuf.ReplicationInfo;
import org.yamcs.protobuf.ReplicationMasterInfo;
import org.yamcs.protobuf.ReplicationSlaveInfo;
import org.yamcs.replication.MasterChannelHandler;
import org.yamcs.replication.ReplicationClient;
import org.yamcs.replication.ReplicationMaster;
import org.yamcs.replication.ReplicationMaster.SlaveServer;
import org.yamcs.replication.ReplicationServer;
import org.yamcs.replication.ReplicationSlave;
import org.yamcs.security.SystemPrivilege;

import com.google.protobuf.Empty;

import io.netty.channel.Channel;

public class ReplicationApi extends AbstractReplicationApi<Context> {

    @Override
    public void getReplicationInfo(Context ctx, Empty request, Observer<ReplicationInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadSystemInfo);
        observer.complete(toReplicationInfo());
    }

    @Override
    public void subscribeReplicationInfo(Context ctx, Empty request, Observer<ReplicationInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadSystemInfo);
        var yamcs = YamcsServer.getServer();
        var future = yamcs.getThreadPoolExecutor().scheduleAtFixedRate(() -> {
            ReplicationInfo info = toReplicationInfo();
            observer.next(info);
        }, 0, 1, TimeUnit.SECONDS);

        observer.setCancelHandler(() -> future.cancel(false));
    }

    private ReplicationInfo toReplicationInfo() {
        List<ReplicationMasterInfo> masters = new ArrayList<>();
        List<ReplicationSlaveInfo> slaves = new ArrayList<>();

        for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
            for (ReplicationMaster master : ysi.getServices(ReplicationMaster.class)) {
                masters.addAll(toReplicationMasterInfo(master));
            }
            for (ReplicationSlave slave : ysi.getServices(ReplicationSlave.class)) {
                slaves.addAll(toReplicationSlaveInfo(slave));
            }
        }
        Collections.sort(masters, (a, b) -> a.getInstance().compareTo(b.getInstance()));
        Collections.sort(masters, (a, b) -> a.getInstance().compareTo(b.getInstance()));

        ReplicationInfo.Builder infob = ReplicationInfo.newBuilder()
                .addAllMasters(masters)
                .addAllSlaves(slaves);
        return infob.build();
    }

    private List<ReplicationMasterInfo> toReplicationMasterInfo(ReplicationMaster master) {
        List<ReplicationMasterInfo> result = new ArrayList<>();

        List<String> streamNames = new ArrayList<>(master.getStreamNames());
        Collections.sort(streamNames);

        long txid = master.getTxId();

        if (master.isTcpClient()) {
            for (SlaveServer slaveServer : master.getSlaveServers()) {
                ReplicationMasterInfo.Builder masterb = ReplicationMasterInfo.newBuilder()
                        .setInstance(master.getYamcsInstance())
                        .addAllStreams(streamNames)
                        .setPush(true)
                        .setPushTo(slaveServer.getInstance())
                        .setLocalTx(txid);

                ReplicationClient tcpClient = slaveServer.getTcpClient();
                if (tcpClient != null) {
                    Channel ch = tcpClient.getChannel();
                    if (ch != null && ch.isActive()) {
                        InetSocketAddress address = (InetSocketAddress) ch.localAddress();
                        masterb.setLocalAddress(address.getAddress().getHostAddress() + ":" + address.getPort());
                        MasterChannelHandler handler = ch.pipeline().get(MasterChannelHandler.class);
                        if (handler != null) {
                            masterb.setNextTx(handler.getNextTxId());
                        }
                    }
                }

                masterb.setRemoteAddress(slaveServer.getHost() + ":" + slaveServer.getPort());
                result.add(masterb.build());
            }
        } else {
            ReplicationServer server = getReplicationServer();
            if (server != null) {
                for (Channel ch : server.getActiveChannels(master)) {
                    ReplicationMasterInfo.Builder masterb = ReplicationMasterInfo.newBuilder()
                            .setInstance(master.getYamcsInstance())
                            .addAllStreams(streamNames)
                            .setPush(false)
                            .setLocalTx(txid);

                    InetSocketAddress address = (InetSocketAddress) ch.localAddress();
                    masterb.setLocalAddress(address.getAddress().getHostAddress() + ":" + address.getPort());

                    address = (InetSocketAddress) ch.remoteAddress();
                    masterb.setRemoteAddress(address.getAddress().getHostAddress() + ":" + address.getPort());

                    MasterChannelHandler handler = ch.pipeline().get(MasterChannelHandler.class);
                    if (handler != null) {
                        masterb.setNextTx(handler.getNextTxId());
                    }

                    result.add(masterb.build());
                }
            }
        }

        return result;
    }

    private List<ReplicationSlaveInfo> toReplicationSlaveInfo(ReplicationSlave slave) {
        List<ReplicationSlaveInfo> result = new ArrayList<>();

        List<String> streamNames = new ArrayList<>(slave.getStreamNames());
        Collections.sort(streamNames);

        long txid = slave.getTxId();

        if (slave.isTcpClient()) {
            ReplicationSlaveInfo.Builder slaveb = ReplicationSlaveInfo.newBuilder()
                    .setInstance(slave.getYamcsInstance())
                    .addAllStreams(streamNames)
                    .setPush(false)
                    .setPullFrom(slave.getMasterInstance())
                    .setTx(txid);

            ReplicationClient tcpClient = slave.getTcpClient();
            if (tcpClient != null) {
                Channel ch = tcpClient.getChannel();
                if (ch != null && ch.isActive()) {
                    InetSocketAddress address = (InetSocketAddress) ch.localAddress();
                    slaveb.setLocalAddress(address.getAddress().getHostAddress() + ":" + address.getPort());
                }
            }

            slaveb.setRemoteAddress(slave.getMasterHost() + ":" + slave.getMasterPort());
            result.add(slaveb.build());
        } else {
            ReplicationServer server = getReplicationServer();
            if (server != null) {
                ReplicationSlaveInfo slavePrototype = ReplicationSlaveInfo.newBuilder()
                        .setInstance(slave.getYamcsInstance())
                        .addAllStreams(streamNames)
                        .setPush(true)
                        .setTx(txid)
                        .buildPartial();

                List<Channel> activeChannels = server.getActiveChannels(slave);
                for (Channel ch : activeChannels) {
                    ReplicationSlaveInfo.Builder slaveb = ReplicationSlaveInfo.newBuilder(slavePrototype);

                    InetSocketAddress address = (InetSocketAddress) ch.localAddress();
                    slaveb.setLocalAddress(address.getAddress().getHostAddress() + ":" + address.getPort());

                    address = (InetSocketAddress) ch.remoteAddress();
                    slaveb.setRemoteAddress(address.getAddress().getHostAddress() + ":" + address.getPort());

                    result.add(slaveb.build());
                }
                if (activeChannels.isEmpty()) {
                    result.add(ReplicationSlaveInfo.newBuilder(slavePrototype).build());
                }
            }
        }

        return result;
    }

    private static ReplicationServer getReplicationServer() {
        YamcsServer yamcs = YamcsServer.getServer();
        return yamcs.getGlobalService(ReplicationServer.class);
    }
}
